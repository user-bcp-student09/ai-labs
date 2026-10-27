package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String ALLOWED_CORS_ORIGIN = "http://localhost:4200";
    private static final String TEST_DATABASE_URL = System.getenv().getOrDefault(
            "AUTH_DB_URL",
            "jdbc:sqlite:./data/workshop.db");

    @Test
    void allowsOnlyConfiguredCorsOrigin() {
        given()
                .header("Origin", ALLOWED_CORS_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo(ALLOWED_CORS_ORIGIN));

        given()
                .header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(403);
    }

    @Test
    void reportsHealthWithoutAuthentication() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", notNullValue());
    }

    @Test
    void ignoresAuthorizationHeaderOnHealthEndpoint() {
        given()
                .header("Authorization", "Bearer not-used-on-health")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"incorrect\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user unknown-user or password"));
    }

    @Test
    void rejectsUsernameContainingQuoteAsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user'\",\"password\":\"incorrect\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"   \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsMissingClient() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsBlankClient() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\",\"client\":\"   \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));
    }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void storesSeedPasswordAsHashAfterSuccessfulLogin() throws Exception {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200);

        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT password FROM legacy_users WHERE username='student'")) {
            if (!result.next()) {
                throw new AssertionError("Expected the seeded student account");
            }
            String storedPassword = result.getString("password");
            assertNotNull(storedPassword);
            assertTrue(PasswordHasher.isHashed(storedPassword));
            assertNotEquals("Workshop123!", storedPassword);
        }
    }

    @Test
    void findsUsernameContainingQuoteWithParameterizedQuery() throws Exception {
        String quotedUsername = "student'quoted";
        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users"
                             + "(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, quotedUsername);
            statement.setString(2, "LocalTest123!");
            statement.setString(3, "Quoted Test User");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }

        UserRecord user = new LegacyDatabase().findByCredentials(quotedUsername, "LocalTest123!");

        assertNotNull(user);
        assertEquals(quotedUsername, user.username());
    }

    @Test
    void migratesLegacyPasswordAfterSuccessfulAuthentication() throws Exception {
        String username = "legacy-migration-user";
        String legacyPassword = "FictionalLegacy123!";
        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users"
                             + "(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, legacyPassword);
            statement.setString(3, "Legacy Migration User");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }

        UserRecord user = new LegacyDatabase().findByCredentials(username, legacyPassword);

        assertNotNull(user);
        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Expected the migrated account");
                }
                String storedPassword = result.getString("password");
                assertTrue(PasswordHasher.isHashed(storedPassword));
                assertNotEquals(legacyPassword, storedPassword);
            }
        }
    }

    @Test
    void migratesLegacyPasswordsDuringDatabaseInitialization() throws Exception {
        String username = "startup-migration-user";
        String legacyPassword = "FictionalStartup123!";
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200);

        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users"
                             + "(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, legacyPassword);
            statement.setString(3, "Startup Migration User");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }

        new LegacyDatabase().initialize();

        String migratedPasswordHash;
        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Expected the startup migration account");
                }
                String storedPassword = result.getString("password");
                assertTrue(PasswordHasher.isHashed(storedPassword));
                assertTrue(PasswordHasher.matches(legacyPassword, storedPassword));
                migratedPasswordHash = storedPassword;
            }
        }

        new LegacyDatabase().initialize();

        try (var connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Expected the startup migration account");
                }
                assertEquals(migratedPasswordHash, result.getString("password"));
            }
        }
    }

    @Test
    void returnsGenericInternalServerErrorWithoutExceptionDetails() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public LoginResponse authenticate(String username, String password, String client) {
                throw new IllegalStateException("synthetic test failure");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        request.client = "angular-workshop";

        Response response = resource.login(request);

        assertEquals(500, response.getStatus());
        Map<?, ?> entity = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("Internal server error", entity.get("message"));
        assertFalse(entity.containsKey("exception"));
    }
}
