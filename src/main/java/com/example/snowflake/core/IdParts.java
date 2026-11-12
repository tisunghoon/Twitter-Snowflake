package com.example.snowflake.core;

import java.time.Instant;

public record IdParts(Instant timestamp, int workerId, int sequence) {}
