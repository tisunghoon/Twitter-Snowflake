package com.example.snowflake.core;

import static com.example.snowflake.core.SnowflakeFormat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.snowflake.support.MutableClock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SnowflakeGeneratorTest {
    @Test
    void generatesKnownValues() {
        assertEquals(0, new SnowflakeGenerator(0, new MutableClock(EPOCH_MILLIS)).nextId());
        var generator = new SnowflakeGenerator(1, new MutableClock(EPOCH_MILLIS + 1));
        assertEquals(4198400, generator.nextId());
        assertEquals(4198401, generator.nextId());
    }

    @Test
    void rejectsInvalidConstruction() {
        var clock = new MutableClock(EPOCH_MILLIS);
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(-1, clock));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(1024, clock));
        assertThrows(NullPointerException.class, () -> new SnowflakeGenerator(0, null));
    }

    @Test
    void preservesStateAfterRollbackAndRangeFailure() {
        var clock = new MutableClock(EPOCH_MILLIS + 10);
        var generator = new SnowflakeGenerator(1, clock);
        long first = generator.nextId();
        clock.set(EPOCH_MILLIS + 9);
        error(generator, SnowflakeErrorCode.CLOCK_ROLLBACK);
        for (long time :
                new long[] {
                    EPOCH_MILLIS - 1,
                    EPOCH_MILLIS + MAX_ELAPSED_MILLIS + 1,
                    Long.MIN_VALUE,
                    Long.MAX_VALUE
                }) {
            clock.set(time);
            error(generator, SnowflakeErrorCode.TIME_OUT_OF_RANGE);
        }
        clock.set(EPOCH_MILLIS + 10);
        assertEquals(first + 1, generator.nextId());
        clock.set(EPOCH_MILLIS + 11);
        assertEquals(0, SnowflakeDecoder.decode(generator.nextId()).sequence());
    }

    @Test
    void preservesExhaustionUntilNextMillis() {
        var clock = new MutableClock(EPOCH_MILLIS + 10);
        var generator = new SnowflakeGenerator(1, clock);
        var ids = new HashSet<Long>();
        for (int i = 0; i < 4096; i++) {
            long id = generator.nextId();
            ids.add(id);
            assertEquals(i, SnowflakeDecoder.decode(id).sequence());
        }
        assertEquals(4096, ids.size());
        error(generator, SnowflakeErrorCode.SEQUENCE_EXHAUSTED);
        error(generator, SnowflakeErrorCode.SEQUENCE_EXHAUSTED);
        clock.set(EPOCH_MILLIS + 9);
        error(generator, SnowflakeErrorCode.CLOCK_ROLLBACK);
        clock.set(EPOCH_MILLIS + 10);
        error(generator, SnowflakeErrorCode.SEQUENCE_EXHAUSTED);
        clock.set(EPOCH_MILLIS + 11);
        assertEquals(0, SnowflakeDecoder.decode(generator.nextId()).sequence());
    }

    @Test
    void supportsFirstFailureAndLastValidMillis() {
        var clock = new MutableClock(EPOCH_MILLIS - 1);
        var generator = new SnowflakeGenerator(0, clock);
        error(generator, SnowflakeErrorCode.TIME_OUT_OF_RANGE);
        clock.set(EPOCH_MILLIS);
        assertEquals(0, generator.nextId());
        clock.set(EPOCH_MILLIS + MAX_ELAPSED_MILLIS);
        assertEquals(MAX_ELAPSED_MILLIS << 22, generator.nextId());
        clock.set(EPOCH_MILLIS + MAX_ELAPSED_MILLIS + 1);
        error(generator, SnowflakeErrorCode.TIME_OUT_OF_RANGE);
        clock.set(EPOCH_MILLIS + MAX_ELAPSED_MILLIS);
        assertEquals((MAX_ELAPSED_MILLIS << 22) + 1, generator.nextId());
    }

    @Test
    void generatesUniqueIdsConcurrently() throws Exception {
        var generator = new SnowflakeGenerator(1, new MutableClock(EPOCH_MILLIS + 10));
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    var ids = new ArrayList<Long>();
                                    for (int i = 0; i < 256; i++) {
                                        ids.add(generator.nextId());
                                    }
                                    return ids;
                                }));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            start.countDown();
            var ids = new HashSet<Long>();
            var sequences = new HashSet<Integer>();
            for (var future : futures) {
                for (long id :
                        future.get(
                                Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    ids.add(id);
                    var parts = SnowflakeDecoder.decode(id);
                    assertEquals(1, parts.workerId());
                    assertEquals(EPOCH_MILLIS + 10, parts.timestamp().toEpochMilli());
                    sequences.add(parts.sequence());
                }
            }
            assertEquals(2048, ids.size());
            assertEquals(2048, sequences.size());
            for (int i = 0; i < 2048; i++) {
                assertTrue(sequences.contains(i));
            }
        } finally {
            futures.forEach(f -> f.cancel(true));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void error(SnowflakeGenerator g, SnowflakeErrorCode code) {
        assertEquals(code, assertThrows(SnowflakeException.class, g::nextId).code());
    }
}
