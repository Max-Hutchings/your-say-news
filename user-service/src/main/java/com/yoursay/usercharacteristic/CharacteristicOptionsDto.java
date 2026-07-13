package com.yoursay.usercharacteristic;

import java.util.List;
import java.util.Map;

/**
 * Versioned onboarding metadata. The backend owns accepted enum values and the curated labels/order
 * offered for new answers; legacy enum constants used by stored profiles are intentionally omitted.
 */
public record CharacteristicOptionsDto(
        int schemaVersion,
        int minimumAge,
        Map<String, List<CharacteristicOptionDto>> fields
) {
}
