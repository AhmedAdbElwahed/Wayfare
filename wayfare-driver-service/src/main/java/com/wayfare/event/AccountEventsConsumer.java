package com.wayfare.event;

import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wayfare.service.DriverService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provisions the driver record when a DRIVER account registers. The bean name
 * is the binding name ({@code onAccountRegistered-in-0}); renaming the method
 * silently unsubscribes it. Redelivery is harmless: ensureDriver is idempotent.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AccountEventsConsumer {

    private static final String DRIVER_ROLE = "DRIVER";

    @Bean
    public Consumer<AccountRegisteredEvent> onAccountRegistered(DriverService driverService) {
        return event -> {
            if (!DRIVER_ROLE.equals(event.getRole())) {
                return; // riders belong to rider-service
            }
            UUID accountId = UUID.fromString(event.getAccountId());
            if (driverService.ensureDriver(accountId, event.getEmail())) {
                log.info("Provisioned driver {}", accountId);
            }
        };
    }
}
