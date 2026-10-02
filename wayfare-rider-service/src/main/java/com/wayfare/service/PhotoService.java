package com.wayfare.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wayfare.domain.Profile;
import com.wayfare.dto.UpdateProfileRequest;
import com.wayfare.storage.ObjectStorage;
import com.wayfare.storage.PresignedUpload;

import lombok.RequiredArgsConstructor;

/**
 * Profile photos live in the public-read media bucket under
 * {@code riders/{id}/photo/}; the profile stores the resulting public URL. The
 * prefix check in {@code requireUploaded} is what stops a rider pointing their
 * profile at somebody else's object.
 */
@Service
@RequiredArgsConstructor
public class PhotoService {

    static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);

    private final ObjectStorage storage;
    private final ProfileService profileService;

    private static String prefix(UUID riderId) {
        return "riders/" + riderId + "/photo/";
    }

    public PresignedUpload uploadUrl(UUID riderId, String contentType) {
        profileService.getProfile(riderId); // 404 for a rider with no profile rather than issuing a useless URL
        String key = ObjectStorage.newKey(prefix(riderId), contentType, TYPES);
        return storage.presignUpload(storage.mediaBucket(), key, UPLOAD_TTL);
    }

    /** Goes through updateProfile so the cached profile is evicted. */
    public Profile setPhoto(UUID riderId, String objectKey) {
        storage.requireUploaded(storage.mediaBucket(), objectKey, prefix(riderId), MAX_BYTES, TYPES);
        return profileService.updateProfile(riderId,
                new UpdateProfileRequest(null, null, storage.publicUrl(objectKey), null));
    }
}
