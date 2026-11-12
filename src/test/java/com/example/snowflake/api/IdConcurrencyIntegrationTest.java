package com.example.snowflake.api;

import static org.junit.jupiter.api.Assertions.*;

import com.example.snowflake.core.SnowflakeFormat;
import com.example.snowflake.support.ConcurrentCalls;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "snowflake.worker-id=1")
@DirtiesContext
class IdConcurrencyIntegrationTest {
    @LocalServerPort int port;

    private long issue(HttpClient client) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ids"))
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        Object raw = JsonPath.read(response.body(), "$.id");
        assertInstanceOf(String.class, raw);
        long id = Long.parseLong((String) raw);
        assertTrue(id >= 0);
        assertEquals(1, (int) JsonPath.<Integer>read(response.body(), "$.workerId"));
        assertEquals(1, (id >>> 12) & 1023);
        assertEquals(
                (int) (id & 4095), (int) JsonPath.<Integer>read(response.body(), "$.sequence"));
        assertEquals(
                Instant.ofEpochMilli(SnowflakeFormat.EPOCH_MILLIS + (id >>> 22)).toString(),
                JsonPath.<String>read(response.body(), "$.timestamp"));
        return id;
    }

    @Test
    void returns2048UniqueIdsThroughRealConcurrentHttp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            long first = issue(client);
            long second = issue(client);
            assertTrue(second > first);
            var jobs = new ArrayList<Callable<List<Long>>>();
            for (int worker = 0; worker < 8; worker++) {
                jobs.add(
                        () -> {
                            var ids = new ArrayList<Long>();
                            for (int i = 0; i < 256; i++) {
                                ids.add(issue(client));
                            }
                            return ids;
                        });
            }
            var batches = ConcurrentCalls.collect(jobs, Duration.ofSeconds(60));
            var ids = new HashSet<Long>();
            int count = 0;
            for (var batch : batches) {
                count += batch.size();
                ids.addAll(batch);
            }
            assertEquals(2048, count);
            assertEquals(2048, ids.size());
            assertFalse(ids.contains(first));
            assertFalse(ids.contains(second));
        }
    }
}
