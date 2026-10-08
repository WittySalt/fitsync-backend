package com.fitsync.notification.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand, so every time rule can be checked at the exact minute it applies. */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.parse("2026-10-07T00:00:00Z");

    public void set(Instant instant) {
        this.now = instant;
    }

    public void set(String instant) {
        set(Instant.parse(instant));
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
