package com.example.snowflake.core;

public final class SnowflakeException extends RuntimeException {
    private final SnowflakeErrorCode code;

    public SnowflakeException(SnowflakeErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public SnowflakeErrorCode code() {
        return code;
    }
}
