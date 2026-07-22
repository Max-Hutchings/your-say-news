package com.yoursay.agents.postagent;

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
import com.yoursay.agents.postagent.error.AgentApiException;
import org.jboss.resteasy.reactive.ResponseStatus;

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
    @ResponseStatus(202)
    public AgentJobDto start(@Valid @NotNull GenerateAgentPostRequest request,
                          @HeaderParam("Authorization") String authorization) {
        return agentService.start(
                securityIdentity.getPrincipal().getName(), authorization, request);
    }

    @GET
    @Path("/{jobId}")
    public AgentJobDto get(@PathParam("jobId") UUID jobId,
                           @HeaderParam("Authorization") String authorization) {
        return agentService.get(jobId, securityIdentity.getPrincipal().getName(), authorization)
                .orElseThrow(() -> AgentApiException.jobMissing(jobId));
    }
}
