package com.wayfare.event;

import java.time.Instant;

import com.google.protobuf.Timestamp;

/** Conversions between {@link Instant} and the protobuf wire timestamp. */
public final class ProtoTimes {

    private ProtoTimes() {
    }

    public static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
