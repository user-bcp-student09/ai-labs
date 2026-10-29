package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String ALLOWED_CORS_ORIGIN = "http://localhost:4200";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
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
    void logsInWithWorkshopUser() throws IOException {
        var response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("debug", is("authenticated-with-legacy-flow"))
                .extract()
                .response();

        String token = response.jsonPath().getString("token");
        long expiresIn = response.jsonPath().getLong("expiresIn");
        JsonNode claims = tokenClaims(token);
        long expiresAt = claims.path("exp").longValue();
        long remainingAtReceipt = expiresAt - Instant.now().getEpochSecond();

        assertEquals("angular-workshop", claims.path("client").asText());
        assertTrue(Math.abs(expiresIn - remainingAtReceipt) <= 1,
                "expiresIn must match the token's remaining lifetime within one second");
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
                .body("message", is("Client is required"))
                .body("token", nullValue());
    }

    @Test
    void rejectsNullClient() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\",\"client\":null}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is required"))
                .body("token", nullValue());
    }

    @Test
    void rejectsBlankClient() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\",\"client\":\"   \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is required"))
                .body("token", nullValue());
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
        String syntheticToken = "test-token-marker";
        String syntheticSecret = System.getenv("AUTH_TOKEN_SECRET");
        assertNotNull(syntheticSecret);

        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public LoginResponse authenticate(String username, String password, String client) {
                throw new IllegalStateException(
                        "synthetic test failure " + syntheticToken + " " + syntheticSecret);
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        request.client = "angular-workshop";

        StringBuilder capturedLogs = new StringBuilder();
        Handler logCapture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedLogs.append(record.getMessage()).append(System.lineSeparator());
                if (record.getThrown() != null) {
                    StringWriter stackTrace = new StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(stackTrace));
                    capturedLogs.append(stackTrace);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Logger logger = Logger.getLogger(AuthResource.class.getName());
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(logCapture);
        String issuedToken;
        Response response;
        try {
            issuedToken = given()
                    .contentType("application/json")
                    .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("token");
            given()
                    .contentType("application/json")
                    .body("{\"username\":\"unknown-user\",\"password\":\"incorrect\",\"client\":\"angular-workshop\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(401);
            response = resource.login(request);
        } finally {
            logger.removeHandler(logCapture);
            logger.setUseParentHandlers(useParentHandlers);
        }

        assertEquals(500, response.getStatus());
        Map<?, ?> entity = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("Internal server error", entity.get("message"));
        assertFalse(entity.containsKey("exception"));
        assertNotNull(issuedToken);
        assertTrue(capturedLogs.toString().contains("Authentication request failed"));
        assertFalse(capturedLogs.toString().contains(syntheticToken));
        assertFalse(capturedLogs.toString().contains(syntheticSecret));
        assertFalse(capturedLogs.toString().contains(issuedToken));
    }

    private static JsonNode tokenClaims(String token) throws IOException {
        String[] tokenParts = token.split("\\.", -1);
        return JSON_MAPPER.readTree(Base64.getUrlDecoder().decode(tokenParts[1]));
    }
}
