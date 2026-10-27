package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return badRequest("Request is null");
        }
        if (isMissing(request.username)) {
            return badRequest("Username is required");
        }
        if (isMissing(request.password)) {
            return badRequest("Password is required");
        }
        if (isMissing(request.client)) {
            return badRequest("Client is required");
        }

        try {
            LoginResponse loginResponse = authService.authenticate(
                    request.username,
                    request.password,
                    request.client);
            if (loginResponse == null) {
                return Response.status(401)
                        .entity(Map.of("message", "Invalid user " + request.username + " or password"))
                        .build();
            }
            return Response.ok(loginResponse).build();
        } catch (Exception error) {
            LOG.error("Authentication request failed", error);
            return Response.status(500)
                    .entity(Map.of("message", "Internal server error"))
                    .build();
        }
    }

    private static boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Response badRequest(String message) {
        return Response.status(400).entity(Map.of("message", message)).build();
    }
}
