package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.tests.MessageAssert;
import me.beeliebub.tweaks.utils.GeometryUtil;
import me.beeliebub.tweaks.utils.InventoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the world-based authorization path of {@link ClaimSubcommand}.
 * Public claim worlds waive only the purchaseable permission; the normal payment and chunk-limit
 * rules remain in force for non-admin players.
 */
class PublicClaimWorldsTest {

    private static ServerMock server;
    private static Plugin attachmentOwner;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        attachmentOwner = MockBukkit.createMockPlugin("tweaks");
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    @Test
    void permissionlessPlayerCanClaimInListedWorld() {
        PlayerMock claimer = player("PublicListed", "public-listed");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        listCurrentWorld(config, claimer);

        ProtectionManager protection = newProtectionManager();
        RegionSelectionManager selections = new RegionSelectionManager(plugin);
        select(selections, claimer, 0, 0, 2, 1);
        InventoryUtil.addResourceRupees(claimer, 100);

        new ClaimSubcommand().execute(context(plugin, protection, selections), claimer,
                new String[]{"publicclaim"});

        Region region = protection.byName(claimer.getWorld(), "publicclaim");
        assertNotNull(region, "a permissionless player should be able to claim in a listed world");
        assertEquals(54, InventoryUtil.getResourceRupeeBalance(claimer),
                "a public-world claim still charges the normal six-chunk cost");
        assertEquals(46, region.cost());
    }

    @Test
    void unlistedWorldDeniesBeforeNameOrSelectionValidation() {
        PlayerMock claimer = player("Unlisted", "public-unlisted");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of(claimer.getWorld().getKey().asString() + "-other"));
        ProtectionManager protection = newProtectionManager();
        RegionSelectionManager selections = new RegionSelectionManager(plugin);

        new ClaimSubcommand().execute(context(plugin, protection, selections), claimer,
                new String[]{"!invalid"});

