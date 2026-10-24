package com.wayfare.exception;

/** The caller is authenticated but is not the rider/driver of this trip. */
public class TripAccessDeniedException extends RuntimeException {

    public TripAccessDeniedException(Object tripId) {
        super("You are not a participant of trip " + tripId);
    }
}
