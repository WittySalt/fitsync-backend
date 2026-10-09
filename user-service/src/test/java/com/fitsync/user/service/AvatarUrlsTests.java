package com.fitsync.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.fitsync.media.MediaStorageService;
import org.junit.jupiter.api.Test;

class AvatarUrlsTests {

    private static final String STORED = "https://fitsync-media.s3.ap-southeast-1.amazonaws.com/avatars/u-1/a.png";
    private static final String SIGNED = "https://s3.example/fitsync-media/avatars/u-1/a.png?X-Amz-Signature=abc";

    private final MediaStorageService media = mock(MediaStorageService.class);

    @Test
    void signsAStoredPublicUrlOfOurOwnAvatarForOneHour() {
        when(media.keyForUrl(STORED)).thenReturn(Optional.of("avatars/u-1/a.png"));
        when(media.presignedGetUrl("avatars/u-1/a.png", Duration.ofHours(1))).thenReturn(SIGNED);

        assertThat(AvatarUrls.readableUrl(STORED, media)).isEqualTo(SIGNED);
    }

    @Test
    void signsAStoredBareKey() {
        when(media.presignedGetUrl("avatars/u-1/a.png", Duration.ofHours(1))).thenReturn(SIGNED);

        assertThat(AvatarUrls.readableUrl("avatars/u-1/a.png", media)).isEqualTo(SIGNED);
    }

    @Test
    void passesThroughUrlsThatAreNotOurAvatars() {
        String foreign = "https://images.example/someone.png";
        when(media.keyForUrl(foreign)).thenReturn(Optional.empty());
        String catalogue = "https://fitsync-media.s3.ap-southeast-1.amazonaws.com/exercises/x/0.jpg";
        when(media.keyForUrl(catalogue)).thenReturn(Optional.of("exercises/x/0.jpg"));

        assertThat(AvatarUrls.readableUrl(foreign, media)).isEqualTo(foreign);
        assertThat(AvatarUrls.readableUrl(catalogue, media)).isEqualTo(catalogue);
        verify(media, never()).presignedGetUrl(anyString(), any());
    }

    @Test
    void keepsEmptyValuesAndWorksWithoutMediaStorage() {
        assertThat(AvatarUrls.readableUrl(null, media)).isNull();
        assertThat(AvatarUrls.readableUrl("", media)).isEmpty();
        assertThat(AvatarUrls.readableUrl(STORED, null)).isEqualTo(STORED);
    }

    @Test
    void returnsNullWhenSigningFailsBecauseThePublicUrlWouldOnlyAnswer403() {
        when(media.keyForUrl(STORED)).thenReturn(Optional.of("avatars/u-1/a.png"));
        when(media.presignedGetUrl(anyString(), any())).thenThrow(new IllegalStateException("no credentials"));

        assertThat(AvatarUrls.readableUrl(STORED, media)).isNull();
    }
}
