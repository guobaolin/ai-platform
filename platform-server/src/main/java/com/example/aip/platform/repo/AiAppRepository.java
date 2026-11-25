package com.example.aip.platform.repo;

import com.example.aip.platform.domain.AiApp;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAppRepository extends JpaRepository<AiApp, String> {
}
