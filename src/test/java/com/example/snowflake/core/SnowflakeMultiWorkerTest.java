package com.example.snowflake.core;

import static org.junit.jupiter.api.Assertions.*;

import com.example.snowflake.support.MutableClock;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SnowflakeMultiWorkerTest {
    @Test
    void keepsTwoWorkersUniqueAtExactlyTheSameMillis() {
        var ids = new HashSet<Long>();
        for (int worker : new int[] {1, 2}) {
            var generator =
                    new SnowflakeGenerator(
                            worker, new MutableClock(SnowflakeFormat.EPOCH_MILLIS + 10));
            for (int sequence = 0; sequence < 2048; sequence++) {
                long id = generator.nextId();
                assertTrue(ids.add(id));
                var parts = SnowflakeDecoder.decode(id);
                assertEquals(worker, parts.workerId());
                assertEquals(sequence, parts.sequence());
                assertEquals(SnowflakeFormat.EPOCH_MILLIS + 10, parts.timestamp().toEpochMilli());
            }
        }
        assertEquals(4096, ids.size());
    }
}
