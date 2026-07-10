package com.wayfare.domain;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "history_index")
public class HistoryIndex {

    @EmbeddedId
    private HistoryKey key; // (userId, tripId)

    private Instant occurredAt;

    // Full trip detail is owned by the Trip service

}
