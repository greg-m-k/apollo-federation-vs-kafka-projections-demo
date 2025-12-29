package com.example.security;

import com.example.security.dto.BadgeInput;
import com.example.security.model.BadgeHolder;
import com.example.security.model.BadgeHolder.AccessLevel;
import com.example.security.model.BadgeHolder.Clearance;
import com.example.security.model.OutboxEvent;
import com.example.security.repository.BadgeHolderRepository;
import com.example.security.repository.OutboxRepository;
import com.example.security.service.BadgeService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BadgeService using H2 in-memory database.
 * Tests verify the Outbox pattern - each change creates an outbox event.
 * These tests run without Docker.
 */
@QuarkusTest
class BadgeServiceTest {

    @Inject
    BadgeService badgeService;

    @Inject
    BadgeHolderRepository badgeHolderRepository;

    @Inject
    OutboxRepository outboxRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        outboxRepository.deleteAll();
        badgeHolderRepository.deleteAll();

        BadgeHolder badge1 = new BadgeHolder("badge-001", "person-001", "B12345",
                AccessLevel.STANDARD, Clearance.CONFIDENTIAL);
        BadgeHolder badge2 = new BadgeHolder("badge-002", "person-002", "B12346",
                AccessLevel.RESTRICTED, Clearance.SECRET);

        badgeHolderRepository.persist(badge1);
        badgeHolderRepository.persist(badge2);
    }

    @Test
    void testGetAllBadgeHolders() {
        List<BadgeHolder> badges = badgeService.getAllBadgeHolders();

        assertThat(badges).hasSize(2);
        assertThat(badges).extracting(b -> b.badgeNumber)
                .containsExactlyInAnyOrder("B12345", "B12346");
    }

    @Test
    void testGetBadgeHolder() {
        BadgeHolder badge = badgeService.getBadgeHolder("badge-001");

        assertThat(badge).isNotNull();
        assertThat(badge.personId).isEqualTo("person-001");
        assertThat(badge.badgeNumber).isEqualTo("B12345");
        assertThat(badge.accessLevel).isEqualTo(AccessLevel.STANDARD);
        assertThat(badge.clearance).isEqualTo(Clearance.CONFIDENTIAL);
    }

    @Test
    void testGetBadgeHolder_NotFound() {
        BadgeHolder badge = badgeService.getBadgeHolder("non-existent");

        assertThat(badge).isNull();
    }

    @Test
    void testGetBadgeHolderByPersonId() {
        BadgeHolder badge = badgeService.getBadgeHolderByPersonId("person-001");

        assertThat(badge).isNotNull();
        assertThat(badge.id).isEqualTo("badge-001");
        assertThat(badge.badgeNumber).isEqualTo("B12345");
    }

    @Test
    void testGetBadgeHolderByPersonId_NotFound() {
        BadgeHolder badge = badgeService.getBadgeHolderByPersonId("non-existent");

        assertThat(badge).isNull();
    }

    @Test
    void testProvisionBadge_CreatesAndOutboxEvent() {
        BadgeInput input = new BadgeInput("person-003", AccessLevel.ALL_ACCESS, Clearance.TOP_SECRET);

        BadgeHolder badge = badgeService.provisionBadge(input);

        // Verify badge was created
        assertThat(badge).isNotNull();
        assertThat(badge.id).startsWith("badge-");
        assertThat(badge.personId).isEqualTo("person-003");
        assertThat(badge.accessLevel).isEqualTo(AccessLevel.ALL_ACCESS);
        assertThat(badge.clearance).isEqualTo(Clearance.TOP_SECRET);
        assertThat(badge.active).isTrue();

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("BadgeProvisioned");
        assertThat(events.get(0).aggregateId).isEqualTo(badge.id);
        assertThat(events.get(0).aggregateType).isEqualTo("security.badge");
        assertThat(events.get(0).payload).contains("person-003");
    }

    @Test
    void testProvisionBadge_AlreadyProvisioned() {
        BadgeInput input = new BadgeInput("person-001", AccessLevel.STANDARD, Clearance.NONE);

        BadgeHolder badge = badgeService.provisionBadge(input);

        assertThat(badge).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testChangeAccessLevel_UpdatesAndCreatesOutboxEvent() {
        BadgeHolder badge = badgeService.changeAccessLevel("badge-001", AccessLevel.ALL_ACCESS);

        // Verify access level was updated
        assertThat(badge).isNotNull();
        assertThat(badge.accessLevel).isEqualTo(AccessLevel.ALL_ACCESS);

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("AccessLevelChanged");
        assertThat(events.get(0).aggregateId).isEqualTo("badge-001");
    }

    @Test
    void testChangeAccessLevel_NotFound() {
        BadgeHolder badge = badgeService.changeAccessLevel("non-existent", AccessLevel.ALL_ACCESS);

        assertThat(badge).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testChangeClearance_UpdatesAndCreatesOutboxEvent() {
        BadgeHolder badge = badgeService.changeClearance("badge-001", Clearance.TOP_SECRET);

        // Verify clearance was updated
        assertThat(badge).isNotNull();
        assertThat(badge.clearance).isEqualTo(Clearance.TOP_SECRET);

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("ClearanceChanged");
        assertThat(events.get(0).aggregateId).isEqualTo("badge-001");
    }

    @Test
    void testChangeClearance_NotFound() {
        BadgeHolder badge = badgeService.changeClearance("non-existent", Clearance.TOP_SECRET);

        assertThat(badge).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }

    @Test
    void testRevokeBadge_RevokesAndCreatesOutboxEvent() {
        BadgeHolder badge = badgeService.revokeBadge("badge-001");

        // Verify badge was revoked
        assertThat(badge).isNotNull();
        assertThat(badge.active).isFalse();

        // Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findUnpublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo("BadgeRevoked");
        assertThat(events.get(0).aggregateId).isEqualTo("badge-001");
    }

    @Test
    void testRevokeBadge_NotFound() {
        BadgeHolder badge = badgeService.revokeBadge("non-existent");

        assertThat(badge).isNull();
        assertThat(outboxRepository.findUnpublished()).isEmpty();
    }
}
