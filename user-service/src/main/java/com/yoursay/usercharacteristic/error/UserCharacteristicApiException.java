package com.yoursay.usercharacteristic.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class UserCharacteristicApiException extends ApiException {

    private UserCharacteristicApiException(String errorCode, Response.Status status, String detailMessage) {
        super("usercharacteristic", errorCode, status, detailMessage);
    }

    public static UserCharacteristicApiException requiredField(String field) {
        return new UserCharacteristicApiException("USER_CHARACTERISTIC_REQUIRED_FIELD", Response.Status.BAD_REQUEST,
                "Required user characteristic field is missing: field=" + field);
    }

    public static UserCharacteristicApiException requestBodyRequired() {
        return new UserCharacteristicApiException("USER_CHARACTERISTIC_REQUEST_BODY_REQUIRED", Response.Status.BAD_REQUEST,
                "User characteristic request body is required");
    }

    public static UserCharacteristicApiException invalidEnumValue(String field, String value, Class<?> enumType) {
        return new UserCharacteristicApiException("USER_CHARACTERISTIC_INVALID_ENUM", Response.Status.BAD_REQUEST,
                "Invalid user characteristic enum value: field=" + field + ", value=" + value
                        + ", enumType=" + enumType.getSimpleName());
    }

    public static UserCharacteristicApiException emptyRace() {
        return new UserCharacteristicApiException("USER_CHARACTERISTIC_EMPTY_RACE", Response.Status.BAD_REQUEST,
                "User characteristic race must contain at least one value");
    }

    public static UserCharacteristicApiException userMissing(String email) {
        return new UserCharacteristicApiException("USER_CHARACTERISTIC_USER_MISSING", Response.Status.BAD_REQUEST,
                "Cannot resolve user characteristic owner for authenticated subject email=" + email);
    }
}
