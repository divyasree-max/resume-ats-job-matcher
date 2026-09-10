package com.atsmatcher.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether a resume belongs to a fresher or an experienced
 * candidate, and with what confidence. This drives the scoring weights
 * used later in GeminiScoringService: freshers are scored more on
 * semantic fit and less on literal keyword overlap, since they won't
 * have JD-specific vocabulary from real work yet.
 *
 * Section and date detection uses regex against common resume header
 * conventions rather than an NLP model, faster, cheaper, and reliable
 * enough for well-structured resumes, which covers the large majority
 * of real uploads.
 */
@Service
public class ExperienceDetectionService {

    public enum ExperienceMode { FRESHER, EXPERIENCED }

    private static final double FRESHER_YEAR_THRESHOLD = 2.0;

    private static final Map<String, List<String>> SECTION_PATTERNS = new LinkedHashMap<>();
    static {
        SECTION_PATTERNS.put("experience", List.of(
                "work experience", "professional experience", "employment history", "experience"));
        SECTION_PATTERNS.put("projects", List.of(
                "projects", "academic projects", "personal projects", "key projects"));
        SECTION_PATTERNS.put("internships", List.of(
                "internships?", "internship experience"));
        SECTION_PATTERNS.put("education", List.of(
                "education", "academic background", "qualifications"));
    }

    private static final Pattern HEADER_PATTERN = buildHeaderPattern();

