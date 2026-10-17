package com.wayfare.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wayfare.domain.HistoryIndex;
import com.wayfare.domain.HistoryKey;

public interface HistoryIndexRepository extends JpaRepository<HistoryIndex, HistoryKey> {

    Page<HistoryIndex> findByKey_UserId(UUID userId, Pageable pageable);

    @Modifying
    @Query("delete from HistoryIndex h where h.key.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
