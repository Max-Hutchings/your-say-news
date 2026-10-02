package com.yoursay.unwrapped;

import com.yoursay.unwrapped.dto.UnwrappedFeaturesDto;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Serves the Post Unwrapped feature flags so the app picks the right post-vote journey. */
@Path("/unwrapped/features")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class UnwrappedFeaturesController {
    @Inject
    UnwrappedService service;

    @GET
    public UnwrappedFeaturesDto features() {
        return service.features();
    }
}
