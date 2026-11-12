package com.example.snowflake.core;

import java.time.Clock;
import java.util.Objects;

public final class SnowflakeGenerator {
    private final int workerId;
    private final Clock clock;
    private long lastTimestamp = -1;
    private int sequence;

    public SnowflakeGenerator(int workerId, Clock clock) {
        if (workerId < 0 || workerId > SnowflakeFormat.MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId는 0~1023이어야 합니다");
        }
        this.workerId = workerId;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized long nextId() {
        long now = clock.millis();
        if (now < SnowflakeFormat.EPOCH_MILLIS
                || now > SnowflakeFormat.EPOCH_MILLIS + SnowflakeFormat.MAX_ELAPSED_MILLIS) {
            throw new SnowflakeException(SnowflakeErrorCode.TIME_OUT_OF_RANGE);
        }
        if (now < lastTimestamp) {
            throw new SnowflakeException(SnowflakeErrorCode.CLOCK_ROLLBACK);
        }
        int candidate = now == lastTimestamp ? sequence + 1 : 0;
        if (candidate > SnowflakeFormat.MAX_SEQUENCE) {
            throw new SnowflakeException(SnowflakeErrorCode.SEQUENCE_EXHAUSTED);
        }
        long id =
                ((now - SnowflakeFormat.EPOCH_MILLIS) << 22) | ((long) workerId << 12) | candidate;
        lastTimestamp = now;
        sequence = candidate;
        return id;
    }
}
