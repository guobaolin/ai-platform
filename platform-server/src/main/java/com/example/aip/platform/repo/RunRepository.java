package com.example.aip.platform.repo;

import java.time.Instant;
import java.util.List;

import com.example.aip.platform.domain.Run;
import com.example.aip.platform.domain.RunStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, String> {
    List<Run> findByStatusAndCreatedAtBefore(RunStatus status, Instant cutoff);
}
