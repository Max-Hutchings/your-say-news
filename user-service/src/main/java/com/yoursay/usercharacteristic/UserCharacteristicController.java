package com.yoursay.usercharacteristic;


import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/user-characteristics")
public class UserCharacteristicController {

    @Inject
    UserCharacteristicService characteristicService;


    @GET
    @Path("/{id}")
    public UserCharacteristicDto getCharacteristicById(@PathParam(value="id")long id){
        Log.infof("Endpoint called: %s", "/api/user-characteristic/%s", id);
        return characteristicService.getByUserId(id);
    }


    @POST
    @Path("/save-characteristic")
    @ResponseStatus(201)
    public UserCharacteristicDto saveUserCharacteristic(UserCharacteristicDto characteristic){
        return characteristicService.save(characteristic);
    }
}
