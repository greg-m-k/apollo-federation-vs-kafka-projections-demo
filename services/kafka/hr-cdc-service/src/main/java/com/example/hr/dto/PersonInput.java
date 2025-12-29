package com.example.hr.dto;

import java.time.LocalDate;

public record PersonInput(
    String name,
    String email,
    LocalDate hireDate
) {}
