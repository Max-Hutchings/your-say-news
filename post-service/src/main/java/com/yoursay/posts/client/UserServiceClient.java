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

    /** Resolve the bearer-token subject and publishing capability without accepting caller identity. */
    @GET
    @Path("/your-say-user/me/access")
    Uni<UserAccess> getCurrentUserAccess(@HeaderParam("Authorization") String authorization);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserAccess(Long userId, String accountType, String publisherStatus, boolean canPublish) {

        public boolean isActiveOfficialPublisher() {
            return "OFFICIAL".equals(accountType)
                    && "ACTIVE".equals(publisherStatus)
                    && canPublish;
        }
    }
}
