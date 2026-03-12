package com.example.demo.models;

public record JobListing(
        String id,
        String title,
        String company,
        String location,
        String jobType,
        String datePosted,
        String salaryMin,
        String salaryMax,
        String salaryCurrency,
        String salaryInterval,
        String description,
        String source,
        String url
) {}
