package com.yoursay.unwrapped.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceUrlPolicyTest {
    @Test
    void rejectsAValidPublicHostOutsideTheGovernedAllowlist() {
        SourceUrlPolicy policy = new SourceUrlPolicy("gov.uk");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> policy.validate("https://example.com/research"));

        assertEquals("UNWRAPPED_SOURCE_DOMAIN_NOT_ALLOWED", error.getMessage());
    }
}
