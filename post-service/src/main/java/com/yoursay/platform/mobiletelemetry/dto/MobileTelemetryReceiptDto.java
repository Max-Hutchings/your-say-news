package com.yoursay.platform.mobiletelemetry.dto;

/** How many events were kept and how many failed validation, so the app can spot a schema drift. */
public record MobileTelemetryReceiptDto(int accepted, int dropped) {
}
