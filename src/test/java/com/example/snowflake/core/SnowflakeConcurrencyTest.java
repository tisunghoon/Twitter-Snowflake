package com.example.snowflake.core;

import static org.junit.jupiter.api.Assertions.*;

import com.example.snowflake.support.ConcurrentCalls;
import com.example.snowflake.support.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SnowflakeConcurrencyTest {
    @Test
    void accountsForAll4097ConcurrentCalls() throws Exception {
        var generator =
                new SnowflakeGenerator(1, new MutableClock(SnowflakeFormat.EPOCH_MILLIS + 10));
        var exhausted = new AtomicInteger();
        var jobs = new ArrayList<Callable<List<Long>>>();
        for (int worker = 0; worker < 8; worker++) {
            int count = worker == 0 ? 513 : 512;
            jobs.add(
                    () -> {
                        var ids = new ArrayList<Long>();
                        for (int i = 0; i < count; i++) {
                            try {
                                ids.add(generator.nextId());
                            } catch (SnowflakeException failure) {
                                assertEquals(SnowflakeErrorCode.SEQUENCE_EXHAUSTED, failure.code());
                                exhausted.incrementAndGet();
                            }
                        }
                        return ids;
                    });
        }
        var results = ConcurrentCalls.collect(jobs, Duration.ofSeconds(10));
        var ids = new HashSet<Long>();
        var sequences = new HashSet<Integer>();
        int success = 0;
        for (var batch : results) {
            success += batch.size();
            for (long id : batch) {
                ids.add(id);
                sequences.add(SnowflakeDecoder.decode(id).sequence());
            }
        }
        assertEquals(4096, success);
        assertEquals(4096, ids.size());
        assertEquals(1, exhausted.get());
        for (int i = 0; i < 4096; i++) {
            assertTrue(sequences.contains(i));
        }
        assertEquals(
                SnowflakeErrorCode.SEQUENCE_EXHAUSTED,
                assertThrows(SnowflakeException.class, generator::nextId).code());
    }
}
