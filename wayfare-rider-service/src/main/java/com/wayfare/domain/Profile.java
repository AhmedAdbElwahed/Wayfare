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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
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
}
