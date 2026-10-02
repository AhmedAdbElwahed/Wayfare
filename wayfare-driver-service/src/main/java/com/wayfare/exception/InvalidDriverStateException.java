package com.wayfare.exception;

/** The requested transition isn't allowed from the driver's current state (maps to 409). */
public class InvalidDriverStateException extends RuntimeException {
    public InvalidDriverStateException(String message) {
        super(message);
    }
}
