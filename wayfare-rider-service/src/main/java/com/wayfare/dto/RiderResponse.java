package com.wayfare.dto;

import java.time.Instant;
import java.util.UUID;

import com.wayfare.domain.Profile;

public record RiderResponse(
        UUID id,
        String name,
        String phone,
        String photoUrl,
        String locale,
        UUID defaultPaymentId,
        Instant createdAt) {
    public static RiderResponse from(Profile profile) {
        return new RiderResponse(
                profile.getId(),
                profile.getName(),
                profile.getPhone(),
                profile.getPhotoUrl(),
                profile.getLocale(),
                profile.getDefaultPaymentId(),
                profile.getCreatedAt());
    }
}
