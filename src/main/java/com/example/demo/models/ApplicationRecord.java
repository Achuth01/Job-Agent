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
    private String status;
    private LocalDateTime appliedAt;

    public ApplicationRecord(String jobId, String company, String title,
                             String status, LocalDateTime appliedAt) {
        this.jobId = jobId;
        this.company = company;
        this.title = title;
        this.status = status;
        this.appliedAt = appliedAt;
    }

    public ApplicationRecord() {
    }
}