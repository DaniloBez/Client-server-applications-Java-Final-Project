plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "9.4.2"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

javafx {
    version = "25"
    modules("javafx.controls")
}

dependencies {
    implementation(project(":shared"))
    implementation("tools.jackson.core:jackson-core:3.1.4")
    implementation("tools.jackson.core:jackson-databind:3.1.4")
}

application {
    mainClass.set("ClientLauncher")
}

tasks.shadowJar {
    archiveFileName.set("client-all.jar")
}