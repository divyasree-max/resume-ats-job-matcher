package com.atsmatcher.service;

import com.atsmatcher.model.JobListing;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Live job search via the Adzuna API (https://developer.adzuna.com),
 * chosen because it's ToS-compliant and has a workable free tier.
 * Deliberately not built against LinkedIn, Naukri, or Indeed: all three
 * prohibit scraping in their terms, which makes that path both legally
 * risky and operationally fragile (constant breakage on markup changes)
 * for something meant to be a reliable product.
 */
@Service
public class JobSearchService {

    private final WebClient webClient;

    @Value("${adzuna.app.id}")
    private String appId;

    @Value("${adzuna.app.key}")
    private String appKey;

    @Value("${adzuna.country}")
    private String country;

    public JobSearchService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.adzuna.com/v1/api/jobs").build();
    }

    public List<JobListing> search(String keywords, String city, int page, int resultsPerPage) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new IllegalStateException(
                    "Adzuna credentials not configured. Set ADZUNA_APP_ID and ADZUNA_APP_KEY " +
                    "environment variables (free tier at https://developer.adzuna.com).");
        }

        String uriPath = String.format("/%s/search/%d", country, page);

        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(uriPath)
                        .queryParam("app_id", appId)
                        .queryParam("app_key", appKey)
                        .queryParam("what", keywords)
                        .queryParam("results_per_page", resultsPerPage)
                        .queryParamIfPresent("where", java.util.Optional.ofNullable(city))
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<JobListing> listings = new ArrayList<>();
        if (response == null || !response.has("results")) return listings;

        for (JsonNode item : response.get("results")) {
            JobListing listing = new JobListing();
            listing.setSource("adzuna");
            listing.setSourceJobId(item.path("id").asText(""));
            listing.setTitle(item.path("title").asText("").trim());
            listing.setCompany(item.path("company").path("display_name").asText("Unknown"));
            listing.setLocationRaw(item.path("location").path("display_name").asText(""));
            listing.setDescription(item.path("description").asText(""));
            listing.setSalaryRaw(formatSalary(item));
            listing.setPostedDate(parseDate(item.path("created").asText(null)));
            listing.setApplyUrl(item.path("redirect_url").asText(""));
            listings.add(listing);
        }
        return listings;
    }

    private String formatSalary(JsonNode item) {
        if (item.has("salary_min") && item.has("salary_max")) {
            double lo = item.get("salary_min").asDouble();
            double hi = item.get("salary_max").asDouble();
            if (lo > 0 && hi > 0) {
                return String.format("%.0f-%.0f", lo, hi);
            }
        }
        return null;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
