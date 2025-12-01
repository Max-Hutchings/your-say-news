package com.yoursay.user;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/live")
@PermitAll
public class LivenessController {

    @GET
    public Response getLive(){
        return Response.ok().build();
    }
}
