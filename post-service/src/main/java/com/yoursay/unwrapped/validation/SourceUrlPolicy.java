package com.yoursay.unwrapped.validation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SourceUrlPolicy {
    static final String DEFAULT_ALLOWED_SUFFIXES = "gov,gov.uk,gov.au,gc.ca,europa.eu,"
            + "who.int,un.org,worldbank.org,oecd.org,imf.org,edu,edu.au,ac.uk,doi.org,"
            + "reuters.com,apnews.com,bbc.com";

    private final Set<String> allowedHostSuffixes;

    public SourceUrlPolicy() {
        this(DEFAULT_ALLOWED_SUFFIXES);
    }

    @Inject
    public SourceUrlPolicy(@ConfigProperty(name = "unwrapped.sources.allowed-host-suffixes",
            defaultValue = DEFAULT_ALLOWED_SUFFIXES) String configuredSuffixes) {
        this.allowedHostSuffixes = Arrays.stream(configuredSuffixes.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

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
            require(allowedHostSuffixes.stream()
                            .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix)),
                    "UNWRAPPED_SOURCE_DOMAIN_NOT_ALLOWED");
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
