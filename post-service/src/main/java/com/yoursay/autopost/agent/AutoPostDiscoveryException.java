package com.yoursay.autopost.agent;

public class AutoPostDiscoveryException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public AutoPostDiscoveryException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public AutoPostDiscoveryException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
