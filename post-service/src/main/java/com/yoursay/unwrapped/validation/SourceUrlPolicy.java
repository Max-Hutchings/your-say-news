package com.yoursay.unwrapped.validation;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.InetAddress;
import java.net.URI;

@ApplicationScoped
public class SourceUrlPolicy {
    public URI validate(String value) {
        try {
            URI uri = URI.create(value);
            require("https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                            && uri.getHost() != null && uri.getPort() == -1,
                    "UNWRAPPED_SOURCE_URL_UNSAFE");
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            require(!host.equals("localhost") && !host.endsWith(".localhost"),
                    "UNWRAPPED_SOURCE_URL_UNSAFE");
            for (InetAddress address : InetAddress.getAllByName(host)) {
                require(isPublic(address), "UNWRAPPED_SOURCE_URL_PRIVATE");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("UNWRAPPED_SOURCE_URL_INVALID", e);
        }
    }

    private static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        return !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isSiteLocalAddress()
                && !address.isMulticastAddress()
                && !uniqueLocalIpv6;
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
