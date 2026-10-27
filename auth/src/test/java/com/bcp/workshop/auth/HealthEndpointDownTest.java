package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(HealthDownTestProfile.class)
class HealthEndpointDownTest {
    @Test
    void returnsServiceUnavailableWhenHealthIsDown() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks.name", hasItem("forced-readiness-check"))
                .body("checks.status", hasItem("DOWN"));
    }
}
