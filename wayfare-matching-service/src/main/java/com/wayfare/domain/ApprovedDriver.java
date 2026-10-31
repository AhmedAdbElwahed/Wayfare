package com.wayfare.domain;

/** A driver the Driver Service reports as APPROVED. {@code ratingAvg} is null when unrated. */
public record ApprovedDriver(String driverId, Double ratingAvg) {
}
