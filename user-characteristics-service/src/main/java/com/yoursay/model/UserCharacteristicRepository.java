package com.yoursay.model;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserCharacteristicRepository {

    public Uni<UserCharacteristic> saveUserCharacteristic(UserCharacteristic userCharacteristic) {
        return userCharacteristic.persist();
    }


    public Uni<UserCharacteristic> getUserCharacteristicByUserId(Long userId) {
        return UserCharacteristic.find("userId", userId).firstResult();
    }


    public Uni<UserCharacteristic> getUserCharacteristicById(Long id){
        return UserCharacteristic.findById(id);
    }
}
