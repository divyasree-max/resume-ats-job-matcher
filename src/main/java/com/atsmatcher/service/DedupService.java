package com.atsmatcher.service;

import com.atsmatcher.model.JobListing;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Catches duplicate listings, whether reposted by the same company or
 * appearing on two different sources. Hashing company + title + a
 * truncated description prefix (rather than relying on the source's own
 * job ID) is what makes cross-source dedup possible at all, two sources
 * never share IDs for the same real-world posting.
 */
@Service
public class DedupService {

    private static final int DESCRIPTION_CHARS_FOR_HASH = 300;

    public record DedupResult(List<JobListing> uniqueListings, int duplicatesRemoved) {}

    public DedupResult deduplicate(List<JobListing> listings) {
        Map<String, List<JobListing>> groups = new LinkedHashMap<>();

        for (JobListing listing : listings) {
            String key = dedupKey(listing);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(listing);
        }

        List<JobListing> unique = new ArrayList<>();
        int duplicatesRemoved = 0;

        for (List<JobListing> group : groups.values()) {
            if (group.size() == 1) {
                unique.add(group.get(0));
                continue;
            }
            duplicatesRemoved += group.size() - 1;

            JobListing best = group.stream()
                    .filter(l -> l.getPostedDate() != null)
                    .max(Comparator.comparing(JobListing::getPostedDate))
                    .orElse(group.get(0));
            unique.add(best);
        }

        return new DedupResult(unique, duplicatesRemoved);
    }

    private String dedupKey(JobListing listing) {
        String companyNorm = normalize(listing.getCompany());
        String titleNorm = normalize(listing.getTitle());
        String description = listing.getDescription() != null ? listing.getDescription() : "";
        String descPrefix = description.substring(0, Math.min(description.length(), DESCRIPTION_CHARS_FOR_HASH));
        String descNorm = normalize(descPrefix);

        String combined = companyNorm + "|" + titleNorm + "|" + descNorm;
        return sha256(combined);
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ");
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on the JVM, this is unreachable
            // in practice, but fail loudly rather than silently if it isn't.
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
