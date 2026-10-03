package com.telecom.repository;

import com.telecom.entity.IngestionJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, String> {

    Page<IngestionJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
