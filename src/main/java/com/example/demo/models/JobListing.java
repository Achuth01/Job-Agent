package com.example.demo.models;

public record JobListing(
        String id,
        String title,
        String company,
        String location,
        String salary,
        String skills,
        String source,
        String url
) {}
