package com.fitsync.auth.service;

import java.util.Locale;

/**
 * Turns what a client says about itself into a short label for the session list: the {@code X-Device-Label}
 * header when it holds something printable, otherwise a browser and platform read from the User-Agent such as
 * "Chrome on Windows". Only the label is stored, never the User-Agent itself or the address.
 */
public final class DeviceLabels {

    public static final int MAX_LENGTH = 60;

    static final String UNKNOWN = "Unknown device";

    /** Long enough for any real User-Agent; the rest is not looked at. */
    private static final int MAX_USER_AGENT_SCAN = 512;

    private DeviceLabels() {
    }

    public static String resolve(String deviceLabelHeader, String userAgent) {
        String fromHeader = sanitize(deviceLabelHeader);
        if (!fromHeader.isEmpty()) {
            return fromHeader;
        }
        return fromUserAgent(userAgent);
    }

    /**
     * Drops control and format characters (line breaks, bidi overrides, zero-width marks) and angle brackets,
     * collapses runs of whitespace, trims, and keeps at most {@link #MAX_LENGTH} code points.
     */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean pendingSpace = false;
        int kept = 0;
        int[] codePoints = raw.codePoints().limit(MAX_USER_AGENT_SCAN).toArray();
        for (int codePoint : codePoints) {
            if (kept >= MAX_LENGTH) {
                break;
            }
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (!isPrintable(codePoint)) {
                continue;
            }
            if (pendingSpace) {
                if (kept + 1 >= MAX_LENGTH) {
                    break;
                }
                out.append(' ');
                kept++;
                pendingSpace = false;
            }
            out.appendCodePoint(codePoint);
            kept++;
        }
        return out.toString();
    }

    private static boolean isPrintable(int codePoint) {
        if (codePoint == '<' || codePoint == '>') {
            return false;
        }
        int type = Character.getType(codePoint);
        return type != Character.CONTROL
                && type != Character.FORMAT
                && type != Character.SURROGATE
                && type != Character.PRIVATE_USE
                && type != Character.UNASSIGNED
                && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR;
    }

    static String fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent.length() > MAX_USER_AGENT_SCAN
                ? userAgent.substring(0, MAX_USER_AGENT_SCAN)
                : userAgent;
        ua = ua.toLowerCase(Locale.ROOT);
        String browser = browser(ua);
        String platform = platform(ua);
        if (browser != null && platform != null) {
            return browser + " on " + platform;
        }
        if (browser != null) {
            return browser;
        }
        if (platform != null) {
            return platform + " device";
        }
        return UNKNOWN;
    }

    // Order matters: Edge, Opera and Samsung Internet also name Chrome, and Chrome also names Safari.
    private static String browser(String ua) {
        if (ua.contains("edg/") || ua.contains("edga/") || ua.contains("edgios/")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("samsungbrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("firefox/") || ua.contains("fxios/")) {
            return "Firefox";
        }
        if (ua.contains("chrome/") || ua.contains("crios/") || ua.contains("chromium/")) {
            return "Chrome";
        }
        if (ua.contains("safari/") && ua.contains("version/")) {
            return "Safari";
        }
        return null;
    }

    // iPadOS and iOS also say "like Mac OS X", and Android says Linux, so those are checked first.
    private static String platform(String ua) {
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return null;
    }
}
