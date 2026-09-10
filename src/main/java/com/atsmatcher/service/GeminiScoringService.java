package com.atsmatcher.service;

import com.atsmatcher.service.ExperienceDetectionService.ExperienceMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Resume-vs-JD scoring using the Gemini API for both stages:
 *
 *   1. Semantic similarity: text-embedding-004 embeds the resume and the
 *      JD separately, cosine similarity between the two vectors gives
 *      the semantic match score.
 *   2. Structured explanation: gemini-2.0-flash is prompted to return a
 *      JSON object listing matched skills, missing skills, and specific
 *      improvement suggestions, this is what makes the score
 *      explainable rather than a bare number.
 *
 * Weights shift by ExperienceMode (fresher vs experienced), same
 * rationale as the rest of this project: freshers are scored more on
 * semantic fit and less on literal keyword overlap since they won't
 * have JD-specific vocabulary from real work yet.
 */
@Service
public class GeminiScoringService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.embedding.url}")
    private String embeddingUrl;

    @Value("${gemini.api.url}")
    private String generateUrl;

    public GeminiScoringService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public record MatchExplanation(
            double finalScore,
            double semanticSimilarity,
            double keywordMatchPct,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> suggestions,
            String modeUsed
    ) {}

    public MatchExplanation scoreResumeAgainstJd(String resumeText, String jdText, ExperienceMode mode) {
        if (apiKey == null || apiKey.isBlank()) {
            return localScore(resumeText, jdText, mode);
        }

        try {
            double[] resumeVec = embed(resumeText);
            double[] jdVec = embed(jdText);
            double semanticSimilarity = cosineSimilarity(resumeVec, jdVec);

            SkillAnalysis skills = analyzeSkills(resumeText, jdText);

            double semanticWeight = mode == ExperienceMode.FRESHER ? 0.55 : 0.45;
            double keywordWeight = mode == ExperienceMode.FRESHER ? 0.45 : 0.55;

            double finalScore = (semanticWeight * semanticSimilarity + keywordWeight * skills.matchPct()) * 100;

            return new MatchExplanation(
                    Math.round(finalScore * 10) / 10.0,
                    Math.round(semanticSimilarity * 1000) / 1000.0,
                    Math.round(skills.matchPct() * 1000) / 1000.0,
                    skills.matched(),
                    skills.missing(),
                    skills.suggestions(),
                    mode.name()
            );
        } catch (RuntimeException ignored) {
            return localScore(resumeText, jdText, mode);
        }
    }

    private MatchExplanation localScore(String resumeText, String jdText, ExperienceMode mode) {
        String resume = resumeText == null ? "" : resumeText.toLowerCase(Locale.ROOT);
        String jd = jdText == null ? "" : jdText.toLowerCase(Locale.ROOT);
        List<String> vocabulary = List.of("python", "sql", "java", "spring boot", "pandas", "numpy",
                "scikit-learn", "machine learning", "statistics", "tensorflow", "pytorch", "tableau",
                "power bi", "matplotlib", "seaborn", "git", "docker", "aws", "postgresql", "excel",
                "data analysis", "deep learning", "nlp");
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : vocabulary) {
            if (jd.contains(skill)) {
                if (resume.contains(skill)) matched.add(skill);
                else missing.add(skill);
            }
        }
        double keywordMatch = matched.size() + missing.size() == 0
                ? 0.0 : (double) matched.size() / (matched.size() + missing.size());
        double finalScore = keywordMatch * 100;
        return new MatchExplanation(
                Math.round(finalScore * 10) / 10.0,
                Math.round(keywordMatch * 1000) / 1000.0,
                Math.round(keywordMatch * 1000) / 1000.0,
                matched,
                missing,
                List.of("Gemini was unavailable, so this score uses local skill matching.",
                        "Add missing skills only when you have genuine project or work experience."),
                mode.name()
        );
    }

    private double[] embed(String text) {
        Map<String, Object> requestBody = Map.of(
                "model", "models/text-embedding-004",
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        JsonNode response = webClient.post()
                .uri(embeddingUrl + "?key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode valuesNode = response.path("embedding").path("values");
        double[] vector = new double[valuesNode.size()];
        for (int i = 0; i < valuesNode.size(); i++) {
            vector[i] = valuesNode.get(i).asDouble();
        }
        return vector;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, magA = 0, magB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }
        if (magA == 0 || magB == 0) return 0.0;
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }

    private record SkillAnalysis(List<String> matched, List<String> missing,
                                  double matchPct, List<String> suggestions) {}

    /**
     * Asks Gemini to extract and compare skills directly, rather than
     * matching against a fixed local taxonomy, this lets it catch
     * synonyms and role-specific terms a hardcoded list would miss (e.g.
     * "React.js" vs "React", or domain-specific tools).
     */
    private SkillAnalysis analyzeSkills(String resumeText, String jdText) {
        String prompt = """
                Compare this resume against this job description. Return ONLY a JSON object,
                no other text, no markdown formatting, with this exact shape:
                {"matched_skills": ["skill1", "skill2"], "missing_skills": ["skill3"], "suggestions": ["one short actionable suggestion"]}

                matched_skills: skills/technologies present in both the resume and the JD.
                missing_skills: skills/technologies the JD asks for that the resume does not show.
                suggestions: 1-2 short, specific, actionable suggestions for improving the resume's match to this JD.

                RESUME:
                %s

                JOB DESCRIPTION:
                %s
                """.formatted(resumeText, jdText);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        JsonNode response = webClient.post()
                .uri(generateUrl + "?key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String rawText = response.path("candidates").get(0).path("content")
                .path("parts").get(0).path("text").asText("{}");

        // Gemini sometimes wraps JSON in markdown fences despite instructions
        // not to, strip them defensively rather than letting parsing fail.
        String cleaned = rawText.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            JsonNode parsed = objectMapper.readTree(cleaned);
            List<String> matched = toList(parsed.path("matched_skills"));
            List<String> missing = toList(parsed.path("missing_skills"));
            List<String> suggestions = toList(parsed.path("suggestions"));

            int totalJdSkills = matched.size() + missing.size();
            double matchPct = totalJdSkills == 0 ? 0.0 : (double) matched.size() / totalJdSkills;

            return new SkillAnalysis(matched, missing, matchPct, suggestions);
        } catch (Exception e) {
            // If Gemini's response didn't parse as expected, fail soft
            // with an empty analysis rather than breaking the whole score.
            return new SkillAnalysis(List.of(), List.of(), 0.0,
                    List.of("Could not analyze skills for this listing, try again."));
        }
    }

    private List<String> toList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            arrayNode.forEach(n -> result.add(n.asText()));
        }
        return result;
    }
}
