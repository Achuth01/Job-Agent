package com.example.demo.repository;

import com.example.demo.models.ResumeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<ResumeDocument, String> {
    Optional<ResumeDocument> findTopByOrderByUploadedAtDesc();
}
