package com.example.demo.tools;

import com.example.demo.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class JobFitTool {

    private static final Logger log = LoggerFactory.getLogger(JobFitTool.class);
    private final ResumeRepository resumeRepository;

    public JobFitTool(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Tool(description = "Score how well a job fits the latest resume (0-100).")
    @Transactional(readOnly = true)
    public int scoreJobFit(
            @ToolParam(description = "Job title") String jobTitle,
            @ToolParam(description = "Job description") String jobDescription) {

        String resume = resumeRepository.findTopByOrderByUploadedAtDesc()
                .map(resumeDoc -> resumeDoc.getContent())
                .orElseThrow(() -> new IllegalStateException("No resume found. Upload one via /api/resume/upload"));

        int score = score(resume, jobTitle, jobDescription);
        log.info("job fit score: titleLength={} descLength={} score={}",
                jobTitle == null ? 0 : jobTitle.length(),
                jobDescription == null ? 0 : jobDescription.length(),
                score);
        return score;
    }

    public int score(String resume, String jobTitle, String jobDescription) {
        String jobText = safe(jobTitle) + " " + safe(jobDescription);
        Set<String> resumeTokens = tokenize(resume);
        Set<String> jobTokens = tokenize(jobText);
        if (jobTokens.isEmpty()) {
            return 0;
        }

        int overlap = 0;
        for (String token : jobTokens) {
            if (resumeTokens.contains(token)) {
                overlap++;
            }
        }

        int score = (int) Math.round(100.0 * overlap / jobTokens.size());
        return Math.min(100, Math.max(0, score));
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String normalized = safe(text);
        if (normalized.isEmpty()) {
            return tokens;
        }
        for (String token : normalized.split("[^a-z0-9+#]+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
