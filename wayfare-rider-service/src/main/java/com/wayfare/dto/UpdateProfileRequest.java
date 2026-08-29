package com.wayfare.dto;

/**
 * Partial update — every field is optional. {@link com.wayfare.service.ProfileService}
 * only overwrites fields that are non-null, so a client can PATCH just
 * {@code photoUrl} without resending the rest of the profile.
 */
public record UpdateProfileRequest(
        String name,
        String phone,
        String photoUrl,
        String locale) {
}
