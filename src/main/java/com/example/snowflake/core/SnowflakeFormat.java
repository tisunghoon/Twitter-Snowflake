package com.example.snowflake.core;

import java.time.Instant;

public final class SnowflakeFormat {
    public static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    public static final long MAX_ELAPSED_MILLIS = (1L << 41) - 1;
    public static final int MAX_WORKER_ID = 1023;
    public static final int MAX_SEQUENCE = 4095;

    private SnowflakeFormat() {}
}
