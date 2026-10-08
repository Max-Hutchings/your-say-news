package com.yoursay.platform.mobiletelemetry.service;

import java.util.Arrays;
import java.util.Optional;

public enum MobileEventType {
    APP_START("app_start"),
    APP_STATE("app_state"),
    SCREEN_ENTER("screen_enter"),
    SCREEN_EXIT("screen_exit"),
    ACTION("action"),
    API_CALL("api_call"),
    ERROR("error"),
    LOG("log");

    private final String wireName;

    MobileEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    static Optional<MobileEventType> fromWireName(String value) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(value)).findFirst();
    }
}
