package com.example.snowflake.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

public final class MutableClock extends Clock {
    private final AtomicLong time;

    public MutableClock(long millis) {
        time = new AtomicLong(millis);
    }

    public void set(long millis) {
        time.set(millis);
    }

    @Override
    public long millis() {
        return time.get();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }
}
