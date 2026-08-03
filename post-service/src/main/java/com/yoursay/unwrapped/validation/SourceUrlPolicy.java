package com.yoursay.unwrapped.validation;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;

@ApplicationScoped
public class SourceUrlPolicy {
    public URI validate(String value) {
        try {
            require(value != null && !value.isBlank() && value.length() <= 8192,
                    "UNWRAPPED_SOURCE_URL_INVALID");
            URI uri = URI.create(value);
            require(("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                            && uri.getHost() != null,
                    "UNWRAPPED_SOURCE_URL_UNSAFE");
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("UNWRAPPED_SOURCE_URL_INVALID", e);
        }
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
