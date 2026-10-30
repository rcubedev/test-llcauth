import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    idea
    id("org.springframework.boot") version "4.1.2"
    id("org.limelightcreatives.tailwind")
}

group = "org.limelightcreatives"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.dependencyScope("mockitoAgent")
val mockitoAgentClasspath = configurations.resolvable("mockitoAgentClasspath") {
    extendsFrom(mockitoAgent)
}

dependencies {
    // Spring Boot's list of tested library versions, so most dependencies need no version number.
    val springBoot = platform(SpringBootPlugin.BOM_COORDINATES)
    implementation(springBoot)
    developmentOnly(springBoot)
    mockitoAgent(springBoot)

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator") // /actuator/health
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.security:spring-security-crypto") // BCrypt, for login codes
    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // A real PostgreSQL for tests and local runs, from Maven like any other dependency: nothing to install.
    // The binaries come one jar per platform; only this machine's is downloaded (embeddedPostgresBinaries).
    testImplementation("io.zonky.test:embedded-postgres:2.2.2") { exclude(group = "io.zonky.test.postgres") }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

// PostgreSQL binaries for the machine running the build: macOS (Intel or Apple Silicon), Windows (x64),
// Linux (x64 or ARM). Elsewhere there are none, and tests need TEST_DATABASE_URL (see DatabaseTest).
val embeddedPostgresBinaries = providers.systemProperty("os.name")
    .zip(providers.systemProperty("os.arch")) { osName, osArch ->
        val os = osName.lowercase().let {
            when {
                "mac" in it -> "darwin"
                "win" in it -> "windows"
                "linux" in it -> "linux"
                else -> null
            }
        }
        val cpu = when (osArch.lowercase()) {
            "amd64", "x86_64" -> "amd64"
            "aarch64", "arm64" -> "arm64v8"
            else -> null
        }
        val platform = "$os-$cpu"
        val published = setOf("darwin-amd64", "darwin-arm64v8", "windows-amd64", "linux-amd64", "linux-arm64v8")
        listOfNotNull(platform.takeIf { it in published })
            .map { dependencies.create("io.zonky.test.postgres:embedded-postgres-binaries-$it:18.6.0") }
    }
configurations.testRuntimeOnly {
    dependencies.addAllLater(embeddedPostgresBinaries)
}

tasks.test {
    useJUnitPlatform()

    jvmArgumentProviders.add(JavaAgent(files(mockitoAgentClasspath)))
    // Mockito adds a class to the bootstrap classpath, which stops class-data sharing from covering
    // anything but JDK classes and makes the JVM warn about it. Turn sharing off: same speed, no warning.
    jvmArgs("-Xshare:off")
    // Tests start their own PostgreSQL, or use the one in TEST_DATABASE_URL if set. See DatabaseTest.
}

tasks.bootRun {
    // Local settings: plain-HTTP cookies, login codes printed to the console. See application-dev.yml.
    // Needs a PostgreSQL you run yourself; ./gradlew bootTestRun starts one for you (see LocalAuthApp).
    systemProperty("spring.profiles.active", "dev")
}

// One runnable jar, build/libs/auth.jar (what the Dockerfile runs); no extra "-plain" jar.
tasks.bootJar {
    archiveFileName = "auth.jar"
}
tasks.jar {
    enabled = false
}

/** `-javaagent:<jar>` for a test or exec JVM, with the jar tracked as an input. */
class JavaAgent(@get:Classpath val jar: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-javaagent:${jar.singleFile.absolutePath}")
}

// ---------------------------------------------------------------------------
// Tailwind (plugin in build-logic/): src/main/css -> static/css/app.css
// ---------------------------------------------------------------------------

sourceSets.main {
    tailwind {
        // Everything Tailwind scans for class names. Keep in step with the @source lines in app.css.
        content.from("src/main/resources/templates")
    }
}

idea {
    module {
        sourceDirs = sourceDirs + sourceSets.main.get().tailwind.css.srcDirs
    }
}
