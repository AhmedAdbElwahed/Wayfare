package com.wayfare.service;

import com.wayfare.domain.Account;
import com.wayfare.domain.AccountRole;
import com.wayfare.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The only way an ADMIN account comes into existence: public registration
 * refuses that role. If {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD} are set,
 * the account is created at start-up when no account has that email yet.
 * An existing account is never modified, so rotating the env var does not
 * change the stored password and a RIDER/DRIVER can't be promoted by it.
 *
 * <p>No {@code AccountRegistered} event is published: admins have no rider or
 * driver record to provision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL:}")
    private String email;

    @Value("${ADMIN_PASSWORD:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD is shorter than {} characters; admin account not created", MIN_PASSWORD_LENGTH);
            return;
        }
        if (accountRepository.existsByEmail(email)) {
            return;
        }
        accountRepository.save(new Account(email, passwordEncoder.encode(password), AccountRole.ADMIN));
        log.info("Provisioned admin account {}", email);
    }
}
