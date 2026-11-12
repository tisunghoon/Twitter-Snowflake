package com.example.snowflake.api;

import com.example.snowflake.core.SnowflakeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdExceptionHandler {
    @ExceptionHandler(SnowflakeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handle(SnowflakeException exception) {
        return new ErrorResponse(exception.code().name(), exception.getMessage());
    }
}
