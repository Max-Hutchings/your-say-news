package com.yoursay;

import com.yoursay.model.YourSayUser;
import com.yoursay.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.time.LocalDate;
import java.util.List;

@Path("/your-say-user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class YourSayUserController{

    @Inject
    YourSayUserRepository yourSayUserRepository;


    @POST
    @ResponseStatus(value = 201)
    public Uni<YourSayUser> saveYourSayUser(YourSayUser YourSayUser) {
        Log.infof("Endpoint called: Save YourSayUser %s", YourSayUser.getEmail());
        YourSayUser.setCreatedDate(LocalDate.now());
        return yourSayUserRepository.saveYourSayUser(YourSayUser)
                .onFailure().invoke(e -> Log.errorf("Failed to save YourSayUser %s. Exception: %s", YourSayUser.getEmail(), e));
    }

    @GET
    @Path("/{email}")
    public Uni<YourSayUser> getYourSayUser(@PathParam(value = "email") String email) {
        Log.infof("Endpoint called: Get YourSayUser %s", email);
        return yourSayUserRepository.findByEmail(email);
    }

    @GET
    @Path("/{id}")
    public Uni<YourSayUser> getYourSayUserById(@PathParam(value = "id") Long id) {
        Log.infof("Endpoint called: Get YourSayUserById %s", id);
        return yourSayUserRepository.findYourSayUserById(id);
    }

    @GET
    @Path("/all")
    public Uni<List<YourSayUser>> getYourSayUsers() {
        return yourSayUserRepository.listAll();
    }

    @DELETE
    public Uni<YourSayUser> deleteYourSayUser(YourSayUser yourSayUser) {
        return yourSayUserRepository.deleteYourSayUser(yourSayUser.id).onFailure().invoke(e -> Log.errorf("Failed to deactivate YourSayUser %s. Exception: %s", yourSayUser.getEmail(), e));
    }


}
