package com.atsmatcher.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Normalizes messy Indian location input before it hits the job search
 * API. Handles alternate city spellings (Bangalore/Bengaluru), area
 * names mapped to their parent city (Whitefield -> Bengaluru), and
 * remote-work detection.
 */
@Service
public class LocationService {

    private static final Map<String, String> CITY_ALIASES = new HashMap<>();
    static {
        CITY_ALIASES.put("bangalore", "Bengaluru");
        CITY_ALIASES.put("bengaluru", "Bengaluru");
        CITY_ALIASES.put("blr", "Bengaluru");
        CITY_ALIASES.put("bombay", "Mumbai");
        CITY_ALIASES.put("mumbai", "Mumbai");
        CITY_ALIASES.put("gurgaon", "Gurugram");
        CITY_ALIASES.put("gurugram", "Gurugram");
        CITY_ALIASES.put("calcutta", "Kolkata");
        CITY_ALIASES.put("kolkata", "Kolkata");
        CITY_ALIASES.put("madras", "Chennai");
        CITY_ALIASES.put("chennai", "Chennai");
        CITY_ALIASES.put("trivandrum", "Thiruvananthapuram");
        CITY_ALIASES.put("poona", "Pune");
        CITY_ALIASES.put("pune", "Pune");
        CITY_ALIASES.put("cochin", "Kochi");
        CITY_ALIASES.put("kochi", "Kochi");
        CITY_ALIASES.put("delhi", "New Delhi");
        CITY_ALIASES.put("new delhi", "New Delhi");
        CITY_ALIASES.put("ncr", "New Delhi");
        CITY_ALIASES.put("hyderabad", "Hyderabad");
        CITY_ALIASES.put("hyd", "Hyderabad");
        CITY_ALIASES.put("vizag", "Visakhapatnam");
        CITY_ALIASES.put("visakhapatnam", "Visakhapatnam");
    }

    private static final Map<String, String> AREA_TO_CITY = new HashMap<>();
    static {
        AREA_TO_CITY.put("whitefield", "Bengaluru");
        AREA_TO_CITY.put("electronic city", "Bengaluru");
        AREA_TO_CITY.put("koramangala", "Bengaluru");
        AREA_TO_CITY.put("hitech city", "Hyderabad");
        AREA_TO_CITY.put("gachibowli", "Hyderabad");
        AREA_TO_CITY.put("powai", "Mumbai");
        AREA_TO_CITY.put("andheri", "Mumbai");
        AREA_TO_CITY.put("omr", "Chennai");
        AREA_TO_CITY.put("guindy", "Chennai");
        AREA_TO_CITY.put("sector 62", "Noida");
        AREA_TO_CITY.put("cyber city", "Gurugram");
    }

    private static final Set<String> REMOTE_KEYWORDS = Set.of(
            "remote", "wfh", "work from home", "work-from-home", "anywhere");

    public record NormalizedLocation(String rawInput, String city, String area, boolean isRemote) {}

    public NormalizedLocation normalize(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return new NormalizedLocation(rawInput, null, null, false);
        }
        String cleaned = rawInput.trim().toLowerCase();

        for (String keyword : REMOTE_KEYWORDS) {
            if (cleaned.equals(keyword) || cleaned.contains(keyword)) {
                return new NormalizedLocation(rawInput, null, null, true);
            }
        }

        String areaMatch = AREA_TO_CITY.get(cleaned);
        if (areaMatch != null) {
            return new NormalizedLocation(rawInput, areaMatch, capitalize(rawInput.trim()), false);
        }

        String city = CITY_ALIASES.getOrDefault(cleaned, capitalize(rawInput.trim()));
        return new NormalizedLocation(rawInput, city, null, false);
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    /** Great-circle distance in km, used for radius filtering. */
    public double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
