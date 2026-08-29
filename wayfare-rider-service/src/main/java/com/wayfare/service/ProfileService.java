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

    @Transactional
    public Profile createProfile(UUID id, CreateRiderRequest request) {
        if (profileRepository.existsById(id)) {
            throw new ProfileAlreadyExistsException(id);
        }
        Profile profile = new Profile(id, request.name(), request.phone(), request.photoUrl(), request.locale());
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
