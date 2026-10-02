package com.wayfare.storage;

/** The client-supplied object key/upload doesn't meet the rules (maps to 400 in the services). */
public class InvalidUploadException extends RuntimeException {
    public InvalidUploadException(String message) {
        super(message);
    }
}
