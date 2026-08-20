package com.yoursay.autopost.validation;

public class AutoPostValidationException extends RuntimeException {

    private final String code;

    public AutoPostValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
