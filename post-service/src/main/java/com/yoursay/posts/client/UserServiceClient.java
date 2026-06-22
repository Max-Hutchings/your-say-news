package com.yoursay.posts.client;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserServiceClient {

    @GET
    @Path("/your-say-user/{id}")
    Uni<Response> getUser(@PathParam("id") Long id);

    /**
     * Resolve the author behind an authenticated email. user-service keeps the PII (email) and
     * returns the anonymised {@code id} we store on the post.
     *
     * <p>user-service's endpoints are role-gated, so the caller's bearer token must be forwarded on
     * this service-to-service call — otherwise it is rejected 401. The token is only relayed between
     * our services; it is never persisted with a post.
     */
    @GET
    @Path("/your-say-user/email/{email}")
    Uni<UserRef> getUserByEmail(@PathParam("email") String email,
                                @HeaderParam("Authorization") String authorization);

    /** Minimal view of a user — only the id crosses into the post domain. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserRef(Long id) {
    }
}
