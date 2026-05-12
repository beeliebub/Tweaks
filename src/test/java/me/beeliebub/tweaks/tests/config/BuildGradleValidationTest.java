package me.beeliebub.tweaks.tests.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildGradleValidationTest {

    private static String buildGradle;
    private static String pluginYml;

    @BeforeAll
    static void load() throws IOException {
        buildGradle = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8);
        pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"), StandardCharsets.UTF_8);
    }

    @Test
    void declaresJavaLibraryAndRunPaperPlugins() {
        assertTrue(buildGradle.contains("id(\"java-library\")"),
                "build.gradle.kts must apply java-library plugin");
        assertTrue(buildGradle.contains("xyz.jpenilla.run-paper"),
                "build.gradle.kts must apply run-paper plugin");
    }

    @Test
    void targetsJava25Toolchain() {
        assertTrue(buildGradle.contains("JavaLanguageVersion.of(25)"),
                "build.gradle.kts must target Java 25 toolchain");
    }

    @Test
    void compileOnlyPaperApiAtRuntimeMatchesPluginYmlApiVersion() {
        assertTrue(buildGradle.contains("io.papermc.paper:paper-api"),
                "must depend on paper-api");
        assertTrue(buildGradle.contains("paper-api:26.1.2"),
                "build.gradle.kts paper-api version should target 26.1.2.x");
        assertTrue(pluginYml.contains("api-version: '26.1.1'") || pluginYml.contains("api-version: '26.1.2'"),
                "plugin.yml api-version should align with paper-api 26.1.x");
    }

    @Test
    void runServerTaskUsesMatchingMinecraftVersion() {
        assertTrue(buildGradle.contains("minecraftVersion(\"26.1.2\")"),
                "runServer task must target Minecraft 26.1.2");
    }

    @Test
    void processResourcesExpandsVersionPlaceholder() {
        assertTrue(buildGradle.contains("processResources"));
        assertTrue(buildGradle.contains("filesMatching(\"plugin.yml\")"),
                "processResources must filter plugin.yml");
        assertTrue(buildGradle.contains("expand("),
                "processResources must call expand() so $version is interpolated");
        assertTrue(pluginYml.contains("version: '$version'"),
                "plugin.yml must use $version placeholder for processResources to fill in");
    }

    @Test
    void usesPaperRepository() {
        assertTrue(buildGradle.contains("repo.papermc.io"),
                "PaperMC repo must be declared");
    }

    @Test
    void testInfrastructureWiredUp() {
        assertTrue(buildGradle.contains("useJUnitPlatform()"),
                "JUnit Platform must be enabled for tests");
        assertTrue(buildGradle.contains("junit-jupiter"),
                "JUnit Jupiter dependency must be declared");
        assertTrue(buildGradle.contains("mockito-core"),
                "Mockito must be declared for mock-based tests");
    }
}
