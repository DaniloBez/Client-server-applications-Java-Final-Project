group = "org.example"
version = "1.0-SNAPSHOT"

subprojects {
    plugins.apply("java")
    plugins.apply("checkstyle")

    repositories {
        mavenCentral()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:6.1.0"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("compileOnly","org.projectlombok:lombok:1.18.46")
        add("annotationProcessor","org.projectlombok:lombok:1.18.46")
        add("implementation", "org.slf4j:slf4j-simple:2.0.17")
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