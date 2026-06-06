group = "org.example"
version = "1.0-SNAPSHOT"

subprojects {
    plugins.apply("java")
    plugins.apply("checkstyle")

    repositories {
        mavenCentral()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:6.0.0"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    configure<CheckstyleExtension> {
        toolVersion = "13.5.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
        isShowViolations = true
    }

    tasks.withType<Checkstyle> {
        reports {
            xml.required.set(false)
            html.required.set(true)
        }
    }
}