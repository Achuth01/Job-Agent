package com.example.demo.api;

public record JobApplicationResponse(
        String url,
        String company,
        String cover,
        String role,
        Integer fitScore
) {}
