package com.wayfare.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
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

    private Instant createdAt;

}
