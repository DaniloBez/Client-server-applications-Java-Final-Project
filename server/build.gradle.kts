group = "org.example"
version = "1.0-SNAPSHOT"

plugins {
    application
    id("com.gradleup.shadow") version "9.4.2"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

application {
    mainClass.set("Main")
}

dependencies {
    implementation(project(":shared"))
    implementation("tools.jackson.core:jackson-core:3.2.0")
    implementation("tools.jackson.core:jackson-databind:3.2.0")
    implementation("org.flywaydb:flyway-core:12.8.1")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:12.8.1")
    runtimeOnly("org.postgresql:postgresql:42.7.11")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.mindrot:jbcrypt:0.4")
    testImplementation("org.assertj:assertj-core:4.0.0-M1")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation(project(":client"))
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
}

tasks.shadowJar {
    archiveFileName.set("server-all.jar")
    mergeServiceFiles {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}