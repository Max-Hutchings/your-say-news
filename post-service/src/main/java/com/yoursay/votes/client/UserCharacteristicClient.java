package com.yoursay.votes.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Blocking REST client for user-service: resolves the caller's numeric user id and fetches their
 * characteristic profile. Both calls require the caller's JWT to be forwarded so user-service can
 * authorise the request and identify the correct user.
 *
 * <p>Runs on virtual threads (all methods are synchronous/blocking).
 */
@RegisterRestClient(configKey = "user-service")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserCharacteristicClient {

    /**
     * Resolve an email to the internal user id. Returns 204 if the user does not exist.
     * The bearer token is forwarded because the endpoint is role-gated on user-service.
     */
    @GET
    @Path("/your-say-user/email/{email}")
    Response getUserByEmail(@PathParam("email") String email,
                            @HeaderParam("Authorization") String authorization);

    /**
     * The authenticated user's characteristic profile, or 204 if they have not completed
     * onboarding. Returns {@link UserCharacteristicView} on 200.
     */
    @GET
    @Path("/user-characteristics/me")
    Response getMyCharacteristics(@HeaderParam("Authorization") String authorization);

    /** Minimal view of a user — only the id crosses into the votes domain. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserRef(Long id) {
    }
}
