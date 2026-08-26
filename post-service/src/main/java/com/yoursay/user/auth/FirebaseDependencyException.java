package com.yoursay.user.auth;

public class FirebaseDependencyException extends RuntimeException {

    private final String faultCode;

    public FirebaseDependencyException(String faultCode) {
        super(faultCode);
        this.faultCode = faultCode;
    }

    public FirebaseDependencyException(String faultCode, Throwable cause) {
        super(faultCode, cause);
        this.faultCode = faultCode;
    }

    public String faultCode() {
        return faultCode;
    }
}
