package com.yoursay.usercharacteristic.model;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserCharacteristicRepository {

    public UserCharacteristic saveUserCharacteristic(UserCharacteristic userCharacteristic) {
         userCharacteristic.persist();
         return userCharacteristic;
    }


    public UserCharacteristic getUserCharacteristicByUserId(Long userId) {
        return UserCharacteristic.find("userId", userId).firstResult();
    }


    public UserCharacteristic getUserCharacteristicById(Long id){
        return UserCharacteristic.findById(id);
    }
}
