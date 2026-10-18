// Shared conventions for every Spring Boot microservice in services/*.
plugins {
    java
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.daw"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Security patch overrides for versions managed by the Spring Boot BOM (Trivy HIGH/CRITICAL findings).
// Remove each one once the Spring Boot release in libs.versions.toml ships a fixed version.
extra["tomcat.version"] = "11.0.27"
extra["jackson-bom.version"] = "3.1.8" // Jackson 3 (tools.jackson)
extra["jackson-2-bom.version"] = "2.21.8" // Jackson 2 (com.fasterxml), still pulled in by springdoc/swagger

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// Only the executable jar is needed in images.
tasks.named<Jar>("jar") {
    enabled = false
}
