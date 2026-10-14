package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.wayfare.domain.Profile;
import com.wayfare.storage.InvalidUploadException;
import com.wayfare.storage.ObjectStorage;

class PhotoServiceTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final PhotoService service = new PhotoService(storage, profiles);
    private final UUID id = UUID.randomUUID();

    @Test
    void setPhotoStoresPublicUrlOfVerifiedObject() {
        String key = "riders/" + id + "/photo/a.jpg";
        when(storage.mediaBucket()).thenReturn("media");
        when(storage.publicUrl(key)).thenReturn("http://cdn/media/" + key);
        when(profiles.updatePhoto(eq(id), any())).thenReturn(new Profile(id));

        service.setPhoto(id, key);

        verify(storage).requireUploaded(eq("media"), eq(key), eq("riders/" + id + "/photo/"), anyLong(), any());
        verify(profiles).updatePhoto(id, "http://cdn/media/" + key);
    }

    @Test
    void invalidUploadNeverTouchesTheProfile() {
        when(storage.mediaBucket()).thenReturn("media");
        when(storage.requireUploaded(any(), any(), any(), anyLong(), any()))
                .thenThrow(new InvalidUploadException("bad"));

        assertThatThrownBy(() -> service.setPhoto(id, "riders/other/photo/x.jpg"))
                .isInstanceOf(InvalidUploadException.class);
        verify(profiles, never()).updatePhoto(any(), any());
    }
}
