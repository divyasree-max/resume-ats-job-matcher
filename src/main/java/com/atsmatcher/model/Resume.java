package com.atsmatcher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalFilename;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    private String experienceMode;   // "FRESHER" or "EXPERIENCED"
    private Double totalYearsExperience;
    private String detectionConfidence;

    private LocalDateTime uploadedAt;
}
