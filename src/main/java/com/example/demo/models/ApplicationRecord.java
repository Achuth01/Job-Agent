package com.example.demo.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
public class ApplicationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String jobId;
    private String company;
    private String title;
    private String jobUrl;
    private String status;
    private Integer fitScore;
    private LocalDateTime appliedAt;

    public ApplicationRecord(String jobId, String company, String title, String jobUrl,
                             String status, Integer fitScore, LocalDateTime appliedAt) {
        this.jobId = jobId;
        this.company = company;
        this.title = title;
        this.jobUrl = jobUrl;
        this.status = status;
        this.fitScore = fitScore;
        this.appliedAt = appliedAt;
    }

    public ApplicationRecord() {
    }

    public String getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public String getStatus() {
        return status;
    }

    public Integer getFitScore() {
        return fitScore;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }
}
