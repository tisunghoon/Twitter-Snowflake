package com.example.snowflake.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ConcurrentCalls {
    private ConcurrentCalls() {}

    public static <T> List<T> collect(List<Callable<T>> jobs, Duration timeout) throws Exception {
        if (jobs.isEmpty() || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("작업과 양수 제한 시간이 필요합니다");
        }
        var executor = Executors.newFixedThreadPool(Math.min(8, jobs.size()));
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(Math.min(8, jobs.size()));
        var futures = new ArrayList<Future<T>>();
        var results = new ArrayList<T>();
        Exception failure = null;
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            for (var job : jobs) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(10, TimeUnit.SECONDS)) {
                                        throw new TimeoutException("시작 제한 시간");
                                    }
                                    return job.call();
                                }));
            }
            if (!ready.await(
                    Math.max(
                            0,
                            Math.min(TimeUnit.SECONDS.toNanos(10), deadline - System.nanoTime())),
                    TimeUnit.NANOSECONDS)) {
                throw new TimeoutException("작업자 준비 제한 시간");
            }
            start.countDown();
            for (var future : futures) {
                try {
                    results.add(
                            future.get(
                                    Math.max(0, deadline - System.nanoTime()),
                                    TimeUnit.NANOSECONDS));
                } catch (ExecutionException | TimeoutException | CancellationException problem) {
                    if (failure == null) {
                        failure = problem;
                    } else {
                        failure.addSuppressed(problem);
                    }
                    if (problem instanceof TimeoutException) {
                        futures.forEach(f -> f.cancel(true));
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
            return results;
        } finally {
            futures.forEach(f -> f.cancel(true));
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 작업자 종료 실패");
            }
        }
    }
}
