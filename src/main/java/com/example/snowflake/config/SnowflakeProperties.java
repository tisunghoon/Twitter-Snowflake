package com.example.snowflake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("snowflake")
public record SnowflakeProperties(Integer workerId) {
    public SnowflakeProperties {
        if (workerId == null || workerId < 0 || workerId > 1023) {
            throw new IllegalArgumentException("snowflake.worker-id는 필수이며 0~1023이어야 합니다");
        }
    }
}
