package com.yoursay.agent;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/agent/jobs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class AgentController {

    @Inject
    AgentService agentService;

    @Inject
    SecurityIdentity securityIdentity;

    @POST
    public Response start(@Valid @NotNull GenerateAgentPostRequest request,
                          @HeaderParam("Authorization") String authorization) {
        AgentJobDto job = agentService.start(
                securityIdentity.getPrincipal().getName(), authorization, request);
        return Response.accepted(job).build();
    }

    @GET
    @Path("/{jobId}")
    public Response get(@PathParam("jobId") UUID jobId,
                        @HeaderParam("Authorization") String authorization) {
        return agentService.get(jobId, securityIdentity.getPrincipal().getName(), authorization)
                .map(job -> Response.ok(job).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
