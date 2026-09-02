package com.yoursay.autopost.agent;

public class AutoPostDiscoveryException extends RuntimeException {

    private final String code;
    private final String faultType;
    private final String stage;
    private final boolean retryable;

    public AutoPostDiscoveryException(String code, String message, boolean retryable) {
        this(code, "dependency", "provider_request", message, retryable, null);
    }

    public AutoPostDiscoveryException(String code, String message, boolean retryable, Throwable cause) {
        this(code, "dependency", "provider_request", message, retryable, cause);
    }

    public AutoPostDiscoveryException(
            String code,
            String stage,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        this(code, "provider_contract", stage, message, retryable, cause);
    }

    public AutoPostDiscoveryException(
            String code,
            String faultType,
            String stage,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.faultType = faultType;
        this.stage = stage;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String stage() {
        return stage;
    }

    public String faultType() {
        return faultType;
    }

    public boolean retryable() {
        return retryable;
    }
}
