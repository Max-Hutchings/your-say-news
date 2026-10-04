package com.yoursay.platform.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ADR-055 environment rules on the real config and deployment files: base and hosted
 * profiles never fall back to laptop services, hosted profiles only set runtime properties (one
 * promoted image), hosted secrets have no defaults, and the secret names agree across
 * application.properties, service/deploy/compose.yaml and render-runtime-env.sh.
 */
class EnvironmentConfigContractTest {

    private static final Path MAIN_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path DEPLOY_COMPOSE = Path.of("../service/deploy/compose.yaml");
    private static final Path RENDER_RUNTIME_ENV = Path.of("../service/deploy/scripts/render-runtime-env.sh");
    private static final Set<String> HOSTED_PROFILES = Set.of("dev", "prod");
    private static final Pattern LAPTOP_VALUE = Pattern.compile(
            "localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2|host\\.docker\\.internal|:4566|:9099"
                    + "|demo-your-say-news|app_user|app_password|post-videos");
    private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern REQUIRED_ENV_REFERENCE = Pattern.compile("^\\$\\{[A-Z0-9_]+}$");
    private static final Pattern COMPOSE_INTERPOLATED_ENV = Pattern.compile("(?m)^ {6}[A-Z0-9_]+: \\$\\{([A-Z0-9_]+)");
    private static final Pattern COMPOSE_LITERAL_ENV = Pattern.compile("(?m)^ {6}([A-Z0-9_]+): (?!\\$\\{)");
    private static final Pattern RENDERED_ENV_KEY = Pattern.compile("write_(?:literal|quoted) ([A-Z0-9_]+)");
    private static final Pattern BUILD_PROFILE_ANNOTATION = Pattern.compile("(@(?:If|Unless)BuildProfile\\([^)]*\\))");
    // Laptop-only beans and beans excluded from tests are fine; nothing may tell dev from prod.
    private static final Set<String> ALLOWED_BUILD_PROFILE_ANNOTATIONS = Set.of(
            "@IfBuildProfile(\"local\")",
            "@UnlessBuildProfile(\"test\")");
    // Read by the Firebase Admin SDK itself; compose sets it to the fixed mounted credential path.
    private static final Set<String> COMPOSE_LITERAL_ENV_ALLOWED = Set.of("GOOGLE_APPLICATION_CREDENTIALS");

    // Runtime properties a hosted profile may set. Everything else is either build-time (baked into
    // the promoted image) or must stay identical across environments, so it belongs in base.
    private static final Set<String> HOSTED_RUNTIME_PROPERTIES = Set.of(
            "app.environment",
            "quarkus.otel.exporter.otlp.endpoint",
            "mobile-telemetry.otlp.endpoint",
            "firebase.auth.project-id",
            "quarkus.datasource.username",
            "quarkus.datasource.password",
            "quarkus.datasource.jdbc.url",
            "quarkus.datasource.reactive.url",
            "quarkus.datasource.jdbc.max-size",
            "quarkus.datasource.reactive.max-size",
            "agent.provider",
            "agent.api-key",
            "agent.model",
            "quarkus.s3.endpoint-override",
            "quarkus.s3.aws.region",
            "quarkus.s3.aws.credentials.static-provider.access-key-id",
            "quarkus.s3.aws.credentials.static-provider.secret-access-key",
            "quarkus.s3.path-style-access",
            "posts.media.bucket",
            "votes.aggregation.suppress-below");

    private static Properties config;

    @BeforeAll
    static void loadApplicationProperties() throws IOException {
        config = new Properties();
        try (Reader reader = Files.newBufferedReader(MAIN_PROPERTIES)) {
            config.load(reader);
        }
    }

