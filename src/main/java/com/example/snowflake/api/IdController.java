package com.example.snowflake.api;

import com.example.snowflake.core.SnowflakeDecoder;
import com.example.snowflake.core.SnowflakeGenerator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdController {
    private final SnowflakeGenerator generator;

    public IdController(SnowflakeGenerator generator) {
        this.generator = generator;
    }

    @PostMapping("/api/ids")
    public IdResponse issue() {
        long id = generator.nextId();
        var parts = SnowflakeDecoder.decode(id);
        return new IdResponse(
                Long.toString(id), parts.timestamp(), parts.workerId(), parts.sequence());
    }
}
