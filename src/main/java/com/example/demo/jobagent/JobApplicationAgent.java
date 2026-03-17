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
    private static final Pattern LOCATION_AFTER_JOBS_PATTERN =
            Pattern.compile("(?i)\\bjobs?\\s+in\\s+([a-zA-Z\\s]+)$");
    private static final Pattern LEADING_LOCATION_PATTERN =
            Pattern.compile("(?i)^([a-zA-Z\\s]+)\\s+jobs?\\b");
    private static final Pattern TRAILING_LOCATION_PATTERN =
            Pattern.compile("(?i)\\bjobs?\\s+([a-zA-Z\\s]+)$");
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
            String queryText = sanitizeQueryText(userMessage);
            String location = extractLocation(queryText);
            String keywords = extractKeywords(queryText, location);
            log.info("forcing job search tool: keywords={} location={}", keywords, location);
            List<JobListing> jobs = jobSearchTool.searchJobs(keywords, location);
            if (isRecentRequest(userMessage)) {
                jobs = sortByRecency(jobs);
            }
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
        String queryText = sanitizeQueryText(userMessage);
        String location = extractLocation(queryText);
        String keywords = extractKeywords(queryText, location);
        log.info("apply for jobs: sessionId={} keywords={} location={}", sessionId, keywords, location);

        List<JobListing> jobs = jobSearchTool.searchJobs(keywords, location);
        if (isRecentRequest(userMessage)) {
            jobs = sortByRecency(jobs);
        }
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

    private String extractKeywords(String message, String location) {
        String text = normalize(message);
        if (text.isEmpty()) {
            return "";
        }
        String cleaned = text.replaceAll("\\b(find|search|looking for|jobs?|openings?|recent|latest|newest|new)\\b", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        Matcher matcher = LOCATION_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String matchedLocation = matcher.group(1);
            cleaned = cleaned.replace("in " + matchedLocation, "")
                    .replace("at " + matchedLocation, "")
                    .trim();
        }
        if (location != null && !location.isBlank() && cleaned.equalsIgnoreCase(location.trim())) {
            return "";
        }
        return cleaned;
    }

    private String extractLocation(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = normalize(message);
        if (normalized.contains("remote")) {
            return "remote";
        }

        Matcher explicit = LOCATION_AFTER_JOBS_PATTERN.matcher(message);
        if (explicit.find()) {
            return explicit.group(1).trim();
        }
        Matcher inAt = LOCATION_PATTERN.matcher(normalized);
        if (inAt.find()) {
            return inAt.group(1).trim();
        }

        Matcher leading = LEADING_LOCATION_PATTERN.matcher(message);
        if (leading.find()) {
            return leading.group(1).trim();
        }
        Matcher trailing = TRAILING_LOCATION_PATTERN.matcher(message);
        if (trailing.find()) {
            return trailing.group(1).trim();
        }

        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String sanitizeQueryText(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int cut = Integer.MAX_VALUE;
        cut = Math.min(cut, indexOrMax(lower, "you must"));
        cut = Math.min(cut, indexOrMax(lower, "then"));
        cut = Math.min(cut, indexOrMax(lower, "and then"));
        cut = Math.min(cut, indexOrMax(lower, "after that"));
        cut = Math.min(cut, indexOrMax(lower, "finally"));
        cut = Math.min(cut, indexOrMax(lower, "\n"));
        cut = Math.min(cut, indexOrMax(lower, "."));
        if (cut != Integer.MAX_VALUE && cut > 0) {
            return trimmed.substring(0, cut).trim();
        }
        return trimmed;
    }

    private int indexOrMax(String value, String token) {
        int idx = value.indexOf(token);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    private String toJson(List<JobListing> jobs) {
        try {
            return objectMapper.writeValueAsString(jobs);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize job results", e);
            return jobs.toString();
        }
    }

    private boolean isRecentRequest(String message) {
        String text = normalize(message);
        return text.contains("recent") || text.contains("latest") || text.contains("newest")
                || text.contains("new jobs") || text.contains("today") || text.contains("past week")
                || text.contains("last 7 days") || text.contains("last week");
    }

    private List<JobListing> sortByRecency(List<JobListing> jobs) {
        List<JobListing> sorted = new ArrayList<>(jobs);
        sorted.sort((a, b) -> Integer.compare(parseAgeMinutes(a.datePosted()), parseAgeMinutes(b.datePosted())));
        return sorted;
    }

    private int parseAgeMinutes(String datePosted) {
        if (datePosted == null || datePosted.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String text = datePosted.toLowerCase(Locale.ROOT).trim();
        if (text.contains("just now") || text.contains("moments ago") || text.contains("today")) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*(minute|hour|day|week|month)s?\\s*ago")
                .matcher(text);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            return switch (unit) {
                case "minute" -> value;
                case "hour" -> value * 60;
                case "day" -> value * 60 * 24;
                case "week" -> value * 60 * 24 * 7;
                case "month" -> value * 60 * 24 * 30;
                default -> Integer.MAX_VALUE;
            };
        }
        return Integer.MAX_VALUE;
    }
}
