package me.beeliebub.tweaks.tests.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PluginYmlValidationTest {

    private static Map<String, Object> root;
    private static String tweaksSource;

    @BeforeAll
    static void load() throws IOException {
        try (var in = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
            root = new Yaml().load(in);
        }
        tweaksSource = Files.readString(
                Path.of("src/main/java/me/beeliebub/tweaks/Tweaks.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    void hasRequiredTopLevelFields() {
        assertEquals("Tweaks", root.get("name"));
        assertEquals("me.beeliebub.tweaks.Tweaks", root.get("main"));
        assertNotNull(root.get("api-version"), "api-version must be set");
        assertNotNull(root.get("version"), "version placeholder must be present");
        assertEquals("POSTWORLD", root.get("load"));
    }

    @Test
    void apiVersionMatchesPaperMajor() {
        String apiVersion = String.valueOf(root.get("api-version"));
        assertTrue(apiVersion.startsWith("26."),
                "api-version should target Paper 26.x, was " + apiVersion);
    }

    @Test
    void everyCommandHasADescription() {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> commands = (Map<String, Map<String, Object>>) root.get("commands");
        assertNotNull(commands, "commands block must exist");
        assertFalse(commands.isEmpty(), "must declare at least one command");

        for (var entry : commands.entrySet()) {
            Map<String, Object> spec = entry.getValue();
            assertNotNull(spec, "command " + entry.getKey() + " has null spec");
            Object desc = spec.get("description");
            assertNotNull(desc, "command " + entry.getKey() + " missing description");
            assertFalse(String.valueOf(desc).isBlank(),
                    "command " + entry.getKey() + " has blank description");
        }
    }

    @Test
    void everyGetCommandCallInTweaksHasYmlEntry() {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> commands = (Map<String, Map<String, Object>>) root.get("commands");
        Set<String> declared = new HashSet<>(commands.keySet());

        Pattern p = Pattern.compile("getCommand\\(\"([^\"]+)\"\\)");
        Matcher m = p.matcher(tweaksSource);
        Set<String> referenced = new HashSet<>();
        while (m.find()) referenced.add(m.group(1));

        assertFalse(referenced.isEmpty(), "expected to find getCommand() calls");
        Set<String> missing = new HashSet<>(referenced);
        missing.removeAll(declared);
        assertTrue(missing.isEmpty(),
                "Commands referenced in Tweaks.java but missing from plugin.yml: " + missing);
    }

    @Test
    void aliasesDoNotCollideWithOtherCommands() {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> commands = (Map<String, Map<String, Object>>) root.get("commands");
        Set<String> commandNames = commands.keySet();

        for (var entry : commands.entrySet()) {
            Object aliasesObj = entry.getValue().get("aliases");
            if (aliasesObj == null) continue;
            assertTrue(aliasesObj instanceof Iterable,
                    "aliases for " + entry.getKey() + " must be a list");
            for (Object alias : (Iterable<?>) aliasesObj) {
                String a = String.valueOf(alias);
                assertFalse(commandNames.contains(a),
                        "alias '" + a + "' on /" + entry.getKey() + " collides with another command name");
            }
        }
    }

    @Test
    void commandsAreYamlMapNotList() {
        Object commands = root.get("commands");
        assertTrue(commands instanceof LinkedHashMap || commands instanceof Map,
                "commands must be a YAML mapping, was " + commands.getClass());
    }
}