    private static Pattern buildHeaderPattern() {
        List<String> all = new ArrayList<>();
        SECTION_PATTERNS.values().forEach(all::addAll);
        String joined = String.join("|", all);
        return Pattern.compile("(?im)^\\s*(" + joined + ")\\s*:?\\s*$");
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("january", 1), Map.entry("feb", 2), Map.entry("february", 2),
            Map.entry("mar", 3), Map.entry("march", 3), Map.entry("apr", 4), Map.entry("april", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("june", 6), Map.entry("jul", 7), Map.entry("july", 7),
            Map.entry("aug", 8), Map.entry("august", 8), Map.entry("sep", 9), Map.entry("sept", 9),
            Map.entry("september", 9), Map.entry("oct", 10), Map.entry("october", 10), Map.entry("nov", 11),
            Map.entry("november", 11), Map.entry("dec", 12), Map.entry("december", 12)
    );

    // Matches "Jan 2021 - Mar 2023", "2021 - Present", "01/2021 - 05/2022"
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?:(?<startMonth>[A-Za-z]{3,9})\\.?\\s+)?(?<startYear>\\d{4})" +
            "\\s*(?:-|–|—|to)\\s*" +
            "(?<end>present|current|now|(?:(?<endMonth>[A-Za-z]{3,9})\\.?\\s+)?(?<endYear>\\d{4}))",
            Pattern.CASE_INSENSITIVE
    );

    public record DateRange(LocalDate start, LocalDate end) {
        public double years() {
            Period p = Period.between(start, end);
            return p.getYears() + p.getMonths() / 12.0;
        }
    }

    public record DetectionResult(
            ExperienceMode mode,
            double totalYearsExperience,
            String confidence,   // "high", "medium", "low"
            String reason,
            boolean hasInternships,
            boolean hasProjects
    ) {}

    public DetectionResult detect(String resumeText) {
        Map<String, String> sections = splitIntoSections(resumeText);
        String experienceSection = sections.getOrDefault("experience", "");

        List<DateRange> ranges = extractDateRanges(experienceSection);
        List<DateRange> merged = mergeOverlapping(ranges);

        double totalYears = merged.stream().mapToDouble(DateRange::years).sum();

        boolean hasInternships = sections.containsKey("internships") ||
                experienceSection.toLowerCase().contains("intern");
        boolean hasProjects = sections.containsKey("projects");

        String confidence;
        String reason;
        if (resumeText == null || resumeText.isBlank()) {
            confidence = "low";
            reason = "No parseable resume text found.";
        } else if (!experienceSection.isBlank() && ranges.isEmpty()) {
            confidence = "low";
            reason = "Experience section found but no date ranges could be parsed.";
        } else if (sections.isEmpty()) {
            confidence = "medium";
            reason = "No clear section headers detected; classification based on whole-document heuristics.";
        } else {
            confidence = "high";
            reason = String.format("Detected %d non-overlapping work period(s) totaling %.1f years.",
                    merged.size(), totalYears);
        }

        ExperienceMode mode = totalYears < FRESHER_YEAR_THRESHOLD
                ? ExperienceMode.FRESHER
                : ExperienceMode.EXPERIENCED;

        return new DetectionResult(mode, Math.round(totalYears * 100) / 100.0, confidence, reason,
                hasInternships, hasProjects);
    }

    private Map<String, String> splitIntoSections(String resumeText) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (resumeText == null || resumeText.isBlank()) return sections;

        Matcher matcher = HEADER_PATTERN.matcher(resumeText);
        List<int[]> matchPositions = new ArrayList<>();
        List<String> matchedHeaders = new ArrayList<>();

        while (matcher.find()) {
            matchPositions.add(new int[]{matcher.start(), matcher.end()});
            matchedHeaders.add(matcher.group(1).toLowerCase());
        }

        for (int i = 0; i < matchPositions.size(); i++) {
            int start = matchPositions.get(i)[1];
            int end = (i + 1 < matchPositions.size()) ? matchPositions.get(i + 1)[0] : resumeText.length();
            String body = resumeText.substring(start, end).trim();

            String canonical = canonicalSectionName(matchedHeaders.get(i));
            if (canonical != null) {
                sections.merge(canonical, body, (existing, added) -> existing + "\n" + added);
            }
        }
        return sections;
    }

    private String canonicalSectionName(String headerText) {
        for (Map.Entry<String, List<String>> entry : SECTION_PATTERNS.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (headerText.matches(pattern)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private List<DateRange> extractDateRanges(String text) {
        List<DateRange> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        Matcher matcher = DATE_RANGE_PATTERN.matcher(text);
        LocalDate today = LocalDate.now();

        while (matcher.find()) {
            try {
                int startYear = Integer.parseInt(matcher.group("startYear"));
                int startMonth = resolveMonth(matcher.group("startMonth"));
                LocalDate start = LocalDate.of(startYear, startMonth, 1);

                String endRaw = matcher.group("end").trim().toLowerCase();
                LocalDate end;
                if (endRaw.equals("present") || endRaw.equals("current") || endRaw.equals("now")) {
                    end = today;
                } else {
                    String endYearStr = matcher.group("endYear");
                    if (endYearStr == null) continue;
                    int endYear = Integer.parseInt(endYearStr);
                    int endMonth = resolveMonth(matcher.group("endMonth"));
                    end = LocalDate.of(endYear, endMonth, 1);
                }

                if (end.isBefore(start)) continue; // skip malformed ranges

                results.add(new DateRange(start, end));
            } catch (Exception ignored) {
                // Skip anything that doesn't parse cleanly rather than
                // letting one malformed date range break the whole scan.
            }
        }
        return results;
    }

    private int resolveMonth(String monthStr) {
        if (monthStr == null) return 1;
        Integer resolved = MONTHS.get(monthStr.toLowerCase());
        return resolved != null ? resolved : 1;
    }

    /**
     * Merges overlapping date ranges so a freelance gig running alongside
     * a full-time role isn't double-counted toward total experience.
     */
    private List<DateRange> mergeOverlapping(List<DateRange> ranges) {
        if (ranges.isEmpty()) return ranges;

        List<DateRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparing(DateRange::start));

        List<DateRange> merged = new ArrayList<>();
        DateRange current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            DateRange next = sorted.get(i);
            if (!next.start().isAfter(current.end())) {
                LocalDate newEnd = current.end().isAfter(next.end()) ? current.end() : next.end();
                current = new DateRange(current.start(), newEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}
