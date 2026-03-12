package com.example.demo.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CoverLetterTool {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterTool.class);
    private final ChatClient chatClient;

    public CoverLetterTool(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Tool(description = "Generate a tailored cover letter for a job application")
    public String generateCoverLetter(
            @ToolParam(description = "Job title and company name") String jobDetails,
            @ToolParam(description = "Key requirements from the job description") String requirements) {

        log.info("generate cover letter: jobDetailsLength={} requirementsLength={}",
                jobDetails == null ? 0 : jobDetails.length(),
                requirements == null ? 0 : requirements.length());
        return chatClient.prompt()
                .system("Write a concise, professional cover letter. Max 3 paragraphs.")
                .user("Job: " + jobDetails + "\nRequirements: " + requirements)
                .call()
                .content();
    }
}
