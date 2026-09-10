package com.atsmatcher.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes inconsistent salary text ("6-9 LPA", "50,000/month",
 * "Rs. 6,00,000 per annum", "$40k-60k") into a consistent annual INR
 * range so listings can be compared or filtered on salary.
 */
@Service
public class SalaryService {

    // Fixed fallback FX rate. Refresh periodically, this is not a live
    // FX call, treat any USD/EUR-derived figure as approximate.
    private static final double USD_TO_INR = 87.0;

    private static final Pattern LPA_RANGE = Pattern.compile(
            "(?<low>\\d+(?:\\.\\d+)?)\\s*(?:-|to)\\s*(?<high>\\d+(?:\\.\\d+)?)\\s*lpa",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LPA_SINGLE = Pattern.compile(
            "(?<val>\\d+(?:\\.\\d+)?)\\s*lpa", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHLY = Pattern.compile(
            "(?:rs\\.?|inr|₹)?\\s*(?<val>[\\d,]+(?:\\.\\d+)?)\\s*(?:/|per)?\\s*month",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOLLAR_RANGE = Pattern.compile(
            "\\$\\s*(?<low>\\d+(?:\\.\\d+)?)\\s*k?\\s*(?:-|to)\\s*\\$?\\s*(?<high>\\d+(?:\\.\\d+)?)\\s*k",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_RANGE = Pattern.compile(
            "(?<low>\\d+(?:\\.\\d+)?)-(?<high>\\d+(?:\\.\\d+)?)");
    private static final Pattern RUPEE_RANGE = Pattern.compile(
            "(?:rs\\.?|inr|₹)?\\s*(?<low>[\\d,]+(?:\\.\\d+)?)\\s*(?:-|to)\\s*" +
            "(?:rs\\.?|inr|₹)?\\s*(?<high>[\\d,]+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    public record NormalizedSalary(
            String rawInput, Double annualMinInr, Double annualMaxInr,
            boolean isEstimated, String currencyDetected) {

        public String displayLpa() {
            if (annualMinInr == null) return null;
            double lowLpa = annualMinInr / 100_000;
            if (annualMaxInr != null && !annualMaxInr.equals(annualMinInr)) {
                double highLpa = annualMaxInr / 100_000;
                return String.format("%.1f - %.1f LPA", lowLpa, highLpa);
            }
            return String.format("%.1f LPA", lowLpa);
        }
    }

    public NormalizedSalary parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new NormalizedSalary(rawText, null, null, false, "INR");
        }
        String text = rawText.trim();

        // Pre-formatted "min-max" from an API adapter (already annual INR),
        // unless both numbers are small, in which case it's shorthand LPA
        // typed without the unit (e.g. "6-9" means 6-9 LPA, not six rupees).
        Matcher plain = PLAIN_RANGE.matcher(text);
        if (plain.matches()) {
            double low = Double.parseDouble(plain.group("low"));
            double high = Double.parseDouble(plain.group("high"));
            if (high >= 1000) {
                return new NormalizedSalary(rawText, low, high, false, "INR");
            }
            return new NormalizedSalary(rawText, low * 100_000, high * 100_000, false, "INR");
        }

        Matcher lpaRange = LPA_RANGE.matcher(text);
        if (lpaRange.find()) {
            double low = Double.parseDouble(lpaRange.group("low")) * 100_000;
            double high = Double.parseDouble(lpaRange.group("high")) * 100_000;
            return new NormalizedSalary(rawText, low, high, false, "INR");
        }

        Matcher lpaSingle = LPA_SINGLE.matcher(text);
        if (lpaSingle.find()) {
            double val = Double.parseDouble(lpaSingle.group("val")) * 100_000;
            return new NormalizedSalary(rawText, val, val, false, "INR");
        }

        Matcher dollarRange = DOLLAR_RANGE.matcher(text);
        if (dollarRange.find()) {
            double lowUsd = Double.parseDouble(dollarRange.group("low")) * 1000;
            double highUsd = Double.parseDouble(dollarRange.group("high")) * 1000;
            return new NormalizedSalary(rawText, lowUsd * USD_TO_INR, highUsd * USD_TO_INR, true, "USD");
        }

        Matcher monthly = MONTHLY.matcher(text);
        if (monthly.find()) {
            double monthlyVal = Double.parseDouble(monthly.group("val").replace(",", ""));
            double annual = monthlyVal * 12;
            return new NormalizedSalary(rawText, annual, annual, false, "INR");
        }

        Matcher rupeeRange = RUPEE_RANGE.matcher(text);
        if (rupeeRange.find()) {
            double low = Double.parseDouble(rupeeRange.group("low").replace(",", ""));
            double high = Double.parseDouble(rupeeRange.group("high").replace(",", ""));
            if (high < 1000) {
                low *= 100_000;
                high *= 100_000;
            }
            return new NormalizedSalary(rawText, low, high, false, "INR");
        }

        return new NormalizedSalary(rawText, null, null, false, "INR");
    }

    /** Returns null if unparseable, so callers can decide whether to
     * exclude the listing or show it with a warning instead of silently
     * dropping a potentially good match. */
    public Boolean meetsMinimum(NormalizedSalary salary, double minimumAnnualInr) {
        if (salary.annualMaxInr() == null) return null;
        return salary.annualMaxInr() >= minimumAnnualInr;
    }
}
