package com.example.security;

import com.example.security.model.BadgeHolder;
import com.example.security.model.BadgeHolder.AccessLevel;
import com.example.security.model.BadgeHolder.Clearance;
import com.example.security.repository.BadgeHolderRepository;
import com.example.security.repository.OutboxRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for BadgeResource REST API using H2 in-memory database.
 * These tests run without Docker.
 */
@QuarkusTest
class BadgeResourceTest {

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
        given()
        .when()
            .get("/api/badges")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("badgeNumber", hasItems("B12345", "B12346"));
    }

    @Test
    void testGetBadgeHolderById() {
        given()
        .when()
            .get("/api/badges/badge-001")
        .then()
            .statusCode(200)
            .body("id", is("badge-001"))
            .body("personId", is("person-001"))
            .body("badgeNumber", is("B12345"))
            .body("accessLevel", is("STANDARD"))
            .body("clearance", is("CONFIDENTIAL"))
            .body("active", is(true));
    }

    @Test
    void testGetBadgeHolderById_NotFound() {
        given()
        .when()
            .get("/api/badges/non-existent")
        .then()
            .statusCode(404);
    }

    @Test
    void testGetBadgeHolderByPersonId() {
        given()
        .when()
            .get("/api/badges/by-person/person-001")
        .then()
            .statusCode(200)
            .body("id", is("badge-001"))
            .body("badgeNumber", is("B12345"));
    }

    @Test
    void testGetBadgeHolderByPersonId_NotFound() {
        given()
        .when()
            .get("/api/badges/by-person/non-existent")
        .then()
            .statusCode(404);
    }

    @Test
    void testProvisionBadge() {
        String badgeJson = """
            {
                "personId": "person-003",
                "accessLevel": "ALL_ACCESS",
                "clearance": "TOP_SECRET"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(badgeJson)
        .when()
            .post("/api/badges")
        .then()
            .statusCode(201)
            .body("personId", is("person-003"))
            .body("accessLevel", is("ALL_ACCESS"))
            .body("clearance", is("TOP_SECRET"))
            .body("active", is(true))
            .body("id", startsWith("badge-"));
    }

    @Test
    void testProvisionBadge_Conflict() {
        String badgeJson = """
            {
                "personId": "person-001",
                "accessLevel": "STANDARD",
                "clearance": "NONE"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(badgeJson)
        .when()
            .post("/api/badges")
        .then()
            .statusCode(409)
            .body(containsString("already has a badge"));
    }

    @Test
    void testChangeAccessLevel() {
        given()
            .queryParam("level", "ALL_ACCESS")
        .when()
            .put("/api/badges/badge-001/access-level")
        .then()
            .statusCode(200)
            .body("id", is("badge-001"))
            .body("accessLevel", is("ALL_ACCESS"));
    }

    @Test
    void testChangeAccessLevel_NotFound() {
        given()
            .queryParam("level", "ALL_ACCESS")
        .when()
            .put("/api/badges/non-existent/access-level")
        .then()
            .statusCode(404);
    }

    @Test
    void testChangeClearance() {
        given()
            .queryParam("level", "TOP_SECRET")
        .when()
            .put("/api/badges/badge-001/clearance")
        .then()
            .statusCode(200)
            .body("id", is("badge-001"))
            .body("clearance", is("TOP_SECRET"));
    }

    @Test
    void testChangeClearance_NotFound() {
        given()
            .queryParam("level", "TOP_SECRET")
        .when()
            .put("/api/badges/non-existent/clearance")
        .then()
            .statusCode(404);
    }

    @Test
    void testRevokeBadge() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/badges/badge-001/revoke")
        .then()
            .statusCode(200)
            .body("id", is("badge-001"))
            .body("active", is(false));
    }

    @Test
    void testRevokeBadge_NotFound() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/badges/non-existent/revoke")
        .then()
            .statusCode(404);
    }

    @Test
    void testHealthEndpoint() {
        given()
        .when()
            .get("/q/health/ready")
        .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}
