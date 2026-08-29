package com.wayfare.controller;

import com.wayfare.dto.CreateRiderRequest;
import com.wayfare.dto.RiderResponse;
import com.wayfare.dto.TripHistoryEntryResponse;
import com.wayfare.dto.UpdateProfileRequest;
import com.wayfare.service.ProfileService;
import com.wayfare.service.TripHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/riders")
@RequiredArgsConstructor
public class RiderController {

    private final ProfileService profileService;
    private final TripHistoryService tripHistoryService;

    @PostMapping
    public ResponseEntity<RiderResponse> createRider(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateRiderRequest request) {
        RiderResponse response = RiderResponse.from(
                profileService.createProfile(UUID.fromString(jwt.getSubject()), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<RiderResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        RiderResponse response = RiderResponse.from(
                profileService.getProfile(UUID.fromString(jwt.getSubject())));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<RiderResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateProfileRequest request) {
        RiderResponse response = RiderResponse.from(
                profileService.updateProfile(UUID.fromString(jwt.getSubject()), request));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/trips")
    public Page<TripHistoryEntryResponse> getTripHistory(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return tripHistoryService.getHistory(UUID.fromString(jwt.getSubject()), pageable)
                .map(TripHistoryEntryResponse::from);
    }
}
