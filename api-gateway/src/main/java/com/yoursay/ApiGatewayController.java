package com.yoursay;

import io.quarkus.logging.Log;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.security.Principal;
import java.util.Map;

@Path("/gateway")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiGatewayController {

    @RestClient
    RoutingClient routingClient;

    public String getDownstreamAuthorisation(SecurityContext securityContext) {
        OidcJwtCallerPrincipal principal = (OidcJwtCallerPrincipal) securityContext.getUserPrincipal();
        String token = principal.getRawToken();
        return "Bearer " + token;
    }

    @GET
    @Path("/{service}/{url}")
    @RolesAllowed("user")
    public Uni<String> getRouter(@PathParam("service") String service, @PathParam("url") String url, @Context SecurityContext securityContext) {

        String authorisation = getDownstreamAuthorisation(securityContext);
        return routingClient.routeGet(authorisation, service, url);
    }



    @POST
    @Path("/{service}/{url}")
    @RolesAllowed("user")
    public Uni<String> postRouter(@PathParam("service") String service, @PathParam("url") String url, @Context SecurityContext securityContext, String body) {
        String authorisation = getDownstreamAuthorisation(securityContext);
        return routingClient.routePost(authorisation, service, url, body);

    }


    @PUT
    @Path("/{service}/{url}")
    @RolesAllowed("user")
    public Uni<String> putRouter(@PathParam("service") String service, @PathParam("url") String url, @Context SecurityContext securityContext, String body) {
        String authorisation = getDownstreamAuthorisation(securityContext);
        return routingClient.routePut(authorisation, service, url, body);
    }

    @DELETE
    @Path("/{service}/{url}")
    @RolesAllowed("user")
    public Uni<String> deleteRouter(@PathParam("service") String service, @PathParam("url") String url, @Context SecurityContext securityContext, String body) {
        String authorisation = getDownstreamAuthorisation(securityContext);
        return routingClient.routeDelete(authorisation, service, url, body);

    }
}
