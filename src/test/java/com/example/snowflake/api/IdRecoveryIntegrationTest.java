package com.example.snowflake.api;

import static org.junit.jupiter.api.Assertions.*;

import com.example.snowflake.SnowflakeApplication;
import com.example.snowflake.core.SnowflakeFormat;
import com.example.snowflake.support.MutableClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
        classes = {SnowflakeApplication.class, IdRecoveryIntegrationTest.TimeConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "snowflake.worker-id=1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IdRecoveryIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(SnowflakeFormat.EPOCH_MILLIS + 10);
        }
    }

    @LocalServerPort int port;
    @Autowired MutableClock clock;

    private HttpResponse<String> issue(HttpClient client) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ids"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void success(HttpClient client, long id, int sequence) throws Exception {
        var result = issue(client);
        assertEquals(200, result.statusCode());
        new org.springframework.test.util.JsonPathExpectationsHelper("$.id")
                .assertValue(result.body(), Long.toString(id));
        new org.springframework.test.util.JsonPathExpectationsHelper("$.workerId")
                .assertValue(result.body(), 1);
        new org.springframework.test.util.JsonPathExpectationsHelper("$.sequence")
                .assertValue(result.body(), sequence);
        new org.springframework.test.util.JsonPathExpectationsHelper("$.timestamp")
                .assertValue(
                        result.body(), java.time.Instant.ofEpochMilli(clock.millis()).toString());
    }

    private void failure(HttpClient client, String code) throws Exception {
        var result = issue(client);
        assertEquals(503, result.statusCode());
        new org.springframework.test.util.JsonPathExpectationsHelper("$.code")
                .assertValue(result.body(), code);
        new org.springframework.test.util.JsonPathExpectationsHelper("$.message")
                .assertValueIsString(result.body());
    }

    @Test
    void preservesRollbackAndRangeStateThroughHttp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            success(client, 41947136, 0);
            clock.set(SnowflakeFormat.EPOCH_MILLIS + 9);
            failure(client, "CLOCK_ROLLBACK");
            for (long invalid :
                    new long[] {SnowflakeFormat.EPOCH_MILLIS - 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                clock.set(invalid);
                failure(client, "TIME_OUT_OF_RANGE");
            }
            clock.set(SnowflakeFormat.EPOCH_MILLIS + 10);
            success(client, 41947137, 1);
            clock.set(SnowflakeFormat.EPOCH_MILLIS + 11);
            success(client, 46141440, 0);
        }
    }

    @Test
    void preservesExhaustionAndRecoversOnlyAtNextMillis() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (int i = 0; i < 4096; i++) {
                success(client, 41947136 + i, i);
            }
            failure(client, "SEQUENCE_EXHAUSTED");
            failure(client, "SEQUENCE_EXHAUSTED");
            clock.set(SnowflakeFormat.EPOCH_MILLIS + 9);
            failure(client, "CLOCK_ROLLBACK");
            clock.set(SnowflakeFormat.EPOCH_MILLIS + 10);
            failure(client, "SEQUENCE_EXHAUSTED");
            clock.set(SnowflakeFormat.EPOCH_MILLIS + 11);
            success(client, 46141440, 0);
            success(client, 46141441, 1);
        }
    }

    @Test
    void supportsFirstFailureAndLastValidMillisThroughHttp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            clock.set(SnowflakeFormat.EPOCH_MILLIS - 1);
            failure(client, "TIME_OUT_OF_RANGE");
            clock.set(SnowflakeFormat.EPOCH_MILLIS);
            success(client, 4096, 0);
            clock.set(SnowflakeFormat.EPOCH_MILLIS + SnowflakeFormat.MAX_ELAPSED_MILLIS);
            long last = Long.MAX_VALUE - 4194303 + 4096;
            success(client, last, 0);
            clock.set(SnowflakeFormat.EPOCH_MILLIS + SnowflakeFormat.MAX_ELAPSED_MILLIS + 1);
            failure(client, "TIME_OUT_OF_RANGE");
            clock.set(SnowflakeFormat.EPOCH_MILLIS + SnowflakeFormat.MAX_ELAPSED_MILLIS);
            success(client, last + 1, 1);
        }
    }
}
