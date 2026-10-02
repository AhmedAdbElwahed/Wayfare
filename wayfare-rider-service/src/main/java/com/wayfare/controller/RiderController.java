package com.wayfare.controller;

import com.wayfare.dto.CreateRiderRequest;
import com.wayfare.dto.PhotoRequest;
import com.wayfare.dto.PhotoUploadUrlRequest;
import com.wayfare.dto.RiderResponse;
import com.wayfare.dto.UploadUrlResponse;
import com.wayfare.dto.TripHistoryEntryResponse;
import com.wayfare.dto.UpdateProfileRequest;
import com.wayfare.service.PhotoService;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    private final PhotoService photoService;

    @PostMapping
    public ResponseEntity<RiderResponse> createRider(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateRiderRequest request) {
        RiderResponse response = RiderResponse.from(
                profileService.createProfile(UUID.fromString(jwt.getSubject()), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<RiderResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        RiderResponse response = profileService.getRider(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<RiderResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateProfileRequest request) {
        RiderResponse response = RiderResponse.from(
                profileService.updateProfile(UUID.fromString(jwt.getSubject()), request));
        return ResponseEntity.ok(response);
    }

    /** Step 1 of a photo change: presigned PUT URL. Step 2 is PUT /riders/me/photo. */
    @PostMapping("/me/photo/upload-url")
    public UploadUrlResponse photoUploadUrl(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid PhotoUploadUrlRequest request) {
        return UploadUrlResponse.from(photoService.uploadUrl(UUID.fromString(jwt.getSubject()), request.contentType()));
    }

    @PutMapping("/me/photo")
    public RiderResponse setPhoto(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid PhotoRequest request) {
        return RiderResponse.from(photoService.setPhoto(UUID.fromString(jwt.getSubject()), request.objectKey()));
    }

    @GetMapping("/me/trips")
    public Page<TripHistoryEntryResponse> getTripHistory(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return tripHistoryService.getHistory(UUID.fromString(jwt.getSubject()), pageable)
                .map(TripHistoryEntryResponse::from);
    }
}
