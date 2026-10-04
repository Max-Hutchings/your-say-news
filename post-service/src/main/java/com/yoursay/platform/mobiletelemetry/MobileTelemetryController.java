package com.yoursay.platform.mobiletelemetry;

import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryReceiptDto;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * Receives the mobile app's telemetry batches. The app cannot reach the collector on a hosted
 * environment, so post-service relays it behind the same Firebase auth as every other route.
 */
@Path("/telemetry/mobile")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class MobileTelemetryController {

    @Inject
    MobileTelemetryService service;

    @Inject
    SecurityIdentity identity;

    @POST
    @ResponseStatus(202)
    public MobileTelemetryReceiptDto record(@Valid @NotNull MobileTelemetryBatchDto batch) {
        return service.record(batch, identity.getPrincipal().getName());
    }
}
