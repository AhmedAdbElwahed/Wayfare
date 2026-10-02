package com.wayfare.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    private UUID id; // = Account.id from auth-service (JWT sub) — caller supplied, not generated

    private String name;

    private String phone;

    private String email;

    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status = DriverStatus.ONBOARDING;

    private BigDecimal ratingAvg;

    private Instant approvedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Driver(UUID id, String email) {
        this.id = id;
        this.email = email;
    }
}
