package com.example.snowflake.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SnowflakeDecoderTest {
    @Test
    void decodesKnownValueAndMaximum() {
        assertEquals(
                new IdParts(Instant.parse("2026-01-01T00:00:00.001Z"), 1, 0),
                SnowflakeDecoder.decode(4198400));
        var maximum = SnowflakeDecoder.decode(Long.MAX_VALUE);
        assertEquals(
                SnowflakeFormat.EPOCH_MILLIS + SnowflakeFormat.MAX_ELAPSED_MILLIS,
                maximum.timestamp().toEpochMilli());
        assertEquals(1023, maximum.workerId());
        assertEquals(4095, maximum.sequence());
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> SnowflakeDecoder.decode(-1));
    }
}
