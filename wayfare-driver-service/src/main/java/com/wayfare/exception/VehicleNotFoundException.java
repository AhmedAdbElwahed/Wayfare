package com.wayfare.exception;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(UUID driverId) {
        super("No vehicle registered for driver %s".formatted(driverId));
    }
}
