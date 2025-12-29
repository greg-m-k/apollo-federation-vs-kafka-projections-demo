package com.example.security.service;

import com.example.security.dto.BadgeEvent;
import com.example.security.dto.BadgeInput;
import com.example.security.model.BadgeHolder;
import com.example.security.model.BadgeHolder.AccessLevel;
import com.example.security.model.BadgeHolder.Clearance;
import com.example.security.model.OutboxEvent;
import com.example.security.repository.BadgeHolderRepository;
import com.example.security.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BadgeService {

    @Inject
    BadgeHolderRepository badgeHolderRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    ObjectMapper objectMapper;

    public List<BadgeHolder> getAllBadgeHolders() {
        return badgeHolderRepository.listAll();
    }

    public BadgeHolder getBadgeHolder(String id) {
        return badgeHolderRepository.findById(id);
    }

    public BadgeHolder getBadgeHolderByPersonId(String personId) {
        return badgeHolderRepository.findByPersonId(personId).orElse(null);
    }

    @Transactional
    public BadgeHolder provisionBadge(BadgeInput input) {
        // Check if already provisioned
        if (badgeHolderRepository.findByPersonId(input.personId()).isPresent()) {
            return null;
        }

        String id = "badge-" + UUID.randomUUID().toString().substring(0, 8);
        String badgeNumber = "B" + System.currentTimeMillis() % 100000;

        BadgeHolder badge = new BadgeHolder(id, input.personId(), badgeNumber,
            input.accessLevel(), input.clearance());
        badgeHolderRepository.persist(badge);

        createOutboxEvent("BadgeProvisioned", badge);
        return badge;
    }

    @Transactional
    public BadgeHolder changeAccessLevel(String id, AccessLevel newAccessLevel) {
        BadgeHolder badge = badgeHolderRepository.findById(id);
        if (badge == null) {
            return null;
        }

        badge.accessLevel = newAccessLevel;
        createOutboxEvent("AccessLevelChanged", badge);
        return badge;
    }

    @Transactional
    public BadgeHolder changeClearance(String id, Clearance newClearance) {
        BadgeHolder badge = badgeHolderRepository.findById(id);
        if (badge == null) {
            return null;
        }

        badge.clearance = newClearance;
        createOutboxEvent("ClearanceChanged", badge);
        return badge;
    }

    @Transactional
    public BadgeHolder revokeBadge(String id) {
        BadgeHolder badge = badgeHolderRepository.findById(id);
        if (badge == null) {
            return null;
        }

        badge.active = false;
        createOutboxEvent("BadgeRevoked", badge);
        return badge;
    }

    private void createOutboxEvent(String eventType, BadgeHolder badge) {
        try {
            BadgeEvent.BadgeData data = new BadgeEvent.BadgeData(
                badge.id, badge.personId, badge.badgeNumber,
                badge.accessLevel.name(), badge.clearance.name(), badge.active
            );
            BadgeEvent event = new BadgeEvent(eventType, badge.id, data, Instant.now());
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent("security.badge", badge.id, eventType, payload);
            outboxRepository.persist(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize badge event", e);
        }
    }
}
