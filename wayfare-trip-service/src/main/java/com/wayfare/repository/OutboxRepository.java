package com.wayfare.repository;

import com.wayfare.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * The next batch to publish, oldest first. Ordered by {@code createdAt}: the
     * id is a random UUID, so ordering by it would not be chronological.
     *
     * <p>{@code PESSIMISTIC_WRITE} + lock timeout {@code -2} is Hibernate's
     * "SKIP LOCKED" ({@code SELECT ... FOR UPDATE SKIP LOCKED}): if two replicas
     * of the service run the relay at once, each locks and gets a different set of
     * rows, instead of both publishing the same ones.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
