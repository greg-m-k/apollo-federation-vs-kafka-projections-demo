package com.example.query;

import com.example.query.model.BadgeProjection;
import com.example.query.model.EmployeeProjection;
import com.example.query.model.PersonProjection;
import com.example.query.repository.BadgeProjectionRepository;
import com.example.query.repository.EmployeeProjectionRepository;
import com.example.query.repository.PersonProjectionRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for projection repositories using H2 in-memory database.
 * These tests run without Docker.
 */
@QuarkusTest
class RepositoryTest {

    @Inject
    PersonProjectionRepository personRepository;

    @Inject
    EmployeeProjectionRepository employeeRepository;

    @Inject
    BadgeProjectionRepository badgeRepository;

    private Instant baseTime;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing data
        badgeRepository.deleteAll();
        employeeRepository.deleteAll();
        personRepository.deleteAll();

        // Truncate to microseconds for consistent comparison with H2 database
        baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Create test persons with different update times
        PersonProjection person1 = new PersonProjection();
        person1.id = "person-001";
        person1.name = "John Doe";
        person1.email = "john.doe@example.com";
        person1.hireDate = LocalDate.of(2020, 1, 15);
        person1.active = true;
        person1.lastUpdated = baseTime.minusSeconds(60);
        person1.eventVersion = 1L;

        PersonProjection person2 = new PersonProjection();
        person2.id = "person-002";
        person2.name = "Jane Smith";
        person2.email = "jane.smith@example.com";
        person2.hireDate = LocalDate.of(2021, 3, 20);
        person2.active = true;
        person2.lastUpdated = baseTime.minusSeconds(10); // More recent
        person2.eventVersion = 2L;

        personRepository.persist(person1);
        personRepository.persist(person2);

        // Create employees
        EmployeeProjection employee1 = new EmployeeProjection();
        employee1.id = "emp-001";
        employee1.personId = "person-001";
        employee1.title = "Software Engineer";
        employee1.department = "Engineering";
        employee1.salary = new BigDecimal("85000.00");
        employee1.active = true;
        employee1.lastUpdated = baseTime.minusSeconds(45);
        employee1.eventVersion = 1L;

        EmployeeProjection employee2 = new EmployeeProjection();
        employee2.id = "emp-002";
        employee2.personId = "person-002";
        employee2.title = "Product Manager";
        employee2.department = "Product";
        employee2.salary = new BigDecimal("95000.00");
        employee2.active = true;
        employee2.lastUpdated = baseTime.minusSeconds(5); // Most recent
        employee2.eventVersion = 1L;

        employeeRepository.persist(employee1);
        employeeRepository.persist(employee2);

        // Create badges
        BadgeProjection badge1 = new BadgeProjection();
        badge1.id = "badge-001";
        badge1.personId = "person-001";
        badge1.badgeNumber = "B12345";
        badge1.accessLevel = "LEVEL_3";
        badge1.clearance = "SECRET";
        badge1.active = true;
        badge1.lastUpdated = baseTime.minusSeconds(30);
        badge1.eventVersion = 1L;

        badgeRepository.persist(badge1);
    }

    // ==================== PersonProjectionRepository Tests ====================

    @Test
    void testPersonRepository_FindById() {
        PersonProjection person = personRepository.findById("person-001");

        assertThat(person).isNotNull();
        assertThat(person.id).isEqualTo("person-001");
        assertThat(person.name).isEqualTo("John Doe");
        assertThat(person.email).isEqualTo("john.doe@example.com");
        assertThat(person.active).isTrue();
    }

    @Test
    void testPersonRepository_FindById_NotFound() {
        PersonProjection person = personRepository.findById("non-existent");

        assertThat(person).isNull();
    }

    @Test
    void testPersonRepository_ListAll() {
        var persons = personRepository.listAll();

        assertThat(persons).hasSize(2);
        assertThat(persons).extracting(p -> p.name)
            .containsExactlyInAnyOrder("John Doe", "Jane Smith");
    }

    @Test
    void testPersonRepository_GetLastUpdateTime() {
        Optional<Instant> lastUpdate = personRepository.getLastUpdateTime();

        assertThat(lastUpdate).isPresent();
        // Should return person-002's update time (most recent)
        assertThat(lastUpdate.get()).isEqualTo(baseTime.minusSeconds(10));
    }

    @Test
    @Transactional
    void testPersonRepository_GetLastUpdateTime_Empty() {
        personRepository.deleteAll();

        Optional<Instant> lastUpdate = personRepository.getLastUpdateTime();

        assertThat(lastUpdate).isEmpty();
    }

    // ==================== EmployeeProjectionRepository Tests ====================

    @Test
    void testEmployeeRepository_FindByPersonId() {
        Optional<EmployeeProjection> employee = employeeRepository.findByPersonId("person-001");

        assertThat(employee).isPresent();
        assertThat(employee.get().id).isEqualTo("emp-001");
        assertThat(employee.get().title).isEqualTo("Software Engineer");
        assertThat(employee.get().department).isEqualTo("Engineering");
        assertThat(employee.get().salary).isEqualByComparingTo(new BigDecimal("85000.00"));
    }

    @Test
    void testEmployeeRepository_FindByPersonId_NotFound() {
        Optional<EmployeeProjection> employee = employeeRepository.findByPersonId("non-existent");

        assertThat(employee).isEmpty();
    }

    @Test
    void testEmployeeRepository_ListAll() {
        var employees = employeeRepository.listAll();

        assertThat(employees).hasSize(2);
        assertThat(employees).extracting(e -> e.title)
            .containsExactlyInAnyOrder("Software Engineer", "Product Manager");
    }

    @Test
    void testEmployeeRepository_GetLastUpdateTime() {
        Optional<Instant> lastUpdate = employeeRepository.getLastUpdateTime();

        assertThat(lastUpdate).isPresent();
        // Should return emp-002's update time (most recent)
        assertThat(lastUpdate.get()).isEqualTo(baseTime.minusSeconds(5));
    }

    // ==================== BadgeProjectionRepository Tests ====================

    @Test
    void testBadgeRepository_FindByPersonId() {
        Optional<BadgeProjection> badge = badgeRepository.findByPersonId("person-001");

        assertThat(badge).isPresent();
        assertThat(badge.get().id).isEqualTo("badge-001");
        assertThat(badge.get().badgeNumber).isEqualTo("B12345");
        assertThat(badge.get().accessLevel).isEqualTo("LEVEL_3");
        assertThat(badge.get().clearance).isEqualTo("SECRET");
    }

    @Test
    void testBadgeRepository_FindByPersonId_NotFound() {
        // person-002 has no badge
        Optional<BadgeProjection> badge = badgeRepository.findByPersonId("person-002");

        assertThat(badge).isEmpty();
    }

    @Test
    void testBadgeRepository_ListAll() {
        var badges = badgeRepository.listAll();

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).badgeNumber).isEqualTo("B12345");
    }

    @Test
    void testBadgeRepository_GetLastUpdateTime() {
        Optional<Instant> lastUpdate = badgeRepository.getLastUpdateTime();

        assertThat(lastUpdate).isPresent();
        assertThat(lastUpdate.get()).isEqualTo(baseTime.minusSeconds(30));
    }

    @Test
    @Transactional
    void testBadgeRepository_GetLastUpdateTime_Empty() {
        badgeRepository.deleteAll();

        Optional<Instant> lastUpdate = badgeRepository.getLastUpdateTime();

        assertThat(lastUpdate).isEmpty();
    }
}
