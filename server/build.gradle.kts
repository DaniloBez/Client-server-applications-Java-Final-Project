group = "org.example"
version = "1.0-SNAPSHOT"

plugins {
    id("application")
    id("com.gradleup.shadow") version "8.3.5"
}

application {
    //mainClass.set("Main")
}

dependencies {
    implementation(project(":shared"))
}