    @Test
    void baseAndHostedProfilesNeverPointAtLaptopServicesOrFixtures() {
        Stream.concat(baseProperties().entrySet().stream(), hostedProperties().entrySet().stream())
                .forEach(entry -> assertTrue(
                        !LAPTOP_VALUE.matcher(entry.getValue()).find(),
                        entry.getKey() + " must not use a laptop value but was " + entry.getValue()));

        assertAll(
                () -> assertEquals("https://9538d45e127bdb7d6b1bf1ecf9020146.eu.r2.cloudflarestorage.com",
                        effective("dev", "quarkus.s3.endpoint-override")),
                () -> assertEquals("your-say-news-media-development", effective("dev", "posts.media.bucket")),
                // Quarkus defaults the OTLP endpoint to localhost:4317, so a missing line is invisible to the scan.
                () -> assertEquals("http://alloy:4317", effective("dev", "quarkus.otel.exporter.otlp.endpoint")),
                () -> assertEquals("http://alloy:4318", effective("dev", "mobile-telemetry.otlp.endpoint")));
    }

    @Test
    void hostedProfilesOnlySetAllowListedRuntimeProperties() {
        hostedProperties().keySet().forEach(key -> assertTrue(
                HOSTED_RUNTIME_PROPERTIES.contains(propertyName(key)),
                key + " is not an allow-listed runtime property; build-time or shared config belongs in base"));
    }

    @Test
    void baseIsHostedSafeAndLocalOptsIntoLaptopConveniences() {
        assertAll(
                () -> assertEquals("false", config.getProperty("quarkus.http.cors.enabled")),
                () -> assertEquals("true", effective("local", "quarkus.http.cors.enabled")),
                () -> assertEquals("false", config.getProperty("quarkus.liquibase.migrate-at-start")),
                () -> assertEquals("true", effective("local", "quarkus.liquibase.migrate-at-start")),
                () -> assertEquals("true", config.getProperty("quarkus.quinoa.just-build")),
                () -> assertEquals("false", effective("local", "quarkus.quinoa.just-build")),
                () -> assertEquals("/api", config.getProperty("quarkus.http.root-path")),
                () -> assertEquals("/", effective("local", "quarkus.http.root-path")),
                () -> assertEquals("false", config.getProperty("agent.log-full-failed-response")),
                () -> assertEquals("true", effective("local", "agent.log-full-failed-response")));
    }

    @Test
    void hostedSecretsAreRequiredEnvReferencesWithoutDefaults() {
        hostedProperties().forEach((key, value) -> {
            Matcher reference = ENV_REFERENCE.matcher(value);
            while (reference.find()) {
                assertTrue(!reference.group(1).contains(":"),
                        key + " must not give an env reference a default but was " + value);
            }
        });

        List.of(
                "quarkus.datasource.username",
                "quarkus.datasource.password",
                "quarkus.datasource.jdbc.url",
                "quarkus.datasource.reactive.url",
                "quarkus.s3.aws.credentials.static-provider.access-key-id",
                "quarkus.s3.aws.credentials.static-provider.secret-access-key",
                "firebase.auth.project-id",
                "agent.api-key"
        ).forEach(key -> assertTrue(
                REQUIRED_ENV_REFERENCE.matcher(String.valueOf(effective("dev", key))).matches(),
                "%dev." + key + " must be a required env reference but was " + effective("dev", key)));
    }

    @Test
    void devSecretNamesMatchWhatComposePassesAndRenderRuntimeEnvWrites() throws IOException {
        String postServiceEnvironment = postServiceComposeEnvironment();
        Set<String> interpolatedFromRuntimeEnv = matches(COMPOSE_INTERPOLATED_ENV, postServiceEnvironment);
        Set<String> literalInCompose = matches(COMPOSE_LITERAL_ENV, postServiceEnvironment);

        Set<String> passedSecrets = new TreeSet<>(interpolatedFromRuntimeEnv);
        passedSecrets.remove("QUARKUS_PROFILE");

        assertAll(
                () -> assertEquals(passedSecrets, envReferences("dev"),
                        "compose must pass exactly the env vars %dev references"),
                () -> assertTrue(renderedRuntimeEnvKeys().containsAll(interpolatedFromRuntimeEnv),
                        "render-runtime-env.sh must write every env var compose passes: " + interpolatedFromRuntimeEnv),
                () -> assertEquals(COMPOSE_LITERAL_ENV_ALLOWED, literalInCompose,
                        "compose may only hard-code the Firebase SDK credential path"));
    }

