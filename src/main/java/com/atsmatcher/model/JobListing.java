package com.atsmatcher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_listings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;          // e.g. "adzuna"
    private String sourceJobId;     // ID as given by the source

    @Column(length = 500)
    private String title;

    private String company;
    private String locationRaw;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String salaryRaw;
    private LocalDateTime postedDate;

    @Column(length = 1000)
    private String applyUrl;

    private boolean isRemoteFlag;

    // Populated after dedup/quality/scoring stages, not stored on first
    // fetch, kept transient conceptually but persisted here for simplicity
    // so a search history can be reconstructed later if needed.
    private Double freshnessScore;
    private Double scamRiskScore;
    private Double matchScore;
}
