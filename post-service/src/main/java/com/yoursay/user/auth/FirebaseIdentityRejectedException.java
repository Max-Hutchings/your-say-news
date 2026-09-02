package com.yoursay.user.auth;

public class FirebaseIdentityRejectedException extends RuntimeException {

    private final String errorCode;

    public FirebaseIdentityRejectedException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
