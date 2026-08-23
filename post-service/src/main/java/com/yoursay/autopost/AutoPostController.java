package com.yoursay.autopost;

import com.yoursay.autopost.dto.AutoPostEventDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;
import java.util.UUID;

@Path("/api/admin/auto-post")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@RunOnVirtualThread
public class AutoPostController {

    @Inject
    AutoPostService service;

    @Inject
    SecurityIdentity securityIdentity;

    @POST
    @Path("/runs")
    @ResponseStatus(202)
    public AutoPostRunDto startDiscoveryRun() {
        return service.startDiscoveryRun(subjectEmail());
    }

    @GET
    @Path("/runs")
    public List<AutoPostRunDto> listRecentRuns() {
        return service.listRecentRuns(subjectEmail());
    }

    @GET
    @Path("/runs/{runId}")
    public AutoPostRunDto getRun(@PathParam("runId") UUID runId) {
        return service.getRun(runId, subjectEmail());
    }

    @GET
    @Path("/runs/{runId}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    // Returning Multi would otherwise mark this endpoint non-blocking, which contradicts the
    // class-level @RunOnVirtualThread and makes RESTEasy resolve the conflict for us at startup.
    // The method does block before it returns the stream (it checks the caller is an administrator),
    // so state that here: it keeps running on a virtual thread, with the intent declared rather
    // than inferred.
    @Blocking
    public Multi<AutoPostEventDto> streamRunEvents(@PathParam("runId") UUID runId) {
        return service.streamRunEvents(runId, subjectEmail());
    }

    @POST
    @Path("/runs/{runId}/candidates/{candidateId}/select")
    @ResponseStatus(202)
    public AutoPostRunDto selectCandidateForDrafting(@PathParam("runId") UUID runId,
                                                      @PathParam("candidateId") UUID candidateId) {
        return service.selectCandidateForDrafting(runId, candidateId, subjectEmail());
    }

    @POST
    @Path("/runs/{runId}/approve")
    public AutoPostRunDto approveAndPublishDraft(@PathParam("runId") UUID runId) {
        return service.approveAndPublishDraft(runId, subjectEmail());
    }

    private String subjectEmail() {
        return securityIdentity.getPrincipal().getName();
    }
}
