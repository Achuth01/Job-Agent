package com.example.demo.tools;

import com.example.demo.models.JobListing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class JobSearchTool {

    private static final int MAX_RESULTS_PER_SOURCE = 20;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JobSearchTool(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Search for job listings by skill keywords and location. Returns list of matching jobs.")
    public List<JobListing> searchJobs(
            @ToolParam(description = "Skills or job title e.g. 'Java Spring AI backend'") String keywords,
            @ToolParam(description = "Location e.g. 'Hyderabad' or 'remote'") String location) {

        List<JobListing> results = new ArrayList<>();
        results.addAll(fetchRemotive(keywords, location));
        results.addAll(fetchArbeitnow(keywords, location));
        return results;
    }

    private List<JobListing> fetchRemotive(String keywords, String location) {
        List<JobListing> results = new ArrayList<>();
        String query = keywords == null ? "" : keywords.trim();

        String uri = UriComponentsBuilder
                .fromUriString("https://remotive.com/api/remote-jobs")
                .queryParam("search", query)
                .build()
                .toUriString();

        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode jobs = root.path("jobs");
            int count = 0;

            for (JsonNode job : jobs) {
                if (count >= MAX_RESULTS_PER_SOURCE) {
                    break;
                }

                String title = job.path("title").asText("");
                String company = job.path("company_name").asText("");
                String jobLocation = job.path("candidate_required_location").asText("Remote");
                String salary = job.path("salary").asText("");
                String url = job.path("url").asText("");
                String tags = joinTextArray(job.path("tags"));

                if (!matchesLocation(location, jobLocation, true)) {
                    continue;
                }
                if (!matchesKeywords(title + " " + company + " " + tags, keywords)) {
                    continue;
                }

                results.add(new JobListing(
                        job.path("id").asText(""),
                        title,
                        company,
                        jobLocation,
                        salary,
                        tags,
                        "remotive",
                        url
                ));
                count++;
            }
        } catch (Exception ignored) {
        }

        return results;
    }

    private List<JobListing> fetchArbeitnow(String keywords, String location) {
        List<JobListing> results = new ArrayList<>();
        String uri = "https://www.arbeitnow.com/api/job-board-api";

        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode jobs = root.path("data");
            int count = 0;

            for (JsonNode job : jobs) {
                if (count >= MAX_RESULTS_PER_SOURCE) {
                    break;
                }

                String title = job.path("title").asText("");
                String company = job.path("company_name").asText("");
                String jobLocation = job.path("location").asText("");
                boolean remote = job.path("remote").asBoolean(false);
                String url = job.path("url").asText("");
                String tags = joinTextArray(job.path("tags"));
                String description = job.path("description").asText("");

                if (!matchesLocation(location, jobLocation, remote)) {
                    continue;
                }
                if (!matchesKeywords(title + " " + company + " " + tags + " " + description, keywords)) {
                    continue;
                }

                results.add(new JobListing(
                        job.path("slug").asText(""),
                        title,
                        company,
                        remote ? "Remote" : jobLocation,
                        "",
                        tags,
                        "arbeitnow",
                        url
                ));
                count++;
            }
        } catch (Exception ignored) {
        }

        return results;
    }

    private boolean matchesKeywords(String text, String keywords) {
        String kw = normalize(keywords);
        if (kw.isEmpty()) {
            return true;
        }

        String haystack = normalize(text);
        for (String token : kw.split(" ")) {
            if (!token.isBlank() && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLocation(String requested, String jobLocation, boolean remote) {
        String req = normalize(requested);
        if (req.isEmpty()) {
            return true;
        }
        if (req.contains("remote")) {
            return remote || normalize(jobLocation).contains("remote");
        }
        return normalize(jobLocation).contains(req);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String joinTextArray(JsonNode array) {
        if (array == null || !array.isArray()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.asText("").isBlank()) {
                items.add(node.asText(""));
            }
        }
        return String.join(", ", items);
    }
}
