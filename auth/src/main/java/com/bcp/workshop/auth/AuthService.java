package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

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
        return new LoginResponse(
                LegacyTokenUtil.create(user.username(), user.role(), client),
                LegacyTokenUtil.TOKEN_EXPIRATION_SECONDS,
                responseUser,
                LEGACY_FLOW_MARKER);
    }
}
