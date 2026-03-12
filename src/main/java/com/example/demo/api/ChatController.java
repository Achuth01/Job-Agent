package com.example.demo.api;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping
    public ResponseEntity<String> chat(@RequestParam String prompt) {
        if (prompt == null || prompt.isBlank()) {
            log.warn("chat request rejected: blank prompt");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("prompt must not be blank");
        }

        log.info("chat request received: promptLength={}", prompt.length());
        String response = chatClient.prompt()
                .advisors(new SimpleLoggerAdvisor())
                .user(prompt)
                .call()
                .content();

        log.info("chat request completed");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/structured")
    public ResponseEntity<StructuredPromptResponse> structuredChat(@RequestBody PromptRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            log.warn("structured chat request rejected: blank prompt");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        log.info("structured chat request received: promptLength={}", request.prompt().length());
        BeanOutputConverter<StructuredPromptResponse> converter =
                new BeanOutputConverter<>(StructuredPromptResponse.class);

        StructuredPromptResponse content = chatClient.prompt()
                .advisors(new SimpleLoggerAdvisor())
                .user(user -> user.text("""
                        Return a response for the following prompt in the required JSON format.
                        Prompt: {prompt}

                        {format}
                        """)
                        .param("prompt", request.prompt())
                        .param("format", converter.getFormat()))
                .call()
                .entity(StructuredPromptResponse.class);

        log.info("structured chat request completed");
        return ResponseEntity.ok(content);
    }

    @PostMapping(value = "/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> fluxChat(@RequestBody PromptRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            log.warn("flux chat request rejected: blank prompt");
            return Flux.error(new IllegalArgumentException("prompt must not be blank"));
        }

        log.info("flux chat request received: promptLength={}", request.prompt().length());
        return chatClient.prompt()
                .advisors(new SimpleLoggerAdvisor())
                .user(request.prompt())
                .stream()
                .content();
    }
}
