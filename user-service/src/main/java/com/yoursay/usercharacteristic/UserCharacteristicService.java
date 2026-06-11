package com.yoursay.usercharacteristic;

/**
 * Public contract for the user-characteristic domain.
 */
public interface UserCharacteristicService {

    UserCharacteristicDto getByUserId(long userId);

    UserCharacteristicDto save(UserCharacteristicDto characteristic);
}