        MessageAssert.assertMessageSent(claimer, "permission");
        assertTrue(protection.regions().isEmpty(),
                "authorization must reject the request before name or selection validation");
    }

    @Test
    void consoleClaimGetsPlayersOnlyMessage() {
        Tweaks plugin = pluginWith(new YamlConfiguration());

        new ClaimSubcommand().execute(context(plugin, newProtectionManager(),
                        new RegionSelectionManager(plugin)), server.getConsoleSender(), new String[0]);

        MessageAssert.assertMessageSent((ConsoleCommandSenderMock) server.getConsoleSender(), "players");
    }

    @Test
    void matchingIsCaseInsensitiveButExactAndNamespaced() {
        PlayerMock claimer = player("WorldKey", "public-world-key");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        String worldKey = claimer.getWorld().getKey().asString();

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of(worldKey.toUpperCase(Locale.ROOT)));
        assertTrue(ClaimSubcommand.isPublicClaimWorld(plugin, claimer.getWorld()));

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of(worldKey + "-suffix"));
        assertFalse(ClaimSubcommand.isPublicClaimWorld(plugin, claimer.getWorld()),
                "a listed key must not match by substring");

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of(claimer.getWorld().getName()));
        assertFalse(ClaimSubcommand.isPublicClaimWorld(plugin, claimer.getWorld()),
                "the bare world name must not replace the namespaced world key");
    }

    @Test
    void emptyListDoesNotGrantClaimAuthorization() {
        PlayerMock claimer = player("EmptyList", "public-empty");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH, List.of());
        ProtectionManager protection = newProtectionManager();
        RegionSelectionManager selections = new RegionSelectionManager(plugin);
        select(selections, claimer, 0, 0, 0, 0);
        InventoryUtil.addResourceRupees(claimer, 10);

        new ClaimSubcommand().execute(context(plugin, protection, selections), claimer,
                new String[]{"emptyclaim"});

        MessageAssert.assertMessageSent(claimer, "permission");
        assertEquals(10, InventoryUtil.getResourceRupeeBalance(claimer),
                "an empty allow-list must deny before payment");
        assertFalse(protection.regions().containsKey("emptyclaim"));
    }

    @Test
    void editingTheListTakesEffectWithoutReconstructingCommandOrContext() {
        PlayerMock claimer = player("LiveList", "public-live");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        ProtectionManager protection = newProtectionManager();
        RegionSelectionManager selections = new RegionSelectionManager(plugin);
        RegionCommandContext context = context(plugin, protection, selections);
        ClaimSubcommand command = new ClaimSubcommand();

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH, List.of());
        command.execute(context, claimer, new String[]{"!invalid"});
        MessageAssert.assertMessageSent(claimer, "permission");

        listCurrentWorld(config, claimer);
        select(selections, claimer, 0, 0, 2, 1);
        InventoryUtil.addResourceRupees(claimer, 100);
        command.execute(context, claimer, new String[]{"liveclaim"});

        assertNotNull(protection.byName(claimer.getWorld(), "liveclaim"),
                "a live allow-list edit must affect the existing command and context");
    }

    @Test
    void publicClaimerStillPaysAndRespectsMaxChunks() {
        PlayerMock claimer = player("PublicLimit", "public-limit");
        FileConfiguration config = new YamlConfiguration();
        config.set("max_chunks", 6);
        Tweaks plugin = pluginWith(config);
        listCurrentWorld(config, claimer);
        ProtectionManager protection = newProtectionManager();
        RegionSelectionManager selections = new RegionSelectionManager(plugin);
        select(selections, claimer, 0, 0, 3, 1);
        InventoryUtil.addResourceRupees(claimer, 100);

        new ClaimSubcommand().execute(context(plugin, protection, selections), claimer,
                new String[]{"overlimit"});

        assertEquals(100, InventoryUtil.getResourceRupeeBalance(claimer),
                "the public-world grant must not bypass the chunk limit or charge a failed claim");
        assertFalse(protection.regions().containsKey("overlimit"));

        select(selections, claimer, 0, 0, 2, 1);
        new ClaimSubcommand().execute(context(plugin, protection, selections), claimer,
                new String[]{"withinlimit"});

        Region region = protection.byName(claimer.getWorld(), "withinlimit");
        assertNotNull(region);
        assertEquals(54, InventoryUtil.getResourceRupeeBalance(claimer),
                "a successful public-world claim must deduct its cost");
        assertEquals(46, region.cost());
    }

    @Test
    void adminWithoutPurchaseableCanClaimUnlistedWorldForFreeWithoutLimit() {
        PlayerMock admin = player("PublicAdmin", "public-admin");
        admin.addAttachment(attachmentOwner, Permissions.PROTECTION_ADMIN, true);
        FileConfiguration config = new YamlConfiguration();
        config.set("max_chunks", 0);
        Tweaks plugin = pluginWith(config);
        ProtectionManager protection = newProtectionManager();
        RegionSelectionManager selections = new RegionSelectionManager(plugin);
        select(selections, admin, 0, 0, 2, 1);

        assertFalse(admin.hasPermission(Permissions.PROTECTION_PURCHASEABLE));
        assertTrue(admin.hasPermission(Permissions.PROTECTION_ADMIN));
        assertFalse(ClaimSubcommand.isPublicClaimWorld(plugin, admin.getWorld()));

        new ProtectionCommand(plugin, protection, selections).onCommand(admin, mock(Command.class),
                "region", new String[]{"claim", "adminclaim"});

        Region region = protection.byName(admin.getWorld(), "adminclaim");
        assertNotNull(region, "admin authorization must work independently of the public-world list");
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(admin));
        assertEquals(0, region.cost(), "admin-bypass claims must store zero cost");
    }

    @Test
    void unknownListedWorldKeyIsInert() {
        PlayerMock claimer = player("UnknownWorld", "public-unknown");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of("minecraft:world-that-is-not-loaded"));

        assertFalse(ClaimSubcommand.isPublicClaimWorld(plugin, claimer.getWorld()));
    }

    @Test
    void registryIncludesClaimOnlyWhenTheSenderCanClaimInTheCurrentWorld() {
        PlayerMock claimer = player("VisibleNames", "public-visible");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        RegionCommandContext context = context(plugin, newProtectionManager(),
                new RegionSelectionManager(plugin));
        RegionSubcommandRegistry registry = new RegionSubcommandRegistry();

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH, List.of());
        assertFalse(registry.visibleNames(context, claimer).contains("claim"));

        listCurrentWorld(config, claimer);
        assertTrue(registry.visibleNames(context, claimer).contains("claim"));

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of(claimer.getWorld().getKey().asString() + "-other"));
        assertFalse(registry.visibleNames(context, claimer).contains("claim"));
    }

    @Test
    void rootUsageMirrorsClaimVisibility() {
        PlayerMock claimer = player("VisibleUsage", "public-visible-usage");
        FileConfiguration config = new YamlConfiguration();
        Tweaks plugin = pluginWith(config);
        RegionCommandContext context = context(plugin, newProtectionManager(),
                new RegionSelectionManager(plugin));
        RegionSubcommandRegistry registry = new RegionSubcommandRegistry();

        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH, List.of());
        registry.showRootUsage(context, claimer);
        assertFalse(drainMessagesContaining(claimer, "/region claim"));

        listCurrentWorld(config, claimer);
        registry.showRootUsage(context, claimer);
        assertTrue(drainMessagesContaining(claimer, "/region claim"));
    }

    @Test
    void publicClaimWorldHelperHandlesNullPluginWorldAndConfig() {
        PlayerMock claimer = player("NullSafety", "public-null-safety");
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getConfig()).thenReturn(null);

        assertFalse(ClaimSubcommand.isPublicClaimWorld(null, claimer.getWorld()));
        assertFalse(ClaimSubcommand.isPublicClaimWorld(plugin, null));
        assertFalse(ClaimSubcommand.isPublicClaimWorld(plugin, claimer.getWorld()));
    }

    private PlayerMock player(String playerName, String worldName) {
        World world = server.addSimpleWorld(worldName);
        PlayerMock player = server.addPlayer(playerName);
        player.setOp(false);
        player.teleport(new Location(world, 0, 64, 0));
        return player;
    }

    private Tweaks pluginWith(FileConfiguration config) {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    private static void listCurrentWorld(FileConfiguration config, PlayerMock claimer) {
        config.set(ClaimSubcommand.PUBLIC_CLAIM_WORLDS_PATH,
                List.of(claimer.getWorld().getKey().asString()));
    }

    private static RegionCommandContext context(Tweaks plugin, ProtectionManager protection,
                                                RegionSelectionManager selections) {
        return new RegionCommandContext(plugin, protection, selections);
    }

    private static ProtectionManager newProtectionManager() {
        return new ProtectionManager(mock(Tweaks.class));
    }

    private static void select(RegionSelectionManager selections, PlayerMock player,
                               int chunkX1, int chunkZ1, int chunkX2, int chunkZ2) {
        RegionSelection selection = selections.getOrCreate(player, player.getWorld());
        selection.setPos1(GeometryUtil.chunkKey(chunkX1, chunkZ1));
        selection.setPos2(GeometryUtil.chunkKey(chunkX2, chunkZ2));
    }

    private static boolean drainMessagesContaining(PlayerMock player, String expectedText) {
        boolean found = false;
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            if (PlainTextComponentSerializer.plainText().serialize(message).contains(expectedText)) {
                found = true;
            }
        }
        return found;
    }
}
