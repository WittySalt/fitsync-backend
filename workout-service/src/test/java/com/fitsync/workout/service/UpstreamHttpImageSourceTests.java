package com.fitsync.workout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class UpstreamHttpImageSourceTests {

    private static final String BASE_URL = "https://images.test/exercises/";
    private static final Duration BACKOFF = Duration.ofMillis(250);

    private final HttpClient httpClient = mock(HttpClient.class);
    private final List<Duration> pauses = new ArrayList<>();
    private final UpstreamHttpImageSource source = new UpstreamHttpImageSource(
            true, BASE_URL, httpClient, BACKOFF, pauses::add);

    @Test
    void waitsLongerBeforeEachRetryAndReturnsTheImageOnceItLoads() throws Exception {
        HttpResponse<byte[]> unavailable = response(503, Map.of());
        HttpResponse<byte[]> ok = response(200, Map.of("Content-Type", List.of("image/png")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(unavailable, unavailable, ok);

        assertThat(source.load("Bench_Press/0.png"))
                .hasValueSatisfying(image -> assertThat(image.contentType()).isEqualTo("image/png"));
        assertThat(pauses).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500));
    }

    @Test
    void doesNotWaitBeforeTheFirstAttemptOrAfterTheLast() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("reset"));

        assertThat(source.load("Bench_Press/0.jpg")).isEmpty();
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(pauses).hasSize(2);
    }

    @Test
    void aNotFoundIsSkippedWithoutWaiting() throws Exception {
        HttpResponse<byte[]> notFound = response(404, Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(notFound);

        assertThat(source.load("Bench_Press/0.jpg")).isEmpty();
        assertThat(pauses).isEmpty();
    }

    @Test
    void anInterruptedWaitStopsRetrying() throws Exception {
        UpstreamHttpImageSource interrupting = new UpstreamHttpImageSource(true, BASE_URL, httpClient, BACKOFF,
                duration -> {
                    throw new InterruptedException("stop");
                });
        HttpResponse<byte[]> unavailable = response(503, Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(unavailable);

        try {
            assertThat(interrupting.load("Bench_Press/0.jpg")).isEmpty();
            verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void withoutAnImageContentTypeTheTypeIsJudgedByTheFileName() throws Exception {
        HttpResponse<byte[]> ok = response(200, Map.of("Content-Type", List.of("application/octet-stream")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        assertThat(source.load("Bench_Press/0.webp"))
                .hasValueSatisfying(image -> assertThat(image.contentType()).isEqualTo("image/webp"));
        assertThat(source.load("Bench_Press/0.jpg"))
                .hasValueSatisfying(image -> assertThat(image.contentType()).isEqualTo("image/jpeg"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, Map<String, List<String>> headers) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new byte[] {1, 2, 3});
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }
}
