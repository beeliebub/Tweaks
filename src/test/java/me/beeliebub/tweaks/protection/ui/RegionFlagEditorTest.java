package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Command-layer coverage for the unified {@code /region flag}/{@code unflag} entry points —
 * moved into the production package (was {@code tests.protection.ProtectionCommandTest},
 * reflecting into {@code ProtectionCommand.runSetFlag}/{@code runRemoveFlag}) since that logic
 * now lives on {@link RegionFlagEditor}, package-private in this same package. Calling
 * {@link RegionFlagEditor#setFlag}/{@link RegionFlagEditor#removeFlag} directly exercises token
 * parsing, material-vs-boolean routing, and target resolution without needing a real Bukkit
 * command dispatch loop or reflection.
 *
 * <p>MockBukkit is initialized once at the class level so {@code Material.matchMaterial} resolves
 * "stone"/"dirt" through the registry — the material-parsing path otherwise rejects unknown
 * materials.
 */
class RegionFlagEditorTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeAll
    static void setUpAll() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private ProtectionManager protection;
    private PermissionManager permissions;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        protection = new ProtectionManager(mock(Tweaks.class));
        permissions = mock(PermissionManager.class);
        sender = mock(CommandSender.class);
        // Console-style sender so isOwnerManagerOrAdmin short-circuits to true
        // without having to mock PROTECTION_ADMIN. Mockito returns false by
        // default for hasPermission(...), and the production code's
        // `!(sender instanceof Player)` branch grants access to console here.
        protection.regions().put("home", new Region(
                "home", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
    }

    private int invokeSetFlag(String name, String flag, String value) {
        return RegionFlagEditor.setFlag(sender, protection, permissions, name, flag, value);
    }

    private int invokeRemoveFlag(String name, String flag, String rawTarget) {
        return RegionFlagEditor.removeFlag(sender, protection, permissions, name, flag, rawTarget);
    }

    @Test
    void setBooleanFlagWithoutTargetWritesDefaultRule() {
        int rc = invokeSetFlag("home", "PVP", "true");

        assertEquals(1, rc, "successful boolean set must return SINGLE_SUCCESS");
        Map<FlagTarget, Boolean> rules = protection.regions().get("home").rulesFor(RegionFlag.PVP);
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.DEFAULT));
    }

    @Test
    void setBooleanFlagWithOwnerTargetWritesOwnerRuleOnly() {
        int rc = invokeSetFlag("home", "PVP", "true owner");

        assertEquals(1, rc);
        Map<FlagTarget, Boolean> rules = protection.regions().get("home").rulesFor(RegionFlag.PVP);
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.OWNER));
        assertNull(rules.get(FlagTarget.DEFAULT),
                "owner-target write must not implicitly populate the DEFAULT rule");
    }

    @Test
    void setMaterialFlagReplacesMaterialList() {
        int rc = invokeSetFlag("home", "ALLOW_BLOCK_BREAK", "stone dirt");

        assertEquals(1, rc);
        Set<Material> materials = protection.regions().get("home")
                .materialsFor(RegionFlag.ALLOW_BLOCK_BREAK);
        assertEquals(Set.of(Material.STONE, Material.DIRT), materials);
    }

    @Test
    void materialFlagRejectsTrueAsMaterialName() {
        int rc = invokeSetFlag("home", "ALLOW_BLOCK_BREAK", "true");

        assertEquals(0, rc,
                "'true' is not a Material, so the material-list flag set must fail");
        assertTrue(protection.regions().get("home")
                        .materialsFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty(),
                "material list must not be mutated when parsing fails");
    }

    @Test
    void booleanFlagRejectsNonBooleanValue() {
        int rc = invokeSetFlag("home", "PVP", "stone");

        assertEquals(0, rc,
                "boolean flag set must reject a value that isn't 'true' or 'false'");
        assertTrue(protection.regions().get("home").rulesFor(RegionFlag.PVP).isEmpty(),
                "rule table must stay empty when the value is invalid");
    }

    @Test
    void unflagMaterialFlagClearsEntireList() {
        protection.setMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.STONE, Material.DIRT));

        int rc = invokeRemoveFlag("home", "ALLOW_BLOCK_BREAK", null);

        assertEquals(1, rc);
        assertTrue(protection.regions().get("home")
                        .materialsFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty(),
                "unflagging a material flag must drop every entry, not just one target");
    }

    @Test
    void unflagBooleanFlagRemovesOnlyTheTargetedRule() {
        protection.setFlag("home", RegionFlag.PVP, FlagTarget.OWNER, true);
        protection.setFlag("home", RegionFlag.PVP, FlagTarget.DEFAULT, false);

        int rc = invokeRemoveFlag("home", "PVP", "owner");

        assertEquals(1, rc);
        Map<FlagTarget, Boolean> rules = protection.regions().get("home").rulesFor(RegionFlag.PVP);
        assertNull(rules.get(FlagTarget.OWNER),
                "the owner-target rule must be removed");
        assertEquals(Boolean.FALSE, rules.get(FlagTarget.DEFAULT),
                "unrelated targets on the same flag must survive");
    }
}
