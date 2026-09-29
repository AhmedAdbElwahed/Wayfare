package com.wayfare.service;

import com.wayfare.domain.Profile;
import com.wayfare.dto.CreateRiderRequest;
import com.wayfare.dto.UpdateProfileRequest;
import com.wayfare.exception.ProfileAlreadyExistsException;
import com.wayfare.exception.ProfileNotFoundException;
import com.wayfare.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public Profile getProfile(UUID id) {
        return profileRepository.findById(id).orElseThrow(() -> new ProfileNotFoundException(id));
    }

    /**
     * Provisions the placeholder profile for a newly registered rider. Returns
     * whether a row was actually written, so the caller can tell a real
     * provisioning apart from a redelivery of the same event.
     *
     * <p>Safe to call repeatedly by design — see
     * {@link com.wayfare.event.AccountEventsConsumer}.
     */
    @Transactional
    public boolean ensureProfile(UUID id) {
        if (profileRepository.existsById(id)) {
            return false;
        }
        profileRepository.save(new Profile(id));
        return true;
    }

    /**
     * Fills in the rider's own details. Since AccountRegistered now creates
     * the row first, the common path here is claiming that placeholder rather
     * than inserting — an existing row is only a conflict if someone has
     * already filled it in, which {@code name != null} identifies. Treating
     * every existing row as a conflict would 409 every rider who registered
     * after the consumer went live.
     */
    @Transactional
    public Profile createProfile(UUID id, CreateRiderRequest request) {
        Profile profile = profileRepository.findById(id)
                .orElseGet(() -> new Profile(id));
        if (profile.getName() != null) {
            throw new ProfileAlreadyExistsException(id);
        }
        profile.setName(request.name());
        profile.setPhone(request.phone());
        profile.setPhotoUrl(request.photoUrl());
        profile.setLocale(request.locale());
        return profileRepository.save(profile);
    }

    @Transactional
    public Profile updateProfile(UUID id, UpdateProfileRequest request) {
        Profile profile = profileRepository.findById(id).orElseThrow(() -> new ProfileNotFoundException(id));
        if (request.name() != null) {
            profile.setName(request.name());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone());
        }
        if (request.photoUrl() != null) {
            profile.setPhotoUrl(request.photoUrl());
        }
        if (request.locale() != null) {
            profile.setLocale(request.locale());
        }
        return profileRepository.save(profile);
    }
}
