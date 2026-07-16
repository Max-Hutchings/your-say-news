package com.yoursay.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "xai")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface XaiResponsesClient {

    @POST
    @Path("/v1/responses")
    Response createResponse(@HeaderParam("Authorization") String authorization, JsonNode request);
}
