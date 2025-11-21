package com.yoursay.user;

import com.yoursay.user.model.YourSayUser;
import com.yoursay.user.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/your-say-user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class YourSayUserController{

    @Inject
    YourSayUserRepository yourSayUserRepository;

    @Inject
    JsonWebToken jwt;




    @GET
    @Path("/data")
    @RolesAllowed("user")
    public Response getData(){
        // Return an HTTP 202 Accepted response with an empty JSON object body
        return Response.accepted().entity(Map.of("data", "This worked")).build();
    }



    @GET
    @Path("/me/basic")
    @RolesAllowed("user")
    public Response getCurrentUserBasic(@Context SecurityContext securityContext) {
        Principal principal = securityContext.getUserPrincipal();

        String username = principal != null ? principal.getName() : null;

        // Collect a small set of example roles you care about
        Set<String> knownRoles = Set.of("user", "admin");

        Set<String> userRoles = knownRoles.stream()
                .filter(securityContext::isUserInRole)
                .collect(Collectors.toSet());

        Map<String, Object> body = Map.of(
                "username", username,
                "roles", userRoles
        );

        return Response.ok(body).build();
    }

    @POST
    @Path("/save")
    @RolesAllowed("user")
    public Response saveUser(@Context SecurityContext securityContext, Map<String, String> body) {
        String email = securityContext.getUserPrincipal().getName();
        String firstName = jwt.getClaim("given_name");
        String lastName = jwt.getClaim("family_name");

        LocalDate birthDate = LocalDate.parse(body.get("birthDate"));

        YourSayUser user = new YourSayUser(email, birthDate, firstName, lastName);
        user = this.yourSayUserRepository.saveYourSayUser(user);

        return Response.ok(user).build();
    }




}
