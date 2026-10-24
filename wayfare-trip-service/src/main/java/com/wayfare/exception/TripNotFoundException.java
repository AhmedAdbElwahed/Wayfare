package com.wayfare.exception;

public class TripNotFoundException extends RuntimeException {

    public TripNotFoundException(Object id) {
        super("Trip " + id + " not found");
    }
}
