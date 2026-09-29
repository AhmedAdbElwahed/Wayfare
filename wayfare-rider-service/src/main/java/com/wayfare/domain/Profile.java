package com.wayfare.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    private UUID id; // = Account.id from Auth Service (JWT sub) — no local user table to join to

    // Nullable because a profile is provisioned from AccountRegistered before
    // the rider has supplied either — see V5 migration. Both are mandatory on
    // the way in through POST /riders, enforced by CreateRiderRequest.
    private String name;

    private String phone;

    private String photoUrl;

    private String locale;

    private UUID defaultPaymentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Creation constructor. Unlike a normal entity, {@code id} is caller
     * supplied — it's the account id from the JWT, not a generated key.
     */
    public Profile(UUID id, String name, String phone, String photoUrl, String locale) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.photoUrl = photoUrl;
        this.locale = locale;
    }

    /**
     * The placeholder a newly registered rider gets: identity only, every
     * detail still unset. {@code name == null} is what marks a profile as
     * never filled in — {@code ProfileService.createProfile} treats such a row
     * as claimable rather than as a conflict.
     */
    public Profile(UUID id) {
        this.id = id;
    }
}
