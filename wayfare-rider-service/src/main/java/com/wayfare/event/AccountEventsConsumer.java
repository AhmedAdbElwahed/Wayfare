package com.wayfare.event;

import com.wayfare.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Creates the rider's profile row in reaction to registration, so a profile
 * exists from the moment the account does — no client call in between, and no
 * window where {@code GET /riders/me} 404s for someone who just signed up.
 *
 * <p>A {@code @Bean} of type {@link Consumer} is Spring Cloud Stream's
 * functional model: the bean *name* is the binding name, so this method being
 * called {@code onAccountRegistered} is what ties it to
 * {@code onAccountRegistered-in-0} in configuration. Renaming the method
 * silently unsubscribes it.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AccountEventsConsumer {

    private static final String RIDER_ROLE = "RIDER";

    @Bean
    public Consumer<AccountRegisteredEvent> onAccountRegistered(ProfileService profileService) {
        return event -> {
            // Drivers register through the same topic; their profile data
            // belongs to wayfare-driver-service, so there is nothing to do
            // here. Same for any role this version doesn't know about.
            if (!RIDER_ROLE.equals(event.role())) {
                return;
            }
            // Kafka delivery is at-least-once, so this runs more than once for
            // the same account after a consumer restart mid-batch. ensureProfile
            // is idempotent, which is what makes redelivery a non-event rather
            // than a duplicate-key error and a poison message.
            boolean created = profileService.ensureProfile(event.accountId());
            if (created) {
                log.info("Provisioned profile for rider {}", event.accountId());
            }
        };
    }
}
