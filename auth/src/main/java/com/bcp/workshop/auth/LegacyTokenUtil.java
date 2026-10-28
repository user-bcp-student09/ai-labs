package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class LegacyTokenUtil {
    public static final long TOKEN_EXPIRATION_SECONDS = 3600;

    private static final String TOKEN_SECRET_ENVIRONMENT_VARIABLE = "AUTH_TOKEN_SECRET";
    private static final String TOKEN_ISSUER = "ai-labs-auth";
    private static final String TOKEN_AUDIENCE = "ai-labs-api";
    private static final String TOKEN_JWT_ALGORITHM = "HS256";
    private static final String TOKEN_TYPE = "JWT";
    private static final String TOKEN_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_HEADER = "{\"alg\":\""
            + TOKEN_JWT_ALGORITHM + "\",\"typ\":\"" + TOKEN_TYPE + "\"}";
    private static final int TOKEN_SECRET_MINIMUM_BYTES = 32;
    private static final int TOKEN_MAXIMUM_LENGTH = 8192;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final ObjectReader STRICT_JSON_READER =
            JSON_MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private LegacyTokenUtil() {}

    public static String create(String username, String role, String client) {
        return create(username, role, client, Instant.now());
    }

    static String create(String username, String role, String client, Instant issuedAt) {
        long issuedAtEpochSeconds = issuedAt.getEpochSecond();
        long expiryEpochSeconds = issuedAt.plusSeconds(TOKEN_EXPIRATION_SECONDS).getEpochSecond();
        ObjectNode claims = JSON_MAPPER.createObjectNode()
                .put("iss", TOKEN_ISSUER)
                .put("aud", TOKEN_AUDIENCE)
                .put("sub", username)
                .put("client", client)
                .put("role", role)
                .put("iat", issuedAtEpochSeconds)
                .put("exp", expiryEpochSeconds);
        String signingInput = encode(TOKEN_HEADER) + "." + encode(serialize(claims));
        return signingInput + "." + encode(sign(signingInput));
    }

    static boolean isValid(String token) {
        if (token == null || token.length() > TOKEN_MAXIMUM_LENGTH) {
            return false;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }

        byte[] suppliedSignature;
        JsonNode header;
        JsonNode claims;
        try {
            suppliedSignature = Base64.getUrlDecoder().decode(parts[2]);
            header = STRICT_JSON_READER.readTree(Base64.getUrlDecoder().decode(parts[0]));
            claims = STRICT_JSON_READER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (java.io.IOException | IllegalArgumentException error) {
            return false;
        }
        if (!MessageDigest.isEqual(
                sign(parts[0] + "." + parts[1]),
                suppliedSignature)) {
            return false;
        }

        if (header == null
                || !header.isObject()
                || !header.path("alg").isTextual()
                || !TOKEN_JWT_ALGORITHM.equals(header.path("alg").asText())
                || !header.path("typ").isTextual()
                || !TOKEN_TYPE.equals(header.path("typ").asText())
                || claims == null
                || !claims.isObject()
                || !claims.path("iss").isTextual()
                || !TOKEN_ISSUER.equals(claims.path("iss").asText())
                || !claims.path("aud").isTextual()
                || !TOKEN_AUDIENCE.equals(claims.path("aud").asText())
                || !claims.path("sub").isTextual()
                || claims.path("sub").asText().isBlank()
                || !claims.path("client").isTextual()
                || claims.path("client").asText().isBlank()
                || !claims.path("role").isTextual()
                || claims.path("role").asText().isBlank()
                || !claims.path("iat").isIntegralNumber()
                || !claims.path("exp").isIntegralNumber()
                || !claims.path("iat").canConvertToLong()
                || !claims.path("exp").canConvertToLong()) {
            return false;
        }

        long issuedAt = claims.path("iat").longValue();
        long expiresAt = claims.path("exp").longValue();
        long currentTime = Instant.now().getEpochSecond();
        return issuedAt <= currentTime && expiresAt > currentTime && expiresAt > issuedAt;
    }

    private static byte[] serialize(JsonNode value) {
        try {
            return JSON_MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Token claims could not be serialized", error);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sign(String value) {
        String secret = System.getenv(TOKEN_SECRET_ENVIRONMENT_VARIABLE);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    TOKEN_SECRET_ENVIRONMENT_VARIABLE + " must be configured");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < TOKEN_SECRET_MINIMUM_BYTES) {
            throw new IllegalStateException(
                    TOKEN_SECRET_ENVIRONMENT_VARIABLE + " must contain at least "
                            + TOKEN_SECRET_MINIMUM_BYTES + " bytes");
        }
        try {
            Mac mac = Mac.getInstance(TOKEN_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, TOKEN_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Token signing is unavailable", error);
        }
    }
}
