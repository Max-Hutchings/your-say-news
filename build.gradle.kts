// Root build: shared configuration for every service module.
// The Quarkus plugin itself is applied per-application-module (see each service's build.gradle.kts).
subprojects {
    apply(plugin = "java")

    group = "com.yoursay"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        // Services build and run on GraalVM 25 (JDK 25). Pin the toolchain so
        // local and CI compile/test against the same language level.
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // -parameters: keep method parameter names (Quarkus REST/CDI rely on them)
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")

        // Tests run against real containers via Quarkus Dev Services (Testcontainers).
        // The docker-java client bundled in Testcontainers negotiates Docker API 1.32,
        // which modern daemons (Docker 25+/API >= 1.44) reject. Pin the client API
        // version so Testcontainers can talk to the local daemon. Honour an explicit
        // host override if one is set.
        environment("DOCKER_API_VERSION", System.getenv("DOCKER_API_VERSION") ?: "1.44")
        if (file("/var/run/docker.sock").exists()) {
            systemProperty(
                "docker.client.strategy",
                "org.testcontainers.dockerclient.UnixSocketClientProviderStrategy"
            )
        }
    }
}
