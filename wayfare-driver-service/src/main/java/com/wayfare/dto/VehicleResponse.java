package com.wayfare.dto;

import com.wayfare.domain.RideType;
import com.wayfare.domain.Vehicle;

public record VehicleResponse(String make, String model, String plate, String color, int capacity, RideType type) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(v.getMake(), v.getModel(), v.getPlate(), v.getColor(), v.getCapacity(), v.getType());
    }
}
