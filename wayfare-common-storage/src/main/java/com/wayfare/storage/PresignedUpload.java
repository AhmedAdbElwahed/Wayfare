package com.wayfare.storage;

import java.time.Instant;

/** Where the client should HTTP PUT the file, and the key to hand back once it has. */
public record PresignedUpload(String uploadUrl, String objectKey, Instant expiresAt) {
}
