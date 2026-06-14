package com.yoursay.usercharacteristic;

/**
 * Public contract for the user-characteristic domain.
 */
public interface UserCharacteristicService {

    /** The characteristic profile for a user, or {@code null} if they have not onboarded. */
    UserCharacteristicDto getByUserId(long userId);

    /**
     * Persist (create or replace) the characteristic profile for the given user. The {@code userId}
     * is supplied by the caller from the authenticated subject — never read from the answer body —
     * preserving the PII boundary. Invalid enum values are rejected.
     */
    UserCharacteristicDto saveForUser(long userId, UserCharacteristicDto answers);
}
