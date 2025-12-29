package com.example.security.dto;

import com.example.security.model.BadgeHolder.AccessLevel;
import com.example.security.model.BadgeHolder.Clearance;

public record BadgeInput(
    String personId,
    AccessLevel accessLevel,
    Clearance clearance
) {}
