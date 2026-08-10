package me.beeliebub.tweaks.tests.skyblock.command.admin;

import me.beeliebub.tweaks.skyblock.command.admin.AdminArgumentParser;
import me.beeliebub.tweaks.skyblock.command.admin.AdminUsage;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUsageTest {
    @Test
    void helpAndLookupExposeTheCreateThenSetSurface() {
        List<String> help = AdminUsage.help();

        assertTrue(help.stream().anyMatch(line -> line.contains("/isadmin types create <id> [display...]")));
        assertTrue(help.stream().anyMatch(line -> line.contains("/isadmin types set <id> name <value...>")));
        assertTrue(help.stream().anyMatch(line -> line.contains("/isadmin challenges requirement edit")));
        assertTrue(help.stream().anyMatch(line -> line.contains("/isadmin generators output remove")));
        assertTrue(help.stream().anyMatch(line -> line.contains("/isadmin shop delete")));
        assertEquals("types set <id> <field> <value...>", AdminUsage.syntax("types set").orElseThrow());
        assertTrue(AdminUsage.help("types does-not-exist").isEmpty());
    }

    @Test
    void literalSuggestionsFilterTheSharedUsageTree() {
        assertEquals(List.of("templates", "types"), AdminUsage.suggestions(new String[]{"t"}));
        assertTrue(AdminUsage.suggestions(new String[]{"types", "s"}).contains("set"));
        assertTrue(AdminUsage.suggestions(new String[]{"challenges", "requirement", ""}).contains("edit"));
    }

    @Test
    void parserUsesTrackingDomainsAndFiniteNumbers() {
        assertTrue(AdminArgumentParser.parseMaterial("stone").isPresent());
        assertTrue(AdminArgumentParser.parseEntityType("zombie").isPresent());
        assertTrue(AdminArgumentParser.parseTrackKey("collect:stone").isPresent());
        assertTrue(AdminArgumentParser.parseTrackKey("kill:zombie").isPresent());
        assertTrue(AdminArgumentParser.validateIdentifier(TrackCategory.COLLECT, "zombie").isPresent());
        assertTrue(AdminArgumentParser.validateIdentifier(TrackCategory.KILL, Material.STONE.name()).isPresent());
        assertTrue(AdminArgumentParser.parseDouble("1.5").isPresent());
        assertTrue(AdminArgumentParser.parseDouble("NaN").isEmpty());
        assertEquals(EntityType.ZOMBIE, AdminArgumentParser.requireEntityType("minecraft:zombie"));
    }
}
