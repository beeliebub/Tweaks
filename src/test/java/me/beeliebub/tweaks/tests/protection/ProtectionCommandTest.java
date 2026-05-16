package me.beeliebub.tweaks.tests.protection;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.FlagTarget;
import me.beeliebub.tweaks.protection.ProtectionCommand;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedConstruction;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

// Command-layer coverage for the unified /region flag and /region unflag entry
// points. These reach into the private static executor methods via reflection
// (the methods are bound to Paper's Brigadier dispatcher in production) and
// drive them with mocked CommandContext + CommandSender so we can exercise
// token parsing, material-vs-boolean routing, and target resolution without
// spinning up a real CommandDispatcher.
//
// MockBukkit is initialized once at the class level so Material.matchMaterial
// resolves "stone"/"dirt" through the registry — the parseMaterials path in
// ProtectionCommand otherwise rejects unknown materials.
class ProtectionCommandTest {

    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static Method runSetFlag;
    private static Method runRemoveFlag;

    @BeforeAll
    static void setUpAll() throws NoSuchMethodException {
        MockBukkit.mock();
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
        runSetFlag = ProtectionCommand.class.getDeclaredMethod(
                "runSetFlag", CommandContext.class, ProtectionManager.class, PermissionManager.class);
        runSetFlag.setAccessible(true);
        runRemoveFlag = ProtectionCommand.class.getDeclaredMethod(
                "runRemoveFlag", CommandContext.class, ProtectionManager.class,
                PermissionManager.class, String.class);
        runRemoveFlag.setAccessible(true);
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
        protection.regions().put("home", new Region(
                "home", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
    }

    private CommandContext<CommandSourceStack> ctx(String name, String flag, String value) {
        @SuppressWarnings("unchecked")
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(ctx.getSource()).thenReturn(source);
        when(source.getSender()).thenReturn(sender);
        if (name != null) when(ctx.getArgument("name", String.class)).thenReturn(name);
        if (flag != null) when(ctx.getArgument("flag", String.class)).thenReturn(flag);
        if (value != null) when(ctx.getArgument("value", String.class)).thenReturn(value);
        return ctx;
    }

    private int invokeSetFlag(CommandContext<CommandSourceStack> ctx) throws Exception {
        return (int) runSetFlag.invoke(null, ctx, protection, permissions);
    }

    private int invokeRemoveFlag(CommandContext<CommandSourceStack> ctx, String rawTarget) throws Exception {
        return (int) runRemoveFlag.invoke(null, ctx, protection, permissions, rawTarget);
    }

    @Test
    void setBooleanFlagWithoutTargetWritesDefaultRule() throws Exception {
        int rc = invokeSetFlag(ctx("home", "PVP", "true"));

        assertEquals(1, rc, "successful boolean set must return SINGLE_SUCCESS");
        Map<FlagTarget, Boolean> rules = protection.regions().get("home").rulesFor(RegionFlag.PVP);
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.DEFAULT));
    }

    @Test
    void setBooleanFlagWithOwnerTargetWritesOwnerRuleOnly() throws Exception {
        int rc = invokeSetFlag(ctx("home", "PVP", "true owner"));

        assertEquals(1, rc);
        Map<FlagTarget, Boolean> rules = protection.regions().get("home").rulesFor(RegionFlag.PVP);
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.OWNER));
        assertNull(rules.get(FlagTarget.DEFAULT),
                "owner-target write must not implicitly populate the DEFAULT rule");
    }

    @Test
    void setMaterialFlagReplacesMaterialList() throws Exception {
        int rc = invokeSetFlag(ctx("home", "ALLOW_BLOCK_BREAK", "stone dirt"));

        assertEquals(1, rc);
        Set<Material> materials = protection.regions().get("home")
                .materialsFor(RegionFlag.ALLOW_BLOCK_BREAK);
        assertEquals(Set.of(Material.STONE, Material.DIRT), materials);
    }

    @Test
    void materialFlagRejectsTrueAsMaterialName() throws Exception {
        int rc = invokeSetFlag(ctx("home", "ALLOW_BLOCK_BREAK", "true"));

        assertEquals(0, rc,
                "'true' is not a Material, so the material-list flag set must fail");
        assertTrue(protection.regions().get("home")
                        .materialsFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty(),
                "material list must not be mutated when parsing fails");
    }

    @Test
    void booleanFlagRejectsNonBooleanValue() throws Exception {
        int rc = invokeSetFlag(ctx("home", "PVP", "stone"));

        assertEquals(0, rc,
                "boolean flag set must reject a value that isn't 'true' or 'false'");
        assertTrue(protection.regions().get("home").rulesFor(RegionFlag.PVP).isEmpty(),
                "rule table must stay empty when the value is invalid");
    }

    @Test
    void unflagMaterialFlagClearsEntireList() throws Exception {
        protection.setMaterials("home", RegionFlag.ALLOW_BLOCK_BREAK,
                Set.of(Material.STONE, Material.DIRT));

        int rc = invokeRemoveFlag(ctx("home", "ALLOW_BLOCK_BREAK", null), null);

        assertEquals(1, rc);
        assertTrue(protection.regions().get("home")
                        .materialsFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty(),
                "unflagging a material flag must drop every entry, not just one target");
    }

    @Test
    void unflagBooleanFlagRemovesOnlyTheTargetedRule() throws Exception {
        protection.setFlag("home", RegionFlag.PVP, FlagTarget.OWNER, true);
        protection.setFlag("home", RegionFlag.PVP, FlagTarget.DEFAULT, false);

        int rc = invokeRemoveFlag(ctx("home", "PVP", null), "owner");

        assertEquals(1, rc);
        Map<FlagTarget, Boolean> rules = protection.regions().get("home").rulesFor(RegionFlag.PVP);
        assertNull(rules.get(FlagTarget.OWNER),
                "the owner-target rule must be removed");
        assertEquals(Boolean.FALSE, rules.get(FlagTarget.DEFAULT),
                "unrelated targets on the same flag must survive");
    }
}
