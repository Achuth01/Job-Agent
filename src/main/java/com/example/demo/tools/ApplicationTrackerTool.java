package com.example.demo.tools;

import com.example.demo.models.ApplicationRecord;
import com.example.demo.repository.ApplicationRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApplicationTrackerTool {

    private static final Logger log = LoggerFactory.getLogger(ApplicationTrackerTool.class);
    private final ApplicationRepository repository;

    public ApplicationTrackerTool(ApplicationRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "Save a job application record to track its status")
    public String trackApplication(
            @ToolParam(description = "Job ID") String jobId,
            @ToolParam(description = "Company name") String company,
            @ToolParam(description = "Job title") String title,
            @ToolParam(description = "Current status: APPLIED, PENDING, REJECTED, INTERVIEW") String status) {

        log.info("track application: jobId={} company={} title={} status={}", jobId, company, title, status);
        ApplicationRecord record = new ApplicationRecord(jobId, company, title, status, LocalDateTime.now());
        repository.save(record);
        return "Application tracked: " + title + " at " + company + " — Status: " + status;
    }

    @Tool(description = "Get all tracked job applications and their statuses")
    public List<ApplicationRecord> getAllApplications() {
        log.info("get all applications");
        return repository.findAll();
    }
}
