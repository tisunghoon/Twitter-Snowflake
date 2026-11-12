package com.example.snowflake.config;

import com.example.snowflake.core.SnowflakeGenerator;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SnowflakeGenerator snowflakeGenerator(SnowflakeProperties properties, Clock clock) {
        return new SnowflakeGenerator(properties.workerId(), clock);
    }
}
