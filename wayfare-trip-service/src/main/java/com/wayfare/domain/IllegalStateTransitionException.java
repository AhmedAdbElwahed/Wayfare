package com.wayfare.domain;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(TripStatus from, TripStatus to) {
        super("Trip cannot move from " + from + " to " + to);
    }
}
