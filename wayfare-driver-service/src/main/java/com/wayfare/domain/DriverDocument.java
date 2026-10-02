package com.wayfare.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documents")
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID driverId;

    @Enumerated(EnumType.STRING)
    private DocumentKind kind;

    private String objectKey; // key in the private documents bucket, never the bytes

    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.PENDING_REVIEW;

    private Instant expiresAt;
}
