package com.example.aip.platform.repo;

import com.example.aip.platform.domain.Dataset;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<Dataset, String> {
}
