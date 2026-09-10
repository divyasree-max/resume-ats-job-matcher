package com.atsmatcher.dto;

import java.time.LocalDateTime;
import java.util.List;

public class JobSearchDtos {

    public record JobSearchRequest(
            String keywords,
            String location,
            Long resumeId,             // optional: if present, results are scored and ranked against this resume
            Double minSalaryAnnualInr,
            Integer maxListingAgeDays,
            int page,
            int resultsPerPage
    ) {}

    public record JobResultItem(
            String title,
            String company,
            String location,
            String salaryDisplay,
            LocalDateTime postedDate,
            String applyUrl,
            double freshnessScore,
            double scamRiskScore,
            List<String> scamFlags,
            Double matchScore,          // null if no resume was supplied
            List<String> matchedSkills,
            List<String> missingSkills
    ) {}

    public record JobSearchResponse(
            List<JobResultItem> results,
            int totalFound,
            int duplicatesRemoved
    ) {}
}
