package com.yoursay.user.auth;

import com.yoursay.user.auth.dto.AdminSignInMethod;
import com.yoursay.user.auth.dto.AdminWebConfigDto;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Hands the admin SPA its Firebase web settings at runtime (ADR-064). The SPA is built once into an
 * image that is promoted from dev to prod, so these cannot be baked in at build time.
 */
@Path("/api/auth/admin/config")
@Produces(MediaType.APPLICATION_JSON)
public class AdminWebConfigController {

    private final AdminWebConfigDto config;

    AdminWebConfigController(
            @ConfigProperty(name = "firebase.auth.project-id") String projectId,
            @ConfigProperty(name = "firebase.web.api-key") String apiKey,
            @ConfigProperty(name = "firebase.web.app-id") String appId,
            @ConfigProperty(name = "firebase.web.sign-in-method") AdminSignInMethod signInMethod,
            @ConfigProperty(name = "firebase.web.auth-emulator-url") Optional<String> authEmulatorUrl
    ) {
        this.config = new AdminWebConfigDto(
                projectId,
                projectId + ".firebaseapp.com",
                apiKey,
                appId,
                signInMethod,
                authEmulatorUrl.orElse(null));
    }

    @GET
    @PermitAll
    public AdminWebConfigDto webConfig() {
        return config;
    }
}
