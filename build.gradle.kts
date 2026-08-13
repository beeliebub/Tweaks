plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://nexus.scarsz.me/content/groups/public/")
    // MockBukkit
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    // MockBukkit v26.2 (unreleased upstream; built locally from MockBukkit/MockBukkit#1592)
    mavenLocal()
}

val mockitoAgent: Configuration by configurations.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.discordsrv:discordsrv:1.30.5")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.yaml:snakeyaml:2.3")
    // TODO(26.2-upgrade): Replace this locally-published build with mockbukkit-v26.2:x.x.x
    //   once a confirmed artifact is published to Maven Central or the Sonatype snapshot repo.
    //   As of 2026-07-26, 26.2 support is only an unmerged draft branch upstream
    //   (MockBukkit/MockBukkit upgrade/v26.2, PR #1592) with no version tag yet, so it's
    //   built from source and installed via `./gradlew publishToMavenLocal` in a checkout
    //   of that branch (cloned to ../MockBukkit), then consumed here via mavenLocal().
    //   Check: https://s01.oss.sonatype.org/content/repositories/snapshots/org/mockbukkit/mockbukkit/
    //   and: https://search.maven.org/search?q=g:org.mockbukkit.mockbukkit
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:dev-f4a02d43")
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
        minecraftVersion("26.2")
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
