package com.example.demo.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import com.example.demo.repository.ResumeRepository;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ResumeTailorTool {

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
        return content.orElseThrow(() ->
                new IllegalStateException("No resume found. Upload one via /api/resume/upload"));
    }
}
