package com.example.snowflake.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.snowflake.core.SnowflakeFormat;
import com.example.snowflake.core.SnowflakeGenerator;
import com.example.snowflake.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IdControllerTest {
    private MockMvc mvc(java.time.Clock clock) {
        return MockMvcBuilders.standaloneSetup(new IdController(new SnowflakeGenerator(1, clock)))
                .setControllerAdvice(new IdExceptionHandler())
                .build();
    }

    @Test
    void returnsStringIdAndDecodedFields() throws Exception {
        var mvc = mvc(new MutableClock(SnowflakeFormat.EPOCH_MILLIS + 1));
        mvc.perform(post("/api/ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("4198400"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T00:00:00.001Z"))
                .andExpect(jsonPath("$.workerId").value(1))
                .andExpect(jsonPath("$.sequence").value(0));
        mvc.perform(post("/api/ids")).andExpect(jsonPath("$.id").value("4198401"));
    }

    @Test
    void mapsThreeRealDomainErrors() throws Exception {
        var clock = new MutableClock(SnowflakeFormat.EPOCH_MILLIS + 10);
        var mvc = mvc(clock);
        mvc.perform(post("/api/ids")).andExpect(status().isOk());
        clock.set(SnowflakeFormat.EPOCH_MILLIS + 9);
        mvc.perform(post("/api/ids"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CLOCK_ROLLBACK"));
        clock.set(SnowflakeFormat.EPOCH_MILLIS - 1);
        mvc.perform(post("/api/ids"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TIME_OUT_OF_RANGE"));
        clock.set(SnowflakeFormat.EPOCH_MILLIS + 10);
        for (int i = 1; i < 4096; i++) {
            mvc.perform(post("/api/ids")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/ids"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEQUENCE_EXHAUSTED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void doesNotHideUnexpectedException() {
        var clock = org.mockito.Mockito.mock(java.time.Clock.class);
        org.mockito.Mockito.when(clock.millis()).thenThrow(new IllegalStateException("unexpected"));
        var failure =
                assertThrows(
                        jakarta.servlet.ServletException.class,
                        () -> mvc(clock).perform(post("/api/ids")));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }
}
