package com.example.demo.controller;

import com.example.demo.jobagent.JobApplicationAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final JobApplicationAgent agent;

    public AgentController(JobApplicationAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            log.warn("agent chat rejected: blank message");
            return ResponseEntity.badRequest().body("message must not be blank");
        }
        log.info("agent chat received: sessionId={} messageLength={}", request.sessionId(), request.message().length());
        if (agent.shouldAutoApply(request.message())) {
            log.info("agent auto-apply flow: sessionId={}", request.sessionId());
            return ResponseEntity.ok(agent.applyForJobs(request.message(), request.sessionId()));
        }

        String response = agent.chat(request.message(), request.sessionId());
        log.info("agent chat completed: sessionId={}", request.sessionId());
        return ResponseEntity.ok(response);
    }

    public record ChatRequest(String message, String sessionId) {}
}
