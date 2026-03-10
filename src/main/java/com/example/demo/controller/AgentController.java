package com.example.demo.controller;

import com.example.demo.jobagent.JobApplicationAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final JobApplicationAgent agent;

    public AgentController(JobApplicationAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        String response = agent.chat(request.message(), request.sessionId());
        return ResponseEntity.ok(response);
    }

    public record ChatRequest(String message, String sessionId) {}
}