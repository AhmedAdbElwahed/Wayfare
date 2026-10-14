package com.wayfare.dto;

/**
 * Partial update — every field is optional. {@link com.wayfare.service.ProfileService}
 * only overwrites fields that are non-null, so a client can PATCH just
 * {@code locale} without resending the rest of the profile. The photo is not
 * settable here: it goes through the upload flow ({@code PUT /riders/me/photo}).
 */
public record UpdateProfileRequest(
        String name,
        String phone,
        String locale) {
}
