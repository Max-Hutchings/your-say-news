package com.yoursay.agent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Produces(MediaType.APPLICATION_JSON)
public interface AgentUserClient {

    @GET
    @Path("/your-say-user/email/{email}")
    Response getUserByEmail(@PathParam("email") String email,
                            @HeaderParam("Authorization") String authorization);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserRef(Long id) {
    }
}
