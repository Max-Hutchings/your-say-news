package com.yoursay.platform.mobiletelemetry.service;

/**
 * The launch a batch came from. {@code userId} is the uploader's internal account id, resolved from
 * auth on the server (ADR-060); it goes on spans and logs only, never on a metric label. Null when the
 * signed-in identity has no account yet.
 */
record MobileSession(String sessionId, String platform, String osVersion, String appVersion, Long userId) {

    MobileSession withUserId(Long id) {
        return new MobileSession(sessionId, platform, osVersion, appVersion, id);
    }
}
