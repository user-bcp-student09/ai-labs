package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTokenUtilTest {
    @Test
    void createsSignedTokenWithExpectedClaimsAndExpiration() {
        String token = LegacyTokenUtil.create("student", "STUDENT", "angular-workshop");
        String[] parts = token.split("\\.");
        String header = decode(parts[0]);
        String claims = decode(parts[1]);

        assertTrue(header.contains("\"alg\":\"HS256\""));
        assertTrue(header.contains("\"typ\":\"JWT\""));
        assertTrue(claims.contains("\"iss\":\"ai-labs-auth\""));
        assertTrue(claims.contains("\"aud\":\"ai-labs-api\""));
        assertTrue(claims.contains("\"sub\":\"student\""));
        assertTrue(claims.contains("\"client\":\"angular-workshop\""));
        assertTrue(claims.contains("\"role\":\"STUDENT\""));
        assertTrue(claims.contains("\"iat\":"));
        assertTrue(claims.contains("\"exp\":"));
        assertTrue(LegacyTokenUtil.isValid(token));
    }

    @Test
    void rejectsAlteredSignature() {
        String[] parts = LegacyTokenUtil.create("student", "STUDENT", "angular-workshop").split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);

        assertFalse(LegacyTokenUtil.isValid(String.join(".", parts)));
    }

    @Test
    void rejectsExpiredToken() {
        Instant expiredIssueTime = Instant.now()
                .minusSeconds(LegacyTokenUtil.TOKEN_EXPIRATION_SECONDS + 1);
        String token = LegacyTokenUtil.create("student", "STUDENT", "angular-workshop", expiredIssueTime);

        assertFalse(LegacyTokenUtil.isValid(token));
    }

    @Test
    void rejectsMalformedTokensAndClaims() throws GeneralSecurityException {
        assertFalse(LegacyTokenUtil.isValid(null));
        assertFalse(LegacyTokenUtil.isValid("not-a-token"));
        assertFalse(LegacyTokenUtil.isValid("a.b.%%%"));
        assertFalse(LegacyTokenUtil.isValid("x".repeat(9000)));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken("{\"sub\":\"student\"}")));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken(
                "{\"iss\":\"ai-labs-auth\",\"iss\":\"ai-labs-auth\","
                        + "\"aud\":\"ai-labs-api\",\"sub\":\"student\",\"client\":\"angular-workshop\","
                        + "\"role\":\"STUDENT\",\"iat\":1,\"exp\":9999999999}")));
        assertFalse(LegacyTokenUtil.isValid(
                createSignedToken("{\"exp\":999999999999999999999999999}")));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}",
                validClaims("\"other-issuer\""))));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken(
                validClaims("\"other-issuer\""))));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken(validClaims(
                "\"ai-labs-auth\"",
                "\"other-service\""))));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken("{invalid-json")));
        assertFalse(LegacyTokenUtil.isValid(createSignedToken(
                validClaims("\"ai-labs-auth\"") + " trailing")));
        long futureIssueTime = Instant.now().plusSeconds(60).getEpochSecond();
        assertFalse(LegacyTokenUtil.isValid(createSignedToken(
                "{\"iss\":\"ai-labs-auth\",\"sub\":\"student\",\"client\":\"angular-workshop\",\"role\":\"STUDENT\","
                        + "\"iat\":" + futureIssueTime + ",\"exp\":"
                        + (futureIssueTime + LegacyTokenUtil.TOKEN_EXPIRATION_SECONDS) + "}")));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String createSignedToken(String claims) throws GeneralSecurityException {
        return createSignedToken("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", claims);
    }

    private static String validClaims(String issuer) {
        return validClaims(issuer, "\"ai-labs-api\"");
    }

    private static String validClaims(String issuer, String audience) {
        long issuedAt = Instant.now().getEpochSecond();
        return "{\"iss\":" + issuer
                + ",\"aud\":" + audience
                + ",\"sub\":\"student\",\"client\":\"angular-workshop\",\"role\":\"STUDENT\",\"iat\":" + issuedAt
                + ",\"exp\":" + (issuedAt + LegacyTokenUtil.TOKEN_EXPIRATION_SECONDS) + "}";
    }

    private static String createSignedToken(String headerJson, String claims)
            throws GeneralSecurityException {
        String header = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                System.getenv("AUTH_TOKEN_SECRET").getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }
}