    @Test
    void publicPathsFollowTheRootPathSoTheHostedHealthCheckStaysPublic() {
        List<String> publicPaths = Arrays.stream(config.getProperty("quarkus.http.auth.permission.public.paths").split(","))
                .map(String::trim)
                .toList();

        assertTrue(publicPaths.contains("live"), "the deploy health check calls /api/live: " + publicPaths);
        // Quarkus only prefixes quarkus.http.root-path onto relative permission paths.
        publicPaths.forEach(path -> assertTrue(!path.startsWith("/"),
                path + " is absolute, so it would not move under /api on hosted environments"));
    }

    @Test
    void eachEnvironmentNamesItselfForTelemetryAndUnknownProfilesFailStartup() {
        assertAll(
                () -> assertEquals("local", effective("local", "app.environment")),
                () -> assertEquals("dev", effective("dev", "app.environment")),
                () -> assertNull(config.getProperty("app.environment")),
                () -> assertNull(effective("prod", "app.environment")));
    }

    @Test
    void buildProfileAnnotationsOnlySeparateLaptopFromPackagedImage() throws IOException {
        try (Stream<Path> sources = Files.walk(MAIN_JAVA)) {
            List<String> annotations = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(EnvironmentConfigContractTest::buildProfileAnnotations)
                    .toList();

            assertTrue(!annotations.isEmpty(), "expected the Firebase and admin session classes to be annotated");
            annotations.forEach(annotation -> assertTrue(ALLOWED_BUILD_PROFILE_ANNOTATIONS.contains(annotation),
                    annotation + " is not allowed; dev and prod share one image, so only local or test may differ"));
        }
    }

    private static Map<String, String> baseProperties() {
        return config.stringPropertyNames().stream()
                .filter(key -> !key.startsWith("%"))
                .collect(Collectors.toMap(key -> key, config::getProperty, (a, b) -> a, TreeMap::new));
    }

    /** Every key that applies to dev or prod, including multi-profile keys such as {@code %dev,local.x}. */
    private static Map<String, String> hostedProperties() {
        return config.stringPropertyNames().stream()
                .filter(key -> key.startsWith("%"))
                .filter(key -> profilesOf(key).stream().anyMatch(HOSTED_PROFILES::contains))
                .collect(Collectors.toMap(key -> key, config::getProperty, (a, b) -> a, TreeMap::new));
    }

    private static Set<String> profilesOf(String profiledKey) {
        String profiles = profiledKey.substring(1, profiledKey.indexOf('.'));
        return Arrays.stream(profiles.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    private static String propertyName(String profiledKey) {
        return profiledKey.substring(profiledKey.indexOf('.') + 1);
    }

    private static String effective(String profile, String key) {
        return config.stringPropertyNames().stream()
                .filter(candidate -> candidate.startsWith("%"))
                .filter(candidate -> propertyName(candidate).equals(key))
                .filter(candidate -> profilesOf(candidate).contains(profile))
                .map(config::getProperty)
                .findFirst()
                .orElse(config.getProperty(key));
    }

    private static Set<String> envReferences(String profile) {
        Set<String> names = new TreeSet<>();
        hostedProperties().forEach((key, value) -> {
            if (profilesOf(key).contains(profile)) {
                Matcher reference = ENV_REFERENCE.matcher(value);
                while (reference.find()) {
                    names.add(reference.group(1));
                }
            }
        });
        return names;
    }

    private static String postServiceComposeEnvironment() throws IOException {
        String compose = Files.readString(DEPLOY_COMPOSE);
        int serviceStart = compose.indexOf("\n  post-service:\n");
        int nextService = compose.indexOf("\n  alloy:\n", serviceStart);
        String postServiceBlock = compose.substring(serviceStart, nextService);
        return postServiceBlock.substring(
                postServiceBlock.indexOf("    environment:\n"),
                postServiceBlock.indexOf("    restart:"));
    }

    private static Set<String> renderedRuntimeEnvKeys() throws IOException {
        return matches(RENDERED_ENV_KEY, Files.readString(RENDER_RUNTIME_ENV));
    }

    private static Stream<String> buildProfileAnnotations(Path source) {
        try {
            return matches(BUILD_PROFILE_ANNOTATION, Files.readString(source)).stream();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
