package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;

import com.wayfare.domain.Profile;
import com.wayfare.dto.RiderResponse;
import com.wayfare.dto.UpdateProfileRequest;
import com.wayfare.repository.ProfileRepository;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProfileCachingTest.Config.class)
class ProfileCachingTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean ProfileRepository profileRepository() { return mock(ProfileRepository.class); }
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager("riders"); }
        @Bean ProfileService profileService(ProfileRepository repo) { return new ProfileService(repo); }
    }

    @Autowired ProfileService service;
    @Autowired ProfileRepository repo;
    @Autowired CacheManager cacheManager;

    @Test
    void secondReadIsServedFromCacheAndWriteEvicts() {
        UUID id = UUID.randomUUID();
        Profile profile = new Profile(id, "Ann", "+100", null, "en");
        when(repo.findById(id)).thenReturn(Optional.of(profile));
        when(repo.save(profile)).thenReturn(profile);

        service.getRider(id);
        service.getRider(id);
        verify(repo, times(1)).findById(id);

        service.updateProfile(id, new UpdateProfileRequest("Bea", null, null, null));
        // updateProfile itself reads once; the next getRider must reload.
        service.getRider(id);
        verify(repo, times(3)).findById(id);
        assertThat(service.getRider(id).name()).isEqualTo("Bea");
        verify(repo, times(3)).findById(id);
    }

    @Test
    void cachedDtoSurvivesJdkSerialization() throws Exception {
        RiderResponse dto = RiderResponse.from(new Profile(UUID.randomUUID(), "Ann", "+100", null, "en"));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(dto); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(dto);
        }
    }
}
