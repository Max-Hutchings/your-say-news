package com.yoursay.unwrapped.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceUrlPolicyTest {
    @Test
    void acceptsHttpAndHttpsWithoutResolvingOrFetchingTheHost() {
        SourceUrlPolicy policy = new SourceUrlPolicy();

        assertDoesNotThrow(() -> policy.validate("https://8.8.8.8/research"));
        assertDoesNotThrow(() -> policy.validate(
                "http://www.icccodesolutions.org/blog/street-performers-legislation/"));
    }

    @Test
    void rejectsNonWebSchemesAndMalformedUrls() {
        SourceUrlPolicy policy = new SourceUrlPolicy();

        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE", assertThrows(IllegalArgumentException.class,
                () -> policy.validate("javascript:alert('source')")).getMessage());
        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE", assertThrows(IllegalArgumentException.class,
                () -> policy.validate("not-a-url")).getMessage());
    }
}
