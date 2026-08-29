package com.wayfare.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wayfare.domain.HistoryIndex;
import com.wayfare.domain.HistoryKey;

public interface HistoryIndexRepository extends JpaRepository<HistoryIndex, HistoryKey> {

    Page<HistoryIndex> findByKey_UserId(UUID userId, Pageable pageable);
}
