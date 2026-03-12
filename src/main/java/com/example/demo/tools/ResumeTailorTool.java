package com.example.demo.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import com.example.demo.repository.ResumeRepository;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Component
public class ResumeTailorTool {

    private static final Logger log = LoggerFactory.getLogger(ResumeTailorTool.class);
    private final ChatClient chatClient;
    private final ResumeRepository resumeRepository;

    public ResumeTailorTool(
            ChatClient.Builder builder,
            ResumeRepository resumeRepository) {
        // Inner ChatClient for resume tailoring - separate from main agent
        this.chatClient = builder.build();
        this.resumeRepository = resumeRepository;
    }

    @Tool(description = "Tailor the candidate's resume to match a specific job description")
    public String tailorResume(
            @ToolParam(description = "The job description to tailor resume for") String jobDescription) {

        log.info("tailor resume: jobDescriptionLength={}", jobDescription == null ? 0 : jobDescription.length());
        String baseResume = loadBaseResume();

        return chatClient.prompt()
                .system("You are an expert resume writer. Tailor the resume to the JD without fabricating experience.")
                .user("Base Resume:\n" + baseResume + "\n\nJob Description:\n" + jobDescription)
                .call()
                .content();
    }

    private String loadBaseResume() {
        Optional<String> content = resumeRepository.findTopByOrderByUploadedAtDesc()
                .map(resume -> resume.getContent());
        if (content.isEmpty()) {
            log.warn("no resume found for tailoring");
        }
        return content.orElseThrow(() ->
                new IllegalStateException("No resume found. Upload one via /api/resume/upload"));
    }
}
