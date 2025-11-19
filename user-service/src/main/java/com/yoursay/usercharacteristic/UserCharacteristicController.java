package com.yoursay.usercharacteristic;


import com.yoursay.usercharacteristic.model.UserCharacteristic;
import com.yoursay.usercharacteristic.model.UserCharacteristicRepository;
import io.quarkus.logging.Log;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/user-characteristics")
public class UserCharacteristicController {

    UserCharacteristicRepository characteristicRepository;


    @GET
    @Path("/{id}")
    public UserCharacteristic getCharacteristicById(@PathParam(value="id")long id){
        Log.infof("Endpoint called: %s", "/api/user-characteristic/%s", id);
        return characteristicRepository.getUserCharacteristicByUserId(id);
    }


    @POST
    @Path("/save-characteristic")
    @ResponseStatus(201)
    public UserCharacteristic saveUserCharacteristic(UserCharacteristic characteristic){
        return characteristicRepository.saveUserCharacteristic(characteristic);
    }
}
