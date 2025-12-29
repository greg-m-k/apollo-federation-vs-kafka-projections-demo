package com.example.query.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Composed view combining data from all three bounded contexts.
 * In Kafka Projections architecture, this is served from a single local query.
 */
public record ComposedView(
    // Person data (from HR)
    String personId,
    String name,
    String email,
    LocalDate hireDate,
    boolean personActive,

    // Employee data (from Employment)
    String employeeId,
    String title,
    String department,
    BigDecimal salary,
    boolean employeeActive,

    // Badge data (from Security)
    String badgeId,
    String badgeNumber,
    String accessLevel,
    String clearance,
    boolean badgeActive,

    // Freshness metadata
    FreshnessInfo freshness
) {
    public record FreshnessInfo(
        Instant personLastUpdated,
        Instant employeeLastUpdated,
        Instant badgeLastUpdated,
        long maxLagMs,
        String dataFreshness
    ) {}
}
