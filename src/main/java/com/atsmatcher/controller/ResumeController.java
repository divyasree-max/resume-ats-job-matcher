package com.atsmatcher.controller;

import com.atsmatcher.dto.ResumeDtos.*;
import com.atsmatcher.model.Resume;
import com.atsmatcher.repository.ResumeRepository;
import com.atsmatcher.service.ExperienceDetectionService;
import com.atsmatcher.service.ExperienceDetectionService.DetectionResult;
import com.atsmatcher.service.GeminiScoringService;
import com.atsmatcher.service.ResumeTextExtractionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Two endpoints:
 *   POST /api/resumes/upload   - upload a resume file, get back the
 *                                 detected experience mode and a resumeId
 *                                 to reference in later score/search calls.
 *   POST /api/resumes/score-jd  - live score a resume against a pasted JD.
 *                                 Intended to be called on debounced input
 *                                 change from the frontend for the "live
 *                                 scoring" experience, not once per click.
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeTextExtractionService extractionService;
    private final ExperienceDetectionService experienceDetectionService;
    private final GeminiScoringService geminiScoringService;
    private final ResumeRepository resumeRepository;

    public ResumeController(
            ResumeTextExtractionService extractionService,
            ExperienceDetectionService experienceDetectionService,
            GeminiScoringService geminiScoringService,
            ResumeRepository resumeRepository
    ) {
        this.extractionService = extractionService;
        this.experienceDetectionService = experienceDetectionService;
        this.geminiScoringService = geminiScoringService;
        this.resumeRepository = resumeRepository;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResumeUploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        var extraction = extractionService.extractText(file);
        DetectionResult detection = experienceDetectionService.detect(extraction.text());

        Resume resume = new Resume();
        resume.setOriginalFilename(extraction.originalFilename());
        resume.setExtractedText(extraction.text());
        resume.setExperienceMode(detection.mode().name());
        resume.setTotalYearsExperience(detection.totalYearsExperience());
        resume.setDetectionConfidence(detection.confidence());
        resume.setUploadedAt(LocalDateTime.now());
        resume = resumeRepository.save(resume);

        String preview = extraction.text().length() > 300
                ? extraction.text().substring(0, 300) + "..."
                : extraction.text();

        return new ResumeUploadResponse(
                resume.getId(),
                extraction.originalFilename(),
                detection.mode().name(),
                detection.totalYearsExperience(),
                detection.confidence(),
                extraction.lowConfidence(),
                preview
        );
    }

    @PostMapping("/score-jd")
    public ScoreJdResponse scoreJd(@RequestBody ScoreJdRequest request) {
        String resumeText;
        ExperienceDetectionService.ExperienceMode mode;

        if (request.resumeTextOverride() != null && !request.resumeTextOverride().isBlank()) {
            // Live-editing path: score against unsaved edited text directly,
            // re-detecting mode each call so edits to the experience
            // section are reflected immediately in scoring weights.
            resumeText = request.resumeTextOverride();
            mode = experienceDetectionService.detect(resumeText).mode();
        } else if (request.resumeId() != null) {
            Resume resume = resumeRepository.findById(request.resumeId())
                    .orElseThrow(() -> new NoSuchElementException("Resume not found: " + request.resumeId()));
            resumeText = resume.getExtractedText();
            mode = ExperienceDetectionService.ExperienceMode.valueOf(resume.getExperienceMode());
        } else {
            throw new IllegalArgumentException("Either resumeId or resumeTextOverride must be provided.");
        }

        var match = geminiScoringService.scoreResumeAgainstJd(resumeText, request.jdText(), mode);

        return new ScoreJdResponse(
                match.finalScore(),
                match.semanticSimilarity(),
                match.keywordMatchPct(),
                match.matchedSkills(),
                match.missingSkills(),
                match.suggestions(),
                match.modeUsed()
        );
    }
}
