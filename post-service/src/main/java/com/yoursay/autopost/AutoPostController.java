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
    public AutoPostRunDto start() {
        return service.start(subjectEmail());
    }

    @GET
    @Path("/runs")
    public List<AutoPostRunDto> list() {
        return service.list(subjectEmail());
    }

    @GET
    @Path("/runs/{runId}")
    public AutoPostRunDto get(@PathParam("runId") UUID runId) {
        return service.get(runId, subjectEmail());
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
    public Multi<AutoPostEventDto> events(@PathParam("runId") UUID runId) {
        return service.events(runId, subjectEmail());
    }

    @POST
    @Path("/runs/{runId}/candidates/{candidateId}/select")
    @ResponseStatus(202)
    public AutoPostRunDto select(@PathParam("runId") UUID runId,
                                 @PathParam("candidateId") UUID candidateId) {
        return service.select(runId, candidateId, subjectEmail());
    }

    @POST
    @Path("/runs/{runId}/approve")
    public AutoPostRunDto approve(@PathParam("runId") UUID runId) {
        return service.approve(runId, subjectEmail());
    }

    private String subjectEmail() {
        return securityIdentity.getPrincipal().getName();
    }
}
