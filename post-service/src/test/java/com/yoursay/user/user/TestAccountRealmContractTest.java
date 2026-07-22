package com.yoursay.user.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class TestAccountRealmContractTest {

    @Test
    void standardProfiledReaderIsAnEnabledKeycloakUser() throws IOException {
        Path realmExport = Path.of("..", "keycloak", "realm-export.json");
        assertTrue(Files.isRegularFile(realmExport),
                () -> "Keycloak realm export not found at " + realmExport.toAbsolutePath());

        JsonNode users = new ObjectMapper().readTree(realmExport.toFile()).path("users");
        JsonNode riley = StreamSupport.stream(users.spliterator(), false)
                .filter(user -> "riley.reader".equals(user.path("username").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("riley.reader is missing from the realm export"));

        assertTrue(riley.path("enabled").asBoolean());
        assertTrue(riley.path("emailVerified").asBoolean());
        assertEquals("riley.reader@example.com", riley.path("email").asText());
        assertEquals("Riley", riley.path("firstName").asText());
        assertEquals("Reader", riley.path("lastName").asText());
        assertTrue(containsText(riley.path("realmRoles"), "user"));
        assertFalse(containsText(riley.path("realmRoles"), "admin"));
        assertTrue(StreamSupport.stream(riley.path("credentials").spliterator(), false)
                .anyMatch(credential -> "password".equals(credential.path("type").asText())
                        && !credential.path("temporary").asBoolean()));
    }

    private static boolean containsText(JsonNode array, String expected) {
        return StreamSupport.stream(array.spliterator(), false)
                .anyMatch(value -> expected.equals(value.asText()));
    }
}
