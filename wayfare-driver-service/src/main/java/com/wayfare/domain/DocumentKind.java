package com.wayfare.domain;

import java.util.EnumSet;
import java.util.Set;

public enum DocumentKind {
    LICENSE, INSURANCE, VEHICLE_REGISTRATION;

    /** Every kind is required for approval; expiry of any of them suspends the driver. */
    public static final Set<DocumentKind> REQUIRED = EnumSet.allOf(DocumentKind.class);
}
