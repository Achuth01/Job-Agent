package com.example.demo.jobagent;

import com.example.demo.api.JobApplicationResponse;
import com.example.demo.models.JobListing;
import com.example.demo.tools.ApplicationTrackerTool;
import com.example.demo.tools.CoverLetterTool;
import com.example.demo.tools.JobFitTool;
import com.example.demo.tools.JobSearchTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobApplicationAgent {

    private static final Pattern LOCATION_PATTERN = Pattern.compile("\\b(?:in|at)\\s+([a-zA-Z\\s]+)$");
    private static final Logger log = LoggerFactory.getLogger(JobApplicationAgent.class);
    private final ChatClient chatClient;
    private final JobSearchTool jobSearchTool;
    private final JobFitTool jobFitTool;
    private final CoverLetterTool coverLetterTool;
    private final ApplicationTrackerTool trackerTool;
    private final ObjectMapper objectMapper;

    public JobApplicationAgent(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            JobSearchTool jobSearchTool,
            JobFitTool jobFitTool,
            CoverLetterTool coverLetterTool,
            ApplicationTrackerTool trackerTool,
            ObjectMapper objectMapper,
            @Value("${app.agent.system-prompt}") String systemPrompt) {

        this.jobSearchTool = jobSearchTool;
        this.jobFitTool = jobFitTool;
        this.coverLetterTool = coverLetterTool;
        this.trackerTool = trackerTool;
        this.objectMapper = objectMapper;
        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .defaultTools(jobSearchTool, jobFitTool, coverLetterTool, trackerTool)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public String chat(String userMessage, String sessionId) {
        log.info("agent chat start: sessionId={} messageLength={}", sessionId, userMessage == null ? 0 : userMessage.length());
        if (shouldTriggerJobSearch(userMessage)) {
            String keywords = extractKeywords(userMessage);
            String location = extractLocation(userMessage);
            log.info("forcing job search tool: keywords={} location={}", keywords, location);
            List<JobListing> jobs = jobSearchTool.searchJobs(keywords, location);
            String jobsJson = toJson(jobs);
            String augmentedPrompt = userMessage
                    + "\n\nJob search results (from tool):\n"
                    + jobsJson
                    + "\n\nSummarize the top matches and ask any clarifying questions if needed.";

            return chatClient.prompt()
                    .user(augmentedPrompt)
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();
        }

        return chatClient.prompt()
                .user(userMessage)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }

    public List<JobApplicationResponse> applyForJobs(String userMessage, String sessionId) {
        String keywords = extractKeywords(userMessage);
        String location = extractLocation(userMessage);
        log.info("apply for jobs: sessionId={} keywords={} location={}", sessionId, keywords, location);

        List<JobListing> jobs = jobSearchTool.searchJobs(keywords, location);
        List<JobApplicationResponse> responses = new ArrayList<>();

        int limit = Math.min(3, jobs.size());
        for (int i = 0; i < limit; i++) {
            JobListing job = jobs.get(i);
            int fitScore = jobFitTool.scoreJobFit(job.title(), job.description());
            String cover = coverLetterTool.generateCoverLetter(job.title() + " at " + job.company(), job.description());
            trackerTool.trackApplication(job.id(), job.company(), job.title(), job.url(), fitScore, "APPLIED");

            responses.add(new JobApplicationResponse(
                    job.url(),
                    job.company(),
                    cover,
                    job.title(),
                    fitScore
            ));
        }

        return responses;
    }

    public boolean shouldAutoApply(String message) {
        String text = normalize(message);
        return text.contains("apply") || text.contains("application") || text.contains("submit");
    }

    private boolean shouldTriggerJobSearch(String message) {
        String text = normalize(message);
        if (text.isEmpty()) {
            return false;
        }
        return text.contains("job") || text.contains("jobs") || text.contains("openings")
                || text.contains("search") || text.contains("find");
    }

    private String extractKeywords(String message) {
        String text = normalize(message);
        if (text.isEmpty()) {
            return "";
        }
        String cleaned = text.replaceAll("\\b(find|search|looking for|jobs?|openings?)\\b", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        Matcher matcher = LOCATION_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String location = matcher.group(1);
            cleaned = cleaned.replace("in " + location, "")
                    .replace("at " + location, "")
                    .trim();
        }
        return cleaned;
    }

    private String extractLocation(String message) {
        String text = normalize(message);
        if (text.contains("remote")) {
            return "remote";
        }
        Matcher matcher = LOCATION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String toJson(List<JobListing> jobs) {
        try {
            return objectMapper.writeValueAsString(jobs);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize job results", e);
            return jobs.toString();
        }
    }
}
