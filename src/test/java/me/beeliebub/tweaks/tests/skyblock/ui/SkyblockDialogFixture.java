package me.beeliebub.tweaks.tests.skyblock.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.SkyblockSetupChecker;
import me.beeliebub.tweaks.skyblock.SkyblockSetupStatus;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeCategory;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminServices;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.economy.ShopService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.island.IslandBiomeService;
import me.beeliebub.tweaks.skyblock.island.IslandCreationService;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandHomes;
import me.beeliebub.tweaks.skyblock.island.IslandInviteManager;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandRegionBridge;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.island.IslandStore;
import me.beeliebub.tweaks.skyblock.island.IslandVisits;
import me.beeliebub.tweaks.skyblock.island.PendingWipeStore;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.skyblock.island.IslandDeletionService;
import me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import me.beeliebub.tweaks.protection.ui.RegionSelection;
import me.beeliebub.tweaks.skyblock.tracking.SkyblockPdc;
import me.beeliebub.tweaks.skyblock.template.TemplateStore;
import me.beeliebub.tweaks.skyblock.template.IslandTemplate;
import me.beeliebub.tweaks.skyblock.tracking.IslandTracker;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import me.beeliebub.tweaks.skyblock.ui.admin.AdminItemEditor;
import me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext;
import me.beeliebub.tweaks.skyblock.ui.admin.SkyblockAdminScreens;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared MockBukkit runtime for the Skyblock administrator Dialog tests. */
public final class SkyblockDialogFixture implements AutoCloseable {
    public final ServerMock server;
    public final JavaPlugin plugin;
    public final World skyblockWorld;
    public final World wrongWorld;
    public final PlayerMock admin;
    public final SkyblockBootstrap.Runtime runtime;
    public final AdminScreenContext context;
    public final SkyblockAdminScreens screens;

    public final TypeRegistry typeRegistry = mock(TypeRegistry.class);
    public final ChallengeRegistry challengeRegistry = mock(ChallengeRegistry.class);
    public final GeneratorRegistry generatorRegistry = mock(GeneratorRegistry.class);
    public final ShopCatalog shopCatalog = mock(ShopCatalog.class);
    public final TemplateStore templateStore = mock(TemplateStore.class);
    public final IslandManager islandManager = mock(IslandManager.class);
    public final SkyblockConfig config = mock(SkyblockConfig.class);
    public final SkyblockSpawn spawn = mock(SkyblockSpawn.class);
    public final ChallengeService challengeService = mock(ChallengeService.class);
    public final ShopService shopService = mock(ShopService.class);
    public final RegionSelectionManager selectionManager = mock(RegionSelectionManager.class);
    public final SkyblockSetupChecker checker = mock(SkyblockSetupChecker.class);

    public final IslandDifficulty normal = new IslandDifficulty("normal", "Normal", 0);
    public final IslandType defaultType = new IslandType("default", "Default",
            java.util.Set.of("normal"), "", List.of(), "PLAINS", java.util.Set.of());
    public final ChallengeCategory category = new ChallengeCategory("general", "General", 0);
    public final Challenge challenge = new Challenge("starter", "general", "Starter", "Starter challenge",
            List.of(), List.of(), List.of(), List.of(), java.util.Set.of());
    public final GeneratorTier defaultGenerator = new GeneratorTier("default", "Default",
            Map.of(Material.COBBLESTONE, 1.0d));
    public final ShopCatalog.Entry shopEntry = new ShopCatalog.Entry(Material.COBBLESTONE, "general", 1.0d, 0.5d);
    public final IslandTemplate template = new IslandTemplate("starter-template", 1, 1, 1,
            List.of("minecraft:stone"), new int[]{0}, Map.of(), new Island.SpawnOffset(0, 0, 0));
    public final Island island;
    public final RegionSelection selection;

    public SkyblockDialogFixture() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        skyblockWorld = server.addSimpleWorld("skyblock");
        wrongWorld = server.addSimpleWorld("wrong");
        admin = server.addPlayer("SkyblockAdmin");
        admin.addAttachment(plugin, Permissions.ADMIN_SKYBLOCK, true);
        admin.teleport(skyblockWorld.getSpawnLocation());
        island = Island.create(admin.getUniqueId(), 0, IslandSize.SMALL);
        selection = new RegionSelection(skyblockWorld);
        selection.setPos1(0L);
        selection.setPos2(0L);

