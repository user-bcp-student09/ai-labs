package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ToggleableReadinessCheck implements HealthCheck {
    private static final String FORCE_HEALTH_DOWN_PROPERTY = "auth.test.force-health-down";
    private static final String CHECK_NAME = "forced-readiness-check";

    @Override
    public HealthCheckResponse call() {
        boolean forceHealthDown = ConfigProvider.getConfig()
                .getOptionalValue(FORCE_HEALTH_DOWN_PROPERTY, Boolean.class)
                .orElse(false);
        if (forceHealthDown) {
            return HealthCheckResponse.down(CHECK_NAME);
        }
        return HealthCheckResponse.up(CHECK_NAME);
    }
}
