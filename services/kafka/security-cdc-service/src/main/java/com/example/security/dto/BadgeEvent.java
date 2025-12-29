package com.example.security.dto;

import java.time.Instant;

public record BadgeEvent(
    String eventType,
    String badgeId,
    BadgeData data,
    Instant timestamp
) {
    public record BadgeData(
        String id,
        String personId,
        String badgeNumber,
        String accessLevel,
        String clearance,
        boolean active
    ) {}
}
