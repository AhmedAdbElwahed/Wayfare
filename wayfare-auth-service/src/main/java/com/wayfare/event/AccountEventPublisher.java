package com.wayfare.event;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link AccountRegisteredEvent} to the broker, but only once the
 * transaction that produced it has actually committed.
 *
 * <p>The indirection through Spring's application events exists for that
 * "only once committed" part. Calling {@link StreamBridge} straight from
 * {@code AuthService.register} would publish while the transaction is still
 * open, so a constraint violation on commit — a concurrent registration
 * winning the unique index on {@code email}, say — would leave a message on
 * the topic announcing an account that does not exist, and rider-service
 * would create a profile for it. Binding the send to
 * {@link TransactionPhase#AFTER_COMMIT} makes the row the precondition for
 * the message.
 *
 * <p>The reverse failure is still possible and is the accepted trade-off
 * here: commit succeeds, the broker is unreachable, and the event is lost
 * with no profile ever created. Closing that gap properly means a
 * transactional outbox (persist the event in the same transaction, relay it
 * separately), which is the standard next step if this becomes a real
 * problem. The consumer side is idempotent either way.
 */
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    /**
     * Binding name — {@code spring.cloud.stream.bindings.accountRegistered-out-0}
     * in this service's config maps it to the {@code account.events} topic.
     */
    private static final String BINDING = "accountRegistered-out-0";

    private final StreamBridge streamBridge;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountRegistered(AccountRegisteredEvent event) {
        streamBridge.send(BINDING, event);
    }
}
