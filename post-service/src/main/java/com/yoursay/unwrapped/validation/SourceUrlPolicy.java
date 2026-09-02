package com.yoursay.unwrapped.validation;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;

/** Accepts only bounded, absolute HTTPS URLs for persisted Unwrapped sources. */
@ApplicationScoped
public class SourceUrlPolicy {
    public URI validate(String value) {
        require(value != null && !value.isBlank() && value.length() <= 8192,
                "UNWRAPPED_SOURCE_URL_INVALID");
        URI uri = parse(value);
        require("https".equalsIgnoreCase(uri.getScheme())
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null,
                "UNWRAPPED_SOURCE_URL_UNSAFE");
        return uri;
    }

    private static URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("UNWRAPPED_SOURCE_URL_INVALID", failure);
        }
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
