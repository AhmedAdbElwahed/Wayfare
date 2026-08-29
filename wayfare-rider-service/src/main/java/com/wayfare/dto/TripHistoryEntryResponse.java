package com.wayfare.dto;

import com.wayfare.domain.HistoryIndex;

import java.time.Instant;
import java.util.UUID;

public record TripHistoryEntryResponse(UUID tripId, Instant occurredAt) {
    public static TripHistoryEntryResponse from(HistoryIndex historyIndex) {
        return new TripHistoryEntryResponse(historyIndex.getKey().getTripId(), historyIndex.getOccurredAt());
    }
}
