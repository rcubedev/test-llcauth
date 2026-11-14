import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    idea
    id("org.springframework.boot") version "4.1.2" // todo(fut): version catalog
    id("org.graalvm.buildtools.native") version "1.1.18"
    id("org.limelightcreatives.tailwind")
    id("gg.jte.gradle") version "3.2.5"
}

group = "org.limelightcreatives"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tailwind {
    version = providers.gradleProperty("tailwind.version")
}

jte {
    generate()
    jteExtension("gg.jte.nativeimage.NativeResourcesExtension")
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.dependencyScope("mockitoAgent")
val mockitoAgentClasspath = configurations.resolvable("mockitoAgentClasspath") {
    extendsFrom(mockitoAgent)
}

dependencies {
    val springBoot = platform(SpringBootPlugin.BOM_COORDINATES)
    implementation(springBoot)
    developmentOnly(springBoot)
    mockitoAgent(springBoot)

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("gg.jte:jte:3.2.5")
    implementation("gg.jte:jte-spring-boot-starter-4:3.2.5")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    jteGenerate("gg.jte:jte-native-resources:3.2.5")

    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("io.zonky.test:embedded-postgres:2.2.2") { exclude(group = "io.zonky.test.postgres") }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

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

sourceSets.main {
    tailwind {
        content.from("src/main/jte", "src/main/resources/static/js")
    }
}

tasks.test {
    useJUnitPlatform()

    jvmArgumentProviders.add(JavaAgent(files(mockitoAgentClasspath)))
    jvmArgs("-Xshare:off")
}

tasks.bootRun {
    systemProperty("spring.profiles.active", "dev")
}

graalvmNative {
    binaries.named("main") {
        imageName = "auth"
        buildArgs.add(providers.gradleProperty("native.optimization").map { "-O$it" }.orElse("-Ob"))
    }
}

tasks.bootJar {
    archiveFileName = "auth.jar"
}

/**
 * Adds the given [jar] as a java agent using the `-javaagent:<jar>` JVM argument
 *
 * @property jar the JAR to use as a Java agent
 */
class JavaAgent(@get:Classpath val jar: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-javaagent:${jar.singleFile.absolutePath}")
}