        when(config.worldKey()).thenReturn(skyblockWorld.getKey().asString());
        when(config.world()).thenReturn(skyblockWorld.getKey().asString());
        when(config.defaultType()).thenReturn(defaultType.id());
        when(config.defaultDifficulty()).thenReturn(normal.id());
        when(config.templateMaxHeight()).thenReturn(256);
        when(config.templateMaxBlocks()).thenReturn(1_000_000);
        when(typeRegistry.difficulties()).thenReturn(List.of(normal));
        when(typeRegistry.types()).thenReturn(List.of(defaultType));
        when(typeRegistry.typesFor(anyString())).thenReturn(List.of(defaultType));
        when(typeRegistry.difficulty(anyString())).thenReturn(Optional.of(normal));
        when(typeRegistry.type(anyString())).thenReturn(Optional.of(defaultType));
        when(challengeRegistry.categories()).thenReturn(List.of(category));
        when(challengeRegistry.challenges()).thenReturn(List.of(challenge));
        when(challengeRegistry.challengesIn(anyString())).thenReturn(List.of(challenge));
        when(challengeRegistry.category(anyString())).thenReturn(Optional.of(category));
        when(challengeRegistry.challenge(anyString())).thenReturn(Optional.of(challenge));
        when(challengeRegistry.hasInvalidDefinitions()).thenReturn(false);
        when(generatorRegistry.tiers()).thenReturn(List.of(defaultGenerator));
        when(generatorRegistry.tier(anyString())).thenReturn(Optional.of(defaultGenerator));
        when(shopCatalog.entries()).thenReturn(List.of(shopEntry));
        when(shopCatalog.entry(Material.COBBLESTONE)).thenReturn(Optional.of(shopEntry));
        when(templateStore.ids()).thenReturn(List.of(template.id()));
        when(templateStore.loadAsync(anyString())).thenReturn(CompletableFuture.completedFuture(template));
        when(islandManager.all()).thenReturn(List.of(island));
        when(islandManager.byId(anyString())).thenReturn(Optional.of(island));
        when(islandManager.spawnLocation(any(Island.class), any(World.class)))
                .thenReturn(new Location(skyblockWorld, 100, 80, 100));
        when(spawn.isRecorded()).thenReturn(false);
        when(spawn.data()).thenReturn(Optional.empty());
        when(challengeService.canUse(any(Island.class), any(PlayerMock.class))).thenReturn(false);
        when(challengeService.readiness(any(Island.class), anyString(), any(PlayerMock.class)))
                .thenReturn(new ChallengeService.Readiness(false, "not ready", List.of()));
        when(selectionManager.get(admin.getUniqueId())).thenReturn(selection);

        runtime = new SkyblockBootstrap.Runtime(config, skyblockWorld, "skyblock",
                mock(IslandStore.class), mock(PendingWipeStore.class),
                mock(IslandGrid.class), islandManager, mock(IslandRegionBridge.class), mock(IslandVisits.class),
                mock(IslandInviteManager.class), mock(IslandHomes.class), spawn, mock(IslandDeletionService.class),
                mock(BukkitTask.class), mock(StorageManager.class), selectionManager,
                typeRegistry, templateStore, challengeRegistry, challengeService, generatorRegistry,
                mock(SkyblockEconomy.class), shopCatalog, shopService, mock(SkyblockPdc.class),
                mock(IslandTracker.class), mock(IslandCreationService.class), mock(IslandBiomeService.class), null);

        when(checker.check()).thenReturn(new SkyblockSetupStatus(List.of()));
        SkyblockAdminServices services = SkyblockAdminServices.create(plugin, runtime);
        AdminItemEditor editor = new AdminItemEditor(plugin);
        context = new AdminScreenContext(plugin, runtime, services, checker, editor);
        screens = new SkyblockAdminScreens(context);
    }

    public PlayerMock noPermissionPlayer() {
        PlayerMock player = server.addPlayer("NoPermission");
        player.addAttachment(plugin, Permissions.ADMIN_SKYBLOCK, false);
        player.teleport(skyblockWorld.getSpawnLocation());
        return player;
    }

    public PlayerMock wrongWorldPlayer() {
        PlayerMock player = server.addPlayer("WrongWorld");
        player.addAttachment(plugin, Permissions.ADMIN_SKYBLOCK, true);
        player.teleport(wrongWorld.getSpawnLocation());
        return player;
    }

    public void pump() {
        server.getScheduler().performOneTick();
    }

    @Override
    public void close() {
        try {
            screens.shutdown();
        } finally {
            MockBukkit.unmock();
        }
    }
}
