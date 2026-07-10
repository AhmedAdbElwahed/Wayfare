package com.wayfare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Credentials only — this is the identity/authentication record shared by
 * riders and drivers. Role-specific profile data (rider payment methods,
 * driver vehicles/documents, ...) lives in wayfare-rider-service /
 * wayfare-driver-service, keyed by this same id (the JWT subject).
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Creation constructor for a brand-new account. {@code id},
     * {@code status} and {@code createdAt} are intentionally not caller
     * supplied — the id is database generated, and status/createdAt take
     * their field defaults — so a new account can never be persisted in an
     * inconsistent state.
     */
    public Account(String email, String passwordHash, AccountRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }
}
