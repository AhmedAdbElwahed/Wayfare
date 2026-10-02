package com.wayfare.controller;

import java.util.UUID;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wayfare.dto.DocumentResponse;
import com.wayfare.dto.DriverResponse;
import com.wayfare.dto.ReviewDocumentRequest;
import com.wayfare.dto.SuspendRequest;
import com.wayfare.service.DocumentStorageService;
import com.wayfare.service.DriverService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Back-office review. Access is restricted to ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/drivers/admin/{driverId}")
@RequiredArgsConstructor
public class DriverAdminController {

    private final DriverService driverService;
    private final DocumentStorageService storageService;

    @GetMapping("/documents")
    public List<DocumentResponse> documents(@PathVariable UUID driverId) {
        return driverService.listDocuments(driverId).stream()
                .map(d -> DocumentResponse.from(d, storageService.downloadUrl(d))).toList();
    }

    @PutMapping("/documents/{documentId}/status")
    public DocumentResponse reviewDocument(@PathVariable UUID driverId, @PathVariable UUID documentId,
            @RequestBody @Valid ReviewDocumentRequest request) {
        var doc = driverService.reviewDocument(driverId, documentId, request.status());
        return DocumentResponse.from(doc, storageService.downloadUrl(doc));
    }

    @PostMapping("/approve")
    public DriverResponse approve(@PathVariable UUID driverId) {
        return DriverResponse.from(driverService.approve(driverId));
    }

    @PostMapping("/suspend")
    public DriverResponse suspend(@PathVariable UUID driverId, @RequestBody @Valid SuspendRequest request) {
        return DriverResponse.from(driverService.suspend(driverId, request.reason()));
    }
}
