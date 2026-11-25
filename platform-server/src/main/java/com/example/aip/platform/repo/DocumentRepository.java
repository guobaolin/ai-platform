package com.example.aip.platform.repo;

import com.example.aip.platform.domain.Document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, String> {
}
