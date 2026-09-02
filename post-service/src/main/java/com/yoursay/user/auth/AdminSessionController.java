package com.yoursay.user.auth;

import com.yoursay.user.auth.dto.AdminIdentityDto;
import com.yoursay.user.auth.dto.AdminSessionRequest;
import com.yoursay.user.auth.dto.CsrfTokenDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.dto.YourSayUserDto;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.http.HttpServerResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.Set;

@Path("/api/auth/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@IfBuildProfile("dev")
public class AdminSessionController {

    @Inject
    AdminSessionService sessionService;

    @Inject
    AdminCookieService cookieService;

    @Inject
    YourSayUserService userService;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    HttpServerResponse response;

    @ConfigProperty(name = "firebase.auth.allowed-origins")
    Set<String> allowedOrigins;

    @GET
    @Path("/csrf")
    @PermitAll
    public CsrfTokenDto csrfToken() {
        return new CsrfTokenDto(cookieService.issueCsrfCookie(response));
    }

    @POST
    @Path("/session")
    @PermitAll
    public AdminIdentityDto createSession(
            @Valid AdminSessionRequest request,
            @HeaderParam("Origin") String origin,
            @HeaderParam("X-CSRF-Token") String csrfHeader,
            @CookieParam(AdminCookieService.CSRF_COOKIE_NAME) String csrfCookie
    ) {
        requireTrustedMutation(origin, csrfHeader, csrfCookie);
        CreatedAdminSession session = sessionService.create(request.idToken());
        cookieService.issueSessionCookie(response, session.cookieValue());
        return session.identity();
    }

    @GET
    @Path("/session")
    @RolesAllowed("admin")
    public AdminIdentityDto currentSession() {
        YourSayUserDto user = userService.getByEmail(securityIdentity.getPrincipal().getName());
        if (user == null || !userService.hasActiveAdminAccess(user.email())) {
            throw new jakarta.ws.rs.ForbiddenException("Active administrator access is required.");
        }
        return new AdminIdentityDto(user.email(), user.firstName() + " " + user.lastName());
    }

    @POST
    @Path("/logout")
    @RolesAllowed("admin")
    @ResponseStatus(204)
    public void logout(
            @HeaderParam("Origin") String origin,
            @HeaderParam("X-CSRF-Token") String csrfHeader,
            @CookieParam(AdminCookieService.CSRF_COOKIE_NAME) String csrfCookie
    ) {
        requireTrustedMutation(origin, csrfHeader, csrfCookie);
        cookieService.clearSessionCookie(response);
    }

    private void requireTrustedMutation(String origin, String csrfHeader, String csrfCookie) {
        if (!allowedOrigins.contains(origin)
                || csrfHeader == null
                || !csrfHeader.equals(csrfCookie)) {
            throw new WebApplicationException("CSRF validation failed.", 403);
        }
    }
}
