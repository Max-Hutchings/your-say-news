package com.yoursay.user.usercharacteristic.dto;

/** One immutable local-currency income band mapped to a currency-neutral reporting tier. */
public record IncomeBandDto(
        String id,
        String label,
        Long lowerInclusive,
        Long upperExclusive,
        String tier
) {
}
