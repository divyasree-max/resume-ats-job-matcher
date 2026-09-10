package com.atsmatcher.dto;

import java.util.List;

public class ResumeDtos {

    public record ResumeUploadResponse(
            Long resumeId,
            String filename,
            String experienceMode,
            double totalYearsExperience,
            String detectionConfidence,
            boolean lowExtractionConfidence,
            String extractedTextPreview
    ) {}

    public record ScoreJdRequest(
            Long resumeId,
            String resumeTextOverride,   // optional: score without a saved resumeId, e.g. live-editing
            String jdText
    ) {}

    public record ScoreJdResponse(
            double finalScore,
            double semanticSimilarity,
            double keywordMatchPct,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> suggestions,
            String modeUsed
    ) {}
}
