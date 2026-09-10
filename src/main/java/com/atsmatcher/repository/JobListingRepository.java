package com.atsmatcher.repository;

import com.atsmatcher.model.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobListingRepository extends JpaRepository<JobListing, Long> {
}
