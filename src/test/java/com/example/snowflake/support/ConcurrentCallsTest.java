package com.example.snowflake.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConcurrentCallsTest {
    @Test
    void collectsEveryResult() throws Exception {
        assertEquals(
                List.of(1, 2, 3),
                ConcurrentCalls.collect(List.of(() -> 1, () -> 2, () -> 3), Duration.ofSeconds(2)));
    }

    @Test
    void exposesWorkerAndHttpFailures() {
        for (Exception failure :
                List.of(new IllegalStateException("worker"), new java.io.IOException("HTTP 503"))) {
            var thrown =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    ConcurrentCalls.collect(
                                            List.<Callable<Integer>>of(
                                                    () -> {
                                                        throw failure;
                                                    },
                                                    () -> 1),
                                            Duration.ofSeconds(2)));
            assertSame(failure, thrown.getCause());
        }
    }

    @Test
    void timesOutAndInterruptsRemainingWorker() {
        var started = new CountDownLatch(1);
        var exited = new AtomicBoolean();
        assertThrows(
                TimeoutException.class,
                () ->
                        ConcurrentCalls.collect(
                                List.<Callable<Integer>>of(
                                        () -> {
                                            started.countDown();
                                            try {
                                                new CountDownLatch(1).await();
                                                return 1;
                                            } finally {
                                                exited.set(true);
                                            }
                                        }),
                                Duration.ofMillis(200)));
        assertEquals(0, started.getCount());
        assertTrue(exited.get());
    }
}
