package com.wayfare.event;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Publishes to {@code driver.events} only after the status change has
 * committed, so the topic never announces a transition that rolled back. Same
 * accepted trade-off as auth-service's publisher: a broker outage after commit
 * loses the event; an outbox is the fix if that matters.
 */
@Component
@RequiredArgsConstructor
public class DriverEventPublisher {

    /** Maps to the {@code driver.events} topic in this service's config. */
    private static final String BINDING = "driverEvents-out-0";

    private final StreamBridge streamBridge;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(DriverStatusChangedEvent event) {
        streamBridge.send(BINDING, event);
    }
}
