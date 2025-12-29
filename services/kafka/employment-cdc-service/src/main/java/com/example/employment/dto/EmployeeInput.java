package com.example.employment.dto;

import java.math.BigDecimal;

public record EmployeeInput(
    String personId,
    String title,
    String department,
    BigDecimal salary
) {}
