package com.example.snowflake.core;

import java.time.Instant;

public final class SnowflakeDecoder {
    private SnowflakeDecoder() {}

    public static IdParts decode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID는 음수일 수 없습니다");
        }
        return new IdParts(
                Instant.ofEpochMilli(SnowflakeFormat.EPOCH_MILLIS + (id >>> 22)),
                (int) ((id >>> 12) & SnowflakeFormat.MAX_WORKER_ID),
                (int) (id & SnowflakeFormat.MAX_SEQUENCE));
    }
}
