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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for QueryResource REST API using H2 in-memory database.
 * These tests run without Docker.
 */
@QuarkusTest
class QueryResourceTest {

    @Inject
    PersonProjectionRepository personRepository;

    @Inject
    EmployeeProjectionRepository employeeRepository;

    @Inject
    BadgeProjectionRepository badgeRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing data
        badgeRepository.deleteAll();
        employeeRepository.deleteAll();
        personRepository.deleteAll();

        // Create test person projections
        PersonProjection person1 = new PersonProjection();
        person1.id = "person-001";
        person1.name = "John Doe";
        person1.email = "john.doe@example.com";
        person1.hireDate = LocalDate.of(2020, 1, 15);
        person1.active = true;
        person1.lastUpdated = Instant.now().minusSeconds(60);
        person1.eventVersion = 1L;

        PersonProjection person2 = new PersonProjection();
        person2.id = "person-002";
        person2.name = "Jane Smith";
        person2.email = "jane.smith@example.com";
        person2.hireDate = LocalDate.of(2021, 3, 20);
        person2.active = true;
        person2.lastUpdated = Instant.now().minusSeconds(30);
        person2.eventVersion = 1L;

        personRepository.persist(person1);
        personRepository.persist(person2);

        // Create test employee projections
        EmployeeProjection employee1 = new EmployeeProjection();
        employee1.id = "emp-001";
        employee1.personId = "person-001";
        employee1.title = "Software Engineer";
        employee1.department = "Engineering";
        employee1.salary = new BigDecimal("85000.00");
        employee1.active = true;
        employee1.lastUpdated = Instant.now().minusSeconds(45);
        employee1.eventVersion = 1L;

        employeeRepository.persist(employee1);

        // Create test badge projections
        BadgeProjection badge1 = new BadgeProjection();
        badge1.id = "badge-001";
        badge1.personId = "person-001";
        badge1.badgeNumber = "B12345";
        badge1.accessLevel = "LEVEL_3";
        badge1.clearance = "SECRET";
        badge1.active = true;
        badge1.lastUpdated = Instant.now().minusSeconds(20);
        badge1.eventVersion = 1L;

        badgeRepository.persist(badge1);
    }

    // ==================== Composed View Tests ====================

    @Test
    void testGetComposedView_FullData() {
        given()
        .when()
            .get("/api/composed/person-001")
        .then()
            .statusCode(200)
            // Person data
            .body("personId", is("person-001"))
            .body("name", is("John Doe"))
            .body("email", is("john.doe@example.com"))
            .body("personActive", is(true))
            // Employee data
            .body("employeeId", is("emp-001"))
            .body("title", is("Software Engineer"))
            .body("department", is("Engineering"))
            .body("employeeActive", is(true))
            // Badge data
            .body("badgeId", is("badge-001"))
            .body("badgeNumber", is("B12345"))
            .body("accessLevel", is("LEVEL_3"))
            .body("clearance", is("SECRET"))
            .body("badgeActive", is(true))
            // Freshness data
            .body("freshness", notNullValue())
            .body("freshness.maxLagMs", greaterThanOrEqualTo(0))
            // Headers
            .header("X-Query-Time-Ms", notNullValue())
            .header("X-Services-Called", is("1"))
            .header("X-Data-Freshness", notNullValue());
    }

    @Test
    void testGetComposedView_PersonOnly() {
        // person-002 has no employee or badge data
        given()
        .when()
            .get("/api/composed/person-002")
        .then()
            .statusCode(200)
            // Person data exists
            .body("personId", is("person-002"))
            .body("name", is("Jane Smith"))
            .body("email", is("jane.smith@example.com"))
            .body("personActive", is(true))
            // Employee data is null
            .body("employeeId", nullValue())
            .body("title", nullValue())
            .body("department", nullValue())
            .body("employeeActive", is(false))
            // Badge data is null
            .body("badgeId", nullValue())
            .body("badgeNumber", nullValue())
            .body("accessLevel", nullValue())
            .body("badgeActive", is(false));
    }

    @Test
    void testGetComposedView_NotFound() {
        given()
        .when()
            .get("/api/composed/non-existent")
        .then()
            .statusCode(404)
            .body("error", is("Person not found"))
            .body("personId", is("non-existent"));
    }

    // ==================== Person Endpoint Tests ====================

    @Test
    void testGetAllPersons() {
        given()
        .when()
            .get("/api/persons")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("name", hasItems("John Doe", "Jane Smith"));
    }

    @Test
    void testGetPersonById() {
        given()
        .when()
            .get("/api/persons/person-001")
        .then()
            .statusCode(200)
            .body("id", is("person-001"))
            .body("name", is("John Doe"))
            .body("email", is("john.doe@example.com"))
            .body("active", is(true));
    }

    @Test
    void testGetPersonById_NotFound() {
        given()
        .when()
            .get("/api/persons/non-existent")
        .then()
            .statusCode(404);
    }

    // ==================== Employee Endpoint Tests ====================

    @Test
    void testGetAllEmployees() {
        given()
        .when()
            .get("/api/employees")
        .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", is("emp-001"))
            .body("[0].title", is("Software Engineer"))
            .body("[0].department", is("Engineering"));
    }

    // ==================== Badge Endpoint Tests ====================

    @Test
    void testGetAllBadges() {
        given()
        .when()
            .get("/api/badges")
        .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", is("badge-001"))
            .body("[0].badgeNumber", is("B12345"))
            .body("[0].accessLevel", is("LEVEL_3"));
    }

    // ==================== Freshness Endpoint Tests ====================

    @Test
    void testGetFreshness() {
        given()
        .when()
            .get("/api/freshness")
        .then()
            .statusCode(200)
            .body("person", notNullValue())
            .body("employee", notNullValue())
            .body("badge", notNullValue())
            .body("timestamp", notNullValue())
            .body("person.lastUpdate", notNullValue())
            .body("person.lagMs", greaterThanOrEqualTo(0))
            .body("person.lagHuman", notNullValue());
    }

    // ==================== Health Check Tests ====================

    @Test
    void testHealthEndpoint() {
        given()
        .when()
            .get("/q/health/ready")
        .then()
            .statusCode(200)
            .body("status", is("UP"));
    }

    @Test
    void testLivenessEndpoint() {
        given()
        .when()
            .get("/q/health/live")
        .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}
