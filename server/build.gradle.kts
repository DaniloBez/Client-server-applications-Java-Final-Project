group = "org.example"
version = "1.0-SNAPSHOT"

plugins {
    application
    id("com.gradleup.shadow") version "9.4.2"
}

application {
    mainClass.set("Main")
}

dependencies {
    implementation(project(":shared"))
    implementation("tools.jackson.core:jackson-core:3.1.4")
    implementation("tools.jackson.core:jackson-databind:3.1.4")
    implementation("org.flywaydb:flyway-core:12.8.1")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:12.8.1")
    runtimeOnly("org.postgresql:postgresql:42.7.11")
    testImplementation("org.assertj:assertj-core:4.0.0-M1")
}

tasks.shadowJar {
    archiveFileName.set("server-all.jar")
}