package com.example.hr.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Event payload for person events published to Kafka.
 */
public record PersonEvent(
    String eventType,
    String personId,
    PersonData data,
    Instant timestamp
) {
    public record PersonData(
        String id,
        String name,
        String email,
        LocalDate hireDate,
        boolean active
    ) {}
}
