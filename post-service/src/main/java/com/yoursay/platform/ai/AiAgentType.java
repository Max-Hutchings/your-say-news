package com.yoursay.platform.ai;

/** Stable, low-cardinality agent names used by cross-system AI telemetry. */
public enum AiAgentType {
    AUTO_POST("autopost"),
    POST_AGENT("postagent"),
    UNWRAPPED("unwrapped");

    private final String metricValue;

    AiAgentType(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
