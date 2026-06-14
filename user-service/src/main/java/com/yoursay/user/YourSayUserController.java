package com.yoursay.user;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.time.LocalDate;
import java.util.Map;


@Path("/your-say-user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class YourSayUserController {

    @Inject
    YourSayUserService userService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public YourSayUserDto getUser() {
        String email = securityIdentity.getPrincipal().getName();

        JsonWebToken jwt = (JsonWebToken) securityIdentity.getPrincipal();
        String firstName = jwt.getClaim("given_name");
        String lastName = jwt.getClaim("family_name");

        return userService.getOrCreateFromIdentity(email, firstName, lastName);
    }


    @GET
    @Path("/data")
    public Response getData() {
        // Return an HTTP 202 Accepted response with an empty JSON object body
        return Response.accepted().entity(Map.of("data", "This worked")).build();
    }


    /**
     * Record the authenticated user's explicit consent to the privacy promise. The body carries the
     * policy version they agreed to; the identity comes from the token.
     */
    @POST
    @Path("/consent")
    public YourSayUserDto recordConsent(Map<String, String> body) {
        String email = securityIdentity.getPrincipal().getName();
        String version = body.getOrDefault("privacyPolicyVersion", "unversioned");
        return userService.recordConsent(email, version);
    }

    @POST
    @Path("/save")
    @ResponseStatus(201)
    public YourSayUserDto saveUser(Map<String, String> body) {
        String email = securityIdentity.getPrincipal().getName();

        String firstName = (String) securityIdentity.getAttribute("given_name");
        String lastName = (String) securityIdentity.getAttribute("family_name");

        LocalDate birthDate = LocalDate.parse(body.get("birthDate"));

        return userService.save(email, firstName, lastName, birthDate);
    }

    @GET
    @Path("/id/{id}")
    public YourSayUserDto getUserById(@PathParam(value = "id") long userId) {
        return userService.getById(userId);
    }


    @GET
    @Path("/email/{email}")
    public YourSayUserDto getUserByEmail(@PathParam(value = "email") String email) {
        return userService.getByEmail(email);
    }
}
