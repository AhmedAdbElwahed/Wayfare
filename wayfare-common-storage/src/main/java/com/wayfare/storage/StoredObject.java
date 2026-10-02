package com.wayfare.storage;

/** Metadata of an object that actually exists in the bucket. */
public record StoredObject(String key, long size, String contentType) {
}
