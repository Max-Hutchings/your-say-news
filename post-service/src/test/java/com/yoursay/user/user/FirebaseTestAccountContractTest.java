package com.yoursay.user.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseTestAccountContractTest {

    @Test
    void localFirebaseAccountsHaveStableUniqueIdsAndExpectedSecurityStates() throws IOException {
        Path accountsFile = Path.of("..", "firebase", "test-accounts.json");
        assertTrue(Files.isRegularFile(accountsFile),
                () -> "Firebase test accounts not found at " + accountsFile.toAbsolutePath());

        JsonNode accounts = new ObjectMapper().readTree(accountsFile.toFile()).path("accounts");
        assertEquals(30, accounts.size());

        Set<String> uids = new HashSet<>();
        Set<String> emails = new HashSet<>();
        StreamSupport.stream(accounts.spliterator(), false).forEach(account -> {
            assertTrue(uids.add(account.path("uid").asText()), "Firebase UIDs must be unique");
            assertTrue(emails.add(account.path("email").asText()), "Firebase emails must be unique");
            assertEquals("password123", account.path("password").asText());
            assertTrue(account.path("emailVerified").asBoolean());
        });

        JsonNode admin = accountByEmail(accounts, "admin@yoursay.com");
        assertEquals("local-yoursay-admin", admin.path("uid").asText());
        assertFalse(admin.path("disabled").asBoolean());

        JsonNode inactive = accountByEmail(accounts, "bob.johnson@example.com");
        assertEquals("local-bob-johnson", inactive.path("uid").asText());
        assertTrue(inactive.path("disabled").asBoolean());

        JsonNode reader = accountByEmail(accounts, "riley.reader@example.com");
        assertEquals("Riley Reader", reader.path("displayName").asText());
        assertFalse(reader.path("disabled").asBoolean());
    }

    private static JsonNode accountByEmail(JsonNode accounts, String email) {
        return StreamSupport.stream(accounts.spliterator(), false)
                .filter(account -> email.equals(account.path("email").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Firebase account " + email));
    }
}
