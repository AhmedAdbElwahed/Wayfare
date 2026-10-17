package com.wayfare.event;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Sends {@link AccountDeletedEvent} once the deleting transaction has
 * committed (same reasoning as {@link AccountEventPublisher}). Same accepted
 * trade-off too: if the broker is down right after commit the event is lost,
 * and the account stays soft-deleted with downstream data un-erased until the
 * event is replayed (the row is kept precisely so it can be).
 */
@Component
@RequiredArgsConstructor
public class AccountDeletedEventPublisher {

    /** {@code spring.cloud.stream.bindings.accountDeleted-out-0} maps this to the {@code account.deleted} topic. */
    private static final String BINDING = "accountDeleted-out-0";

    private final StreamBridge streamBridge;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeleted(AccountDeletedEvent event) {
        streamBridge.send(BINDING, event);
    }
}
