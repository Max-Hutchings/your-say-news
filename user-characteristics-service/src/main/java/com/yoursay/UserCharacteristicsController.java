package com.yoursay;


import com.yoursay.model.UserCharacteristic;
import com.yoursay.model.UserCharacteristicRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/user-characteristics")
public class UserCharacteristicsController {

    @Inject
    UserCharacteristicRepository userCharacteristicRepository;

    @RestClient
    UserServiceClient userServiceClient;



    @GET
    @Path("/{userId}")
    public Uni<UserCharacteristic> getUserCharacteristicByUserId(@PathParam("userId") Long userId){
        Log.infof("Endpoint Called: Get user characteristic by id: %d", userId);
        return userCharacteristicRepository.getUserCharacteristicByUserId(userId).onFailure().invoke(error -> Log.errorf("Failed to get user Characteristic by user id: %d. Exception: %s", userId, error));
    }


    @GET
    @Path("/{id}")
    public Uni<UserCharacteristic> getUserCharacteristicById(@PathParam("id") Long id){
        Log.infof("Endpoint Called: Get user characteristic by id: %d", id);
        return userCharacteristicRepository.getUserCharacteristicById(id).onFailure().invoke(error -> Log.errorf("Failed to get user Characteristic by id: %d. Exception: %s", id, error));
    }


    @POST
    @Path("/")
    public Uni<UserCharacteristic> saveUserCharacteristic(UserCharacteristic userCharacteristic){
        Log.infof("Endpoint Called: Save user characteristic");
        return userServiceClient.getUser(userCharacteristic.getUserId())
                .onFailure().invoke(error -> Log.errorf("Failed to check user %d exits. Exception: %s", userCharacteristic.getUserId(), error))
                .flatMap(response -> {
                    if (response.getStatus() == 204){
                        return Uni.createFrom().nullItem();
                    }
                    return userCharacteristicRepository.saveUserCharacteristic(userCharacteristic);
                }).onFailure().invoke(error -> Log.errorf("Failed to save user characteristic with id: %d. Exception: %s", userCharacteristic.getUserId(), error));
    }
}
