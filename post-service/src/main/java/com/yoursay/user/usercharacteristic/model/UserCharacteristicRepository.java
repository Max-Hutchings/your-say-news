package com.yoursay.user.usercharacteristic.model;

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

    public UserCharacteristic getUserCharacteristicById(Long id) {
        return UserCharacteristic.findById(id);
    }
}
