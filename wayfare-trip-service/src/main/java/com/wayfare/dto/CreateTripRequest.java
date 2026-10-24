package com.wayfare.dto;

import com.wayfare.domain.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateTripRequest(@NotNull @Valid GeoPoint pickup, @NotNull @Valid GeoPoint dropoff) {
}
