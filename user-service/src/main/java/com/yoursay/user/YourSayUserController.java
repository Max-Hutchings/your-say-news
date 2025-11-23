package com.yoursay.user;

import com.yoursay.user.model.YourSayUser;
import com.yoursay.user.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
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
@RolesAllowed("user")
@RunOnVirtualThread
public class YourSayUserController{

    @Inject
    YourSayUserRepository yourSayUserRepository;

    @Inject
    SecurityIdentity securityIdentity;



    @GET
    @Path("/data")
    public Response getData(){
        // Return an HTTP 202 Accepted response with an empty JSON object body
        return Response.accepted().entity(Map.of("data", "This worked")).build();
    }


    @POST
    @Path("/save")
    @ResponseStatus(201)
    public YourSayUser saveUser(Map<String, String> body) {
        String email = securityIdentity.getPrincipal().getName();

        String firstName = (String) securityIdentity.getAttribute("given_name");
        String lastName  = (String) securityIdentity.getAttribute("family_name");

        LocalDate birthDate = LocalDate.parse(body.get("birthDate"));

        YourSayUser user = new YourSayUser(email, birthDate, firstName, lastName);
        user = this.yourSayUserRepository.saveYourSayUser(user);

        return user;
    }

    @GET
    @Path("/id/{id}")
    public YourSayUser getUserById(@PathParam(value="id") long userId){
        return yourSayUserRepository.findYourSayUserById(userId);
    }


    @GET
    @Path("/email/{email}")
    public YourSayUser getUserByEmail(@PathParam(value="email") String email){
        return yourSayUserRepository.findByEmail(email);
    }




}
