package com.example.demo.jobagent;

import com.example.demo.tools.ApplicationTrackerTool;
import com.example.demo.tools.CoverLetterTool;
import com.example.demo.tools.JobSearchTool;
import com.example.demo.tools.ResumeTailorTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JobApplicationAgent {

    private final ChatClient chatClient;

    public JobApplicationAgent(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            JobSearchTool jobSearchTool,
            ResumeTailorTool resumeTailorTool,
            CoverLetterTool coverLetterTool,
            ApplicationTrackerTool trackerTool,
            @Value("${app.agent.system-prompt}") String systemPrompt) {

        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .defaultTools(jobSearchTool, resumeTailorTool, coverLetterTool, trackerTool)
                .defaultAdvisors(
                        // Memory: remembers past conversation turns
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public String chat(String userMessage, String sessionId) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }
}
