package com.wayfare.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wayfare.dto.DocumentResponse;
import com.wayfare.dto.DocumentUploadRequest;
import com.wayfare.dto.DriverResponse;
import com.wayfare.dto.PhotoRequest;
import com.wayfare.dto.UploadUrlRequest;
import com.wayfare.dto.UploadUrlResponse;
import com.wayfare.dto.UpdateDriverProfileRequest;
import com.wayfare.dto.VehicleRequest;
import com.wayfare.dto.VehicleResponse;
import com.wayfare.service.DocumentStorageService;
import com.wayfare.service.DriverService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Driver-facing endpoints; the driver is always the JWT subject, never a path parameter. */
@RestController
@RequestMapping("/drivers/me")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final DocumentStorageService storageService;

    @GetMapping
    public DriverResponse me(@AuthenticationPrincipal Jwt jwt) {
        return DriverResponse.from(driverService.get(id(jwt)));
    }

    @PatchMapping
    public DriverResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateDriverProfileRequest request) {
        return DriverResponse.from(driverService.updateProfile(id(jwt), request));
    }

    @PutMapping("/vehicle")
    public VehicleResponse upsertVehicle(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid VehicleRequest request) {
        return VehicleResponse.from(driverService.upsertVehicle(id(jwt), request));
    }

    @GetMapping("/vehicle")
    public VehicleResponse vehicle(@AuthenticationPrincipal Jwt jwt) {
        return VehicleResponse.from(driverService.getVehicle(id(jwt)));
    }

    @PostMapping("/documents")
    public ResponseEntity<DocumentResponse> addDocument(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid DocumentUploadRequest request) {
        var doc = driverService.addDocument(id(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(doc, storageService.downloadUrl(doc)));
    }

    /** Step 1: get a presigned PUT URL. Step 2 is POST /documents with the returned key. */
    @PostMapping("/documents/upload-url")
    public UploadUrlResponse documentUploadUrl(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid UploadUrlRequest request) {
        if (request.kind() == null) {
            throw new com.wayfare.storage.InvalidUploadException("kind is required");
        }
        return UploadUrlResponse.from(storageService.documentUploadUrl(id(jwt), request.kind(), request.contentType()));
    }

    @PostMapping("/photo/upload-url")
    public UploadUrlResponse photoUploadUrl(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid UploadUrlRequest request) {
        return UploadUrlResponse.from(storageService.photoUploadUrl(id(jwt), request.contentType()));
    }

    @PutMapping("/photo")
    public DriverResponse setPhoto(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid PhotoRequest request) {
        return DriverResponse.from(driverService.setPhoto(id(jwt), request.objectKey()));
    }

    @GetMapping("/documents")
    public List<DocumentResponse> documents(@AuthenticationPrincipal Jwt jwt) {
        return driverService.listDocuments(id(jwt)).stream()
                .map(d -> DocumentResponse.from(d, storageService.downloadUrl(d))).toList();
    }

    @PostMapping("/submit")
    public DriverResponse submit(@AuthenticationPrincipal Jwt jwt) {
        return DriverResponse.from(driverService.submitForApproval(id(jwt)));
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
