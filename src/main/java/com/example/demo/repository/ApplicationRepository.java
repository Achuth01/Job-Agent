package com.example.demo.repository;


import com.example.demo.models.ApplicationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationRecord, String> {
    // JpaRepository gives you save(), findAll(), findById(), delete() for free
    // Add custom queries if needed:
    List<ApplicationRecord> findByStatus(String status);
    List<ApplicationRecord> findByCompany(String company);
}