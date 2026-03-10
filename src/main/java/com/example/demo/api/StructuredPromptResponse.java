package com.example.demo.api;

import java.util.List;

public record StructuredPromptResponse(String answer, List<String> keyPoints) {
}
