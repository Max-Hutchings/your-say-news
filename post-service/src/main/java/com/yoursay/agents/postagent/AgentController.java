package com.yoursay.agents.postagent;

import com.yoursay.agents.postagent.dto.AgentGenerationEventDto;
import com.yoursay.agents.postagent.dto.GenerateAgentPostRequest;
import com.yoursay.agents.postagent.dto.PepperDraftDto;
import com.yoursay.agents.postagent.dto.UpdatePepperDraftRequest;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.UUID;

@Path("/agent/drafts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class AgentController {

    @Inject
    AgentService agentService;

    @POST
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Blocking
    public Multi<AgentGenerationEventDto> start(
            @Valid @NotNull GenerateAgentPostRequest request,
            @HeaderParam("Authorization") String authorization) {
        return agentService.start(authorization, request);
    }

    @GET
    @Path("/{draftId}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Blocking
    public Multi<AgentGenerationEventDto> events(
            @PathParam("draftId") UUID draftId,
            @HeaderParam("X-Pepper-Replica") String replicaId,
            @HeaderParam("Authorization") String authorization) {
        return agentService.events(draftId, replicaId, authorization);
    }

    @GET
    @Path("/latest")
    @RunOnVirtualThread
    public RestResponse<PepperDraftDto> latest(
            @HeaderParam("Authorization") String authorization) {
        return agentService.latest(authorization)
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @PUT
    @Path("/{draftId}")
    @RunOnVirtualThread
    public PepperDraftDto save(
            @PathParam("draftId") UUID draftId,
            @Valid @NotNull UpdatePepperDraftRequest request,
            @HeaderParam("Authorization") String authorization) {
        return agentService.save(draftId, request, authorization);
    }
}
