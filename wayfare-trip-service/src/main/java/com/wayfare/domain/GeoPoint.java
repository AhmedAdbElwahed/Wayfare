package com.wayfare.domain;

import jakarta.persistence.Embeddable;

/**
 * A latitude/longitude pair. {@code @Embeddable} means it has no table of its
 * own: its two fields become columns of whichever entity embeds it (Trip embeds
 * it twice, with different column names — see the overrides there).
 */
@Embeddable
public record GeoPoint(double lat, double lng) {
}
