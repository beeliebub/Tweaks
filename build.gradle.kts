plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // MockBukkit
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

val mockitoAgent: Configuration by configurations.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.yaml:snakeyaml:2.3")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito requires explicit -javaagent attachment on Java 21+ for inline mocking.
    mockitoAgent("org.mockito:mockito-core:5.14.2") { isTransitive = false }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
        // Byte Buddy lags Java releases; opt in to instrument Java 25 bytecode.
        jvmArgs(
            "-javaagent:${mockitoAgent.asPath}",
            "-Dnet.bytebuddy.experimental=true"
        )
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}