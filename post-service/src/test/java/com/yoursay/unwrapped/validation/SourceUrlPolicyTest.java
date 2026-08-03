package com.yoursay.unwrapped.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SourceUrlPolicyTest {
    @Test
    void acceptsAnyPublicHttpsHost() {
        SourceUrlPolicy policy = new SourceUrlPolicy();

        assertDoesNotThrow(() -> policy.validate("https://8.8.8.8/research"));
    }
}
