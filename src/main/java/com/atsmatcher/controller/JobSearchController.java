package com.atsmatcher.controller;

import com.atsmatcher.dto.JobSearchDtos.*;
import com.atsmatcher.model.JobListing;
import com.atsmatcher.model.Resume;
import com.atsmatcher.repository.ResumeRepository;
import com.atsmatcher.service.*;
import com.atsmatcher.service.ExperienceDetectionService.ExperienceMode;
import com.atsmatcher.service.QualityFilterService.ScamCheckResult;
import com.atsmatcher.service.SalaryService.NormalizedSalary;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * POST /api/jobs/search
 *
 * Full pipeline: normalize location -> fetch from Adzuna -> deduplicate
 * -> drop stale/scam listings -> parse salary -> score against resume
 * (if resumeId supplied) -> rank -> return.
 *
 * If no resumeId is supplied, results rank by freshness only, that's a
 * placeholder ordering, the real ranking signal is the resume match
 * score once a resume is in play.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final JobSearchService jobSearchService;
    private final LocationService locationService;
    private final DedupService dedupService;
    private final QualityFilterService qualityFilterService;
    private final SalaryService salaryService;
    private final GeminiScoringService geminiScoringService;
    private final ResumeRepository resumeRepository;

    public JobSearchController(
            JobSearchService jobSearchService,
            LocationService locationService,
            DedupService dedupService,
            QualityFilterService qualityFilterService,
            SalaryService salaryService,
            GeminiScoringService geminiScoringService,
            ResumeRepository resumeRepository
    ) {
        this.jobSearchService = jobSearchService;
        this.locationService = locationService;
        this.dedupService = dedupService;
        this.qualityFilterService = qualityFilterService;
        this.salaryService = salaryService;
        this.geminiScoringService = geminiScoringService;
        this.resumeRepository = resumeRepository;
    }

    @PostMapping("/search")
    public JobSearchResponse search(@RequestBody JobSearchRequest request) {
        String city = null;
        if (request.location() != null && !request.location().isBlank()) {
            var normalizedLoc = locationService.normalize(request.location());
            city = normalizedLoc.city(); // null if remote
        }

        int page = request.page() > 0 ? request.page() : 1;
        int resultsPerPage = request.resultsPerPage() > 0 ? request.resultsPerPage() : 20;

        List<JobListing> rawListings = jobSearchService.search(request.keywords(), city, page, resultsPerPage);

        var dedupResult = dedupService.deduplicate(rawListings);

        int maxAge = request.maxListingAgeDays() != null ? request.maxListingAgeDays() : 60;

        Resume resume = null;
        if (request.resumeId() != null) {
            resume = resumeRepository.findById(request.resumeId())
                    .orElseThrow(() -> new NoSuchElementException("Resume not found: " + request.resumeId()));
        }

        List<JobResultItem> items = new ArrayList<>();

        for (JobListing listing : dedupResult.uniqueListings()) {
            if (qualityFilterService.isStale(listing.getPostedDate(), maxAge)) {
                continue;
            }

            ScamCheckResult scamCheck = qualityFilterService.checkForScamSignals(listing);
            if (scamCheck.riskScore() >= 0.7) {
                continue; // excluded outright; 0.5-0.7 still shown with a warning flag
            }

            NormalizedSalary salary = salaryService.parse(listing.getSalaryRaw());
            if (request.minSalaryAnnualInr() != null) {
                Boolean meetsMin = salaryService.meetsMinimum(salary, request.minSalaryAnnualInr());
                if (Boolean.FALSE.equals(meetsMin)) {
                    continue;
                }
            }

            double freshness = qualityFilterService.freshnessScore(listing.getPostedDate());

            Double matchScore = null;
            List<String> matchedSkills = List.of();
            List<String> missingSkills = List.of();

            if (resume != null) {
                ExperienceMode mode = ExperienceMode.valueOf(resume.getExperienceMode());
                var match = geminiScoringService.scoreResumeAgainstJd(
                        resume.getExtractedText(), listing.getDescription(), mode);
                matchScore = match.finalScore();
                matchedSkills = match.matchedSkills();
                missingSkills = match.missingSkills();
            }

            items.add(new JobResultItem(
                    listing.getTitle(),
                    listing.getCompany(),
                    listing.getLocationRaw(),
                    salary.displayLpa(),
                    listing.getPostedDate(),
                    listing.getApplyUrl(),
                    freshness,
                    scamCheck.riskScore(),
                    scamCheck.flags(),
                    matchScore,
                    matchedSkills,
                    missingSkills
            ));
        }

        if (resume != null) {
            items.sort(Comparator.comparing(
                    (JobResultItem i) -> i.matchScore() != null ? i.matchScore() : 0.0
            ).reversed());
        } else {
            items.sort(Comparator.comparing(JobResultItem::freshnessScore).reversed());
        }

        return new JobSearchResponse(items, items.size(), dedupResult.duplicatesRemoved());
    }
}
