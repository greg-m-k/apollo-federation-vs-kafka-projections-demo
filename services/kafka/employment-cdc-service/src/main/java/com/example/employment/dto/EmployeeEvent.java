package com.example.employment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EmployeeEvent(
    String eventType,
    String employeeId,
    EmployeeData data,
    Instant timestamp
) {
    public record EmployeeData(
        String id,
        String personId,
        String title,
        String department,
        BigDecimal salary,
        boolean active
    ) {}
}
