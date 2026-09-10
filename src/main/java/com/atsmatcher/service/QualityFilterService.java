package com.atsmatcher.service;

import com.atsmatcher.model.JobListing;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Freshness scoring (listings decay in relevance as they age) and scam
 * detection (heuristic, not ML-based, deliberately conservative so a
 * legitimate small recruiter with a generic-sounding name isn't
 * penalized on one weak signal alone).
 */
@Service
public class QualityFilterService {

    private static final List<Pattern> PAYMENT_REQUEST_PATTERNS = List.of(
            Pattern.compile("registration fee", Pattern.CASE_INSENSITIVE),
            Pattern.compile("security deposit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("processing fee", Pattern.CASE_INSENSITIVE),
            Pattern.compile("training fee", Pattern.CASE_INSENSITIVE),
            Pattern.compile("refundable deposit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("activation fee", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> UNREALISTIC_CLAIMS_PATTERNS = List.of(
            Pattern.compile("earn\\s+(?:up to\\s+)?(?:rs\\.?|inr|₹)?\\s*\\d+[,\\d]*\\s*(?:per|/)\\s*day",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("100%\\s+job\\s+guarantee", Pattern.CASE_INSENSITIVE),
            Pattern.compile("guaranteed\\s+placement", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> VAGUE_CONTACT_PATTERNS = List.of(
            Pattern.compile("whatsapp\\s+only", Pattern.CASE_INSENSITIVE),
            Pattern.compile("telegram\\s+(?:only|group)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<String> GENERIC_COMPANY_NAMES = List.of(
            "pvt ltd", "private limited", "consultancy", "hr solutions",
            "manpower", "placement services"
    );

    public double freshnessScore(LocalDateTime postedDate) {
        if (postedDate == null) return 0.5; // neutral, missing metadata isn't the job's fault
        long ageDays = Duration.between(postedDate, LocalDateTime.now()).toDays();
        if (ageDays < 0) return 1.0;   // clock skew between sources
        if (ageDays >= 45) return 0.0;
        return Math.round((1.0 - (ageDays / 45.0)) * 1000.0) / 1000.0;
    }

    public boolean isStale(LocalDateTime postedDate, int maxAgeDays) {
        if (postedDate == null) return false;
        return Duration.between(postedDate, LocalDateTime.now()).toDays() > maxAgeDays;
    }

    public record ScamCheckResult(double riskScore, List<String> flags, boolean shouldShowWarning) {}

    public ScamCheckResult checkForScamSignals(JobListing listing) {
        String text = (safe(listing.getTitle()) + " " + safe(listing.getDescription())).toLowerCase();
        List<String> flags = new ArrayList<>();
        double risk = 0.0;

        if (matchesAny(text, PAYMENT_REQUEST_PATTERNS)) {
            flags.add("requests upfront payment");
            risk += 0.4;
        }
        if (matchesAny(text, UNREALISTIC_CLAIMS_PATTERNS)) {
            flags.add("unrealistic earnings or guarantee claims");
            risk += 0.3;
        }
        if (matchesAny(text, VAGUE_CONTACT_PATTERNS)) {
            flags.add("informal/unverifiable contact method only");
            risk += 0.2;
        }

        String companyLower = safe(listing.getCompany()).toLowerCase().trim();
        boolean genericName = GENERIC_COMPANY_NAMES.stream().anyMatch(companyLower::contains)
                && companyLower.split("\\s+").length <= 3;
        if (genericName) {
            flags.add("generic recruiter/consultancy name with no specific company identity");
            risk += 0.1;
        }

        if (listing.getApplyUrl() == null || !listing.getApplyUrl().startsWith("http")) {
            flags.add("no verifiable application link");
            risk += 0.1;
        }

        risk = Math.min(risk, 1.0);
        return new ScamCheckResult(Math.round(risk * 100.0) / 100.0, flags, risk >= 0.5);
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
