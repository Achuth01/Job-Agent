package com.example.demo.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
public class ResumeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String fileName;
    private String contentType;

    @Lob
    private String content;

    private LocalDateTime uploadedAt;

    public ResumeDocument() {
    }

    public ResumeDocument(String fileName, String contentType, String content, LocalDateTime uploadedAt) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.uploadedAt = uploadedAt;
    }

    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
