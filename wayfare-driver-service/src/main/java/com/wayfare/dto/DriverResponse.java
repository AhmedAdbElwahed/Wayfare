package com.wayfare.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.wayfare.domain.Driver;
import com.wayfare.domain.DriverStatus;

public record DriverResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String photoUrl,
        DriverStatus status,
        BigDecimal ratingAvg,
        Instant approvedAt,
        Instant createdAt) {
    public static DriverResponse from(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getPhone(), d.getEmail(), d.getPhotoUrl(), d.getStatus(),
                d.getRatingAvg(), d.getApprovedAt(), d.getCreatedAt());
    }
}
