package com.example.snowflake.api;

import java.time.Instant;

public record IdResponse(String id, Instant timestamp, int workerId, int sequence) {}
