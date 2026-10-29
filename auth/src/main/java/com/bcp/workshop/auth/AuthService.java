package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.time.Instant;

@ApplicationScoped
public class AuthService {
    private static final String LEGACY_FLOW_MARKER = "authenticated-with-legacy-flow";

    @Inject
    LegacyDatabase database;

    public LoginResponse authenticate(String username, String password, String client) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return null;
        }

        LoginResponse.User responseUser = new LoginResponse.User(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role());
        Instant issuedAt = Instant.now();
        long expiresAtEpochSecond = LegacyTokenUtil.expirationEpochSecond(issuedAt);
        String token = LegacyTokenUtil.create(user.username(), user.role(), client, issuedAt);
        long expiresIn = expiresAtEpochSecond - Instant.now().getEpochSecond();
        return new LoginResponse(
                token,
                expiresIn,
                responseUser,
                LEGACY_FLOW_MARKER);
    }
}
