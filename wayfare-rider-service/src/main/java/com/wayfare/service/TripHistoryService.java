package com.wayfare.service;

import com.wayfare.domain.HistoryIndex;
import com.wayfare.repository.HistoryIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripHistoryService {

    private final HistoryIndexRepository historyIndexRepository;

    @Transactional(readOnly = true)
    public Page<HistoryIndex> getHistory(UUID userId, Pageable pageable) {
        return historyIndexRepository.findByKey_UserId(userId, pageable);
    }
}
