package com.yoursay.unwrapped.validation;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceUrlPolicyTest {
    private final SourceUrlPolicy policy = new SourceUrlPolicy();

    @Test
    void acceptsAbsoluteHttpsUrlsCaseInsensitively() {
        assertEquals(URI.create("HTTPS://www.ons.gov.uk/economy"),
                policy.validate("HTTPS://www.ons.gov.uk/economy"));
    }

    @Test
    void rejectsMissingMalformedRelativeAndOversizedUrlsWithStableCodes() {
        assertEquals("UNWRAPPED_SOURCE_URL_INVALID", failure(null));
        assertEquals("UNWRAPPED_SOURCE_URL_INVALID", failure("   "));
        assertEquals("UNWRAPPED_SOURCE_URL_INVALID", failure("https://ons.gov.uk/[broken"));
        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE", failure("/relative/source"));
        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE", failure("https:source-without-host"));
        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE", failure("http://www.ons.gov.uk/source"));
        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE",
                failure("https://jane:secret@www.ons.gov.uk/source"));
    }

    @Test
    void acceptsExactly8192CharactersAndRejects8193() {
        String prefix = "https://www.ons.gov.uk/";
        String maximum = prefix + "a".repeat(8_192 - prefix.length());

        assertEquals(URI.create(maximum), policy.validate(maximum));
        assertEquals("UNWRAPPED_SOURCE_URL_INVALID", failure(maximum + "a"));
    }

    private String failure(String value) {
        return assertThrows(IllegalArgumentException.class, () -> policy.validate(value))
                .getMessage();
    }
}
