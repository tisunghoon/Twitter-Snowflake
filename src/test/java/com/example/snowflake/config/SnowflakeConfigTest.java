package com.example.snowflake.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.snowflake.core.SnowflakeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SnowflakeConfigTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SnowflakeConfig.class);

    @Test
    void rejectsMissingOrInvalidWorker() {
        runner.run(context -> assertThat(context).hasFailed());
        for (String value : new String[] {"-1", "1024", "abc"}) {
            runner.withPropertyValues("snowflake.worker-id=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void createsOneSharedGeneratorForValidBoundaries() {
        for (int value : new int[] {0, 1023}) {
            runner.withPropertyValues("snowflake.worker-id=" + value)
                    .run(
                            context -> {
                                assertThat(context)
                                        .hasNotFailed()
                                        .hasSingleBean(SnowflakeGenerator.class);
                                assertThat(context.getBean(SnowflakeGenerator.class))
                                        .isSameAs(context.getBean(SnowflakeGenerator.class));
                            });
        }
    }
}
