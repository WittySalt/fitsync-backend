package com.fitsync.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class DeviceLabelsTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"
                + "|Chrome on Windows",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"
                + " Edg/125.0|Edge on Windows",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5"
                + " Safari/605.1.15|Safari on macOS",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"
                + " Version/17.5 Mobile/15E148 Safari/604.1|Safari on iOS",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"
                + " CriOS/125.0 Mobile/15E148 Safari/604.1|Chrome on iOS",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile"
                + " Safari/537.36|Chrome on Android",
        "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0|Firefox on Linux",
        "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0"
                + " Safari/537.36|Chrome on ChromeOS",
        "curl/8.7.1|Unknown device",
        "Dalvik/2.1.0 (Linux; U; Android 14)|Android device"
    })
    void readsBrowserAndPlatformFromTheUserAgent(String userAgent, String expected) {
        assertThat(DeviceLabels.resolve(null, userAgent)).isEqualTo(expected);
    }

    @Test
    void aMissingUserAgentIsAnUnknownDevice() {
        assertThat(DeviceLabels.resolve(null, null)).isEqualTo("Unknown device");
        assertThat(DeviceLabels.resolve("   ", "")).isEqualTo("Unknown device");
    }

    @Test
    void aHeaderLabelWinsOverTheUserAgent() {
        assertThat(DeviceLabels.resolve("Work phone", "Mozilla/5.0 (Windows NT 10.0) Chrome/125.0"))
                .isEqualTo("Work phone");
    }

    @Test
    void aHeaderLabelLosesLineBreaksAndInvisibleCharacters() {
        assertThat(DeviceLabels.resolve("Kitchen\r\nX-Injected: 1\u200B tablet", null))
                .isEqualTo("Kitchen X-Injected: 1 tablet");
    }

    @Test
    void aHeaderLabelThatIsOnlyInvisibleFallsBackToTheUserAgent() {
        assertThat(DeviceLabels.resolve("\u202E\u200B\r\n", "Mozilla/5.0 (X11; Linux x86_64) Firefox/126.0"))
                .isEqualTo("Firefox on Linux");
    }

    @Test
    void aHeaderLabelIsCutToSixtyCodePoints() {
        String label = DeviceLabels.resolve("📱".repeat(80), null);
        assertThat(label.codePointCount(0, label.length())).isEqualTo(DeviceLabels.MAX_LENGTH);
    }
}
