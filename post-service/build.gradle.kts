import java.util.Locale
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("io.quarkus")
    jacoco
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val minimumLineCoverage = "0.80".toBigDecimal()
val jacocoDataFile = layout.buildDirectory.file("jacoco-quarkus.exec")
val jacocoReportDirectory = layout.buildDirectory.dir("reports/jacoco/test")

sourceSets {
    main {
        resources.srcDir(rootProject.file("liquibase/changelog"))
    }
    test {
        resources.srcDir(rootProject.file("liquibase/test"))
    }
}

dependencies {
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))
    implementation(enforcedPlatform("$quarkusPlatformGroupId:quarkus-amazon-services-bom:$quarkusPlatformVersion"))
    implementation(enforcedPlatform("io.quarkiverse.langchain4j:quarkus-langchain4j-bom:1.12.0"))

    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-rest-client-config")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-hibernate-reactive-panache")
    implementation("io.quarkus:quarkus-reactive-pg-client")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-liquibase")
    implementation("io.quarkus:quarkus-micrometer-opentelemetry")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-oidc")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.8.3")
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-openai")
    implementation("io.quarkiverse.amazonservices:quarkus-amazon-s3")
    implementation("org.jetbrains:annotations")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.testcontainers:localstack:1.21.4")
}

tasks.test {
    systemProperty("quarkus.jacoco.data-file", jacocoDataFile.get().asFile.absolutePath)
    systemProperty("quarkus.jacoco.report-location", jacocoReportDirectory.get().asFile.absolutePath)

    // Opt-in shuffle: `TEST_ORDER_SEED=<n> ./gradlew :post-service:test` reorders the test classes,
    // so a test that only passes because of data or session state another test left behind fails
    // here instead of intermittently on CI. Quarkus owns the primary orderer (it groups classes by
    // test profile so the application is not torn down mid-group), so this drives the secondary
    // hook it exposes rather than replacing it.
    System.getenv("TEST_ORDER_SEED")?.let { seed ->
        systemProperty(
            "junit.quarkus.orderer.secondary-orderer",
            "org.junit.jupiter.api.ClassOrderer\$Random"
        )
        systemProperty("junit.jupiter.execution.order.random.seed", seed)
    }

    extensions.configure<JacocoTaskExtension> {
        excludeClassLoaders = listOf("*QuarkusClassLoader")
        destinationFile = jacocoDataFile.get().asFile
    }

    finalizedBy(tasks.jacocoTestCoverageVerification)
}

// quarkus-jacoco creates the combined unit and @QuarkusTest report.
tasks.jacocoTestReport {
    enabled = false
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    executionData.setFrom(jacocoDataFile)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = minimumLineCoverage
            }
        }
    }

    doFirst {
        val reportFile = jacocoReportDirectory.get().file("jacoco.xml").asFile
        check(reportFile.exists()) {
            "JaCoCo XML report was not generated at ${reportFile.absolutePath}"
        }

        val report = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            .newDocumentBuilder()
            .parse(reportFile)
            .documentElement
        val counters = report.childNodes
        val lineCounter = (0 until counters.length)
            .map { counters.item(it) }
            .filterIsInstance<org.w3c.dom.Element>()
            .first { it.tagName == "counter" && it.getAttribute("type") == "LINE" }
        val missed = lineCounter.getAttribute("missed").toInt()
        val covered = lineCounter.getAttribute("covered").toInt()
        val percentage = if (missed + covered == 0) 100.0 else covered * 100.0 / (missed + covered)

        logger.lifecycle(
            "Backend line coverage: %.2f%% (minimum: %.0f%%)".format(
                Locale.ROOT,
                percentage,
                minimumLineCoverage.toDouble() * 100
            )
        )
        logger.lifecycle("Backend coverage report: ${jacocoReportDirectory.get().file("index.html").asFile.absolutePath}")
    }
}
