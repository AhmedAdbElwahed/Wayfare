package com.wayfare.dto;

import com.wayfare.domain.RideType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotBlank String make,
        @NotBlank String model,
        @NotBlank String plate,
        String color,
        @Min(1) @Max(12) int capacity,
        @NotNull RideType type) {
}
