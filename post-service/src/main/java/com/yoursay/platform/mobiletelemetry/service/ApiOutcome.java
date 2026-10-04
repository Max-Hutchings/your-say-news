package com.yoursay.platform.mobiletelemetry.service;

/**
 * The success / error / fault outcome of one API call, as the app saw it. A handled 4xx is an
 * error; a 5xx, a dropped connection or a timeout is a fault, because the app could not finish what
 * the user asked for.
 */
record ApiOutcome(String outcome, String errorCode, String faultCode) {

    static final String NONE = "none";

    static ApiOutcome classify(int status, String errorKind) {
        if (status >= 200 && status < 400) {
            return new ApiOutcome("success", NONE, NONE);
        }
        if (status >= 400 && status < 500) {
            return new ApiOutcome("error", "http_" + status, NONE);
        }
        if (status >= 500 && status < 600) {
            return new ApiOutcome("fault", NONE, "http_" + status);
        }
        return switch (errorKind) {
            case "cancelled" -> new ApiOutcome("error", "cancelled", NONE);
            case "network" -> new ApiOutcome("fault", NONE, "network_error");
            case "timeout" -> new ApiOutcome("fault", NONE, "timeout");
            default -> new ApiOutcome("fault", NONE, "unknown");
        };
    }

    boolean isSuccess() {
        return "success".equals(outcome);
    }

    boolean isFault() {
        return "fault".equals(outcome);
    }

    String failureCode() {
        return isFault() ? faultCode : errorCode;
    }
}
