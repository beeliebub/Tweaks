package me.beeliebub.tweaks.skyblock;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import me.beeliebub.tweaks.skyblock.command.IslandAdminCommand;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminServices;
import me.beeliebub.tweaks.skyblock.command.IslandCommand;
import me.beeliebub.tweaks.skyblock.command.SkyblockCommand;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandDeletionService;
import me.beeliebub.tweaks.skyblock.island.IslandDeletionStore;
import me.beeliebub.tweaks.skyblock.island.IslandCreationService;
import me.beeliebub.tweaks.skyblock.island.IslandBiomeService;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandHomes;
import me.beeliebub.tweaks.skyblock.island.IslandInviteManager;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandRegionBridge;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.island.IslandStore;
import me.beeliebub.tweaks.skyblock.island.IslandVisits;
import me.beeliebub.tweaks.skyblock.island.PendingWipeStore;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.skyblock.template.TemplateStore;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.generator.CobbleGeneratorListener;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.economy.PayCommand;
import me.beeliebub.tweaks.skyblock.economy.SellCommand;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.economy.ShopService;
import me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy;
import me.beeliebub.tweaks.skyblock.listener.ContainmentListener;
import me.beeliebub.tweaks.skyblock.listener.ChallengeProgressListener;
import me.beeliebub.tweaks.skyblock.listener.SkyblockWorldListener;
import me.beeliebub.tweaks.skyblock.listener.WipeApplyListener;
import me.beeliebub.tweaks.skyblock.tracking.IslandTracker;
import me.beeliebub.tweaks.skyblock.tracking.SkyblockPdc;
import me.beeliebub.tweaks.skyblock.tracking.TaintRetirement;
import me.beeliebub.tweaks.skyblock.tracking.TrackingActionListener;
import me.beeliebub.tweaks.skyblock.tracking.TrackingGatherListener;
import me.beeliebub.tweaks.skyblock.tracking.TrackingInteractionListener;
import me.beeliebub.tweaks.skyblock.ui.admin.AdminItemEditor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/** Boot gate and lifecycle owner for Skyblock's persistence runtime. */
public final class SkyblockBootstrap {

    private static final String REMEDIATION_PROFILE = "skyblock";
    private static final String REMEDIATION_LABEL = "Skyblock";
    private static final String REMEDIATION_COLOR = "AQUA";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;

    private SkyblockBootstrap() {
    }

    /** Immutable runtime publication after the world/profile gate has passed. */
    public record Runtime(
            SkyblockConfig config,
            World world,
            String profile,
            IslandStore islandStore,
            PendingWipeStore pendingWipeStore,
            IslandGrid grid,
            IslandManager islandManager,
            IslandRegionBridge regionBridge,
            IslandVisits visits,
            IslandInviteManager invites,
            IslandHomes homes,
            SkyblockSpawn spawn,
            IslandDeletionService deletion,
            BukkitTask deletionTask,
            StorageManager storageManager,
            RegionSelectionManager regionSelectionManager,
            TypeRegistry typeRegistry,
            TemplateStore templateStore,
            ChallengeRegistry challengeRegistry,
            ChallengeService challengeService,
            GeneratorRegistry generatorRegistry,
            SkyblockEconomy economy,
            ShopCatalog shopCatalog,
            ShopService shopService,
            SkyblockPdc pdc,
            IslandTracker tracker,
            IslandCreationService creationService,
            IslandBiomeService biomeService,
            AdminItemEditor adminItemEditor
    ) {
        public Runtime {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(islandStore, "islandStore");
            Objects.requireNonNull(pendingWipeStore, "pendingWipeStore");
        }

        /** Compatibility constructor for gate-focused tests that only need frozen core state. */
        public Runtime(SkyblockConfig config, World world, String profile,
                       IslandStore islandStore, PendingWipeStore pendingWipeStore) {
            this(config, world, profile, islandStore, pendingWipeStore, null, null, null, null, null, null,
                    null, null, null, null, null);
        }

        public Runtime(SkyblockConfig config, World world, String profile,
                       IslandStore islandStore, PendingWipeStore pendingWipeStore, IslandGrid grid,
                       IslandManager islandManager, IslandRegionBridge regionBridge, IslandVisits visits,
                       IslandInviteManager invites, IslandHomes homes, SkyblockSpawn spawn,
                       IslandDeletionService deletion, BukkitTask deletionTask, StorageManager storageManager,
                       RegionSelectionManager regionSelectionManager) {
            this(config, world, profile, islandStore, pendingWipeStore, grid, islandManager, regionBridge,
                    visits, invites, homes, spawn, deletion, deletionTask, storageManager, regionSelectionManager,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public IslandDeletionService.BeginResult beginDeletion(Island island) {
            if (deletion == null) return IslandDeletionService.BeginResult.PERSISTENCE_FAILED;
            return deletion.begin(island);
        }

        public void wipeNow(UUID player) {
            if (storageManager != null) storageManager.clearProfileData(player, profile);
        }

        public me.beeliebub.tweaks.protection.ui.RegionSelection regionSelection(UUID player) {
            return regionSelectionManager == null ? null : regionSelectionManager.get(player);
        }
    }

    public static void register(Tweaks plugin, Services services) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(services, "services");

        SkyblockConfig config = new SkyblockConfig(plugin);
        for (String resource : List.of("skyblock/types.yml", "skyblock/challenges.yml",
                "skyblock/generators.yml", "skyblock/shop.yml")) {
            if (plugin.getResource(resource) != null) plugin.saveResource(resource, false);
        }
        String worldKey = config.world();
        World world = findLoadedWorld(plugin, worldKey);
        if (world == null) {
            failGate(plugin, worldKey, "the configured world is not loaded");
            return;
        }

        WorldProfileTable profileTable = services.worldProfileTable();
        if (profileTable.entry(worldKey).isEmpty()) {
            failGate(plugin, worldKey, "no exact world-profile mapping exists for the configured world");
            return;
        }
        String profile = profileTable.profileFor(worldKey);
        if (profile.equals(profileTable.fallback().profile())) {
            failGate(plugin, worldKey, "the world resolves to the fallback profile '" + profile + "'");
            return;
        }
        if (!isExclusiveProfile(profileTable, worldKey, profile)) {
            failGate(plugin, worldKey, "profile '" + profile + "' is also resolved by another configured world");
            return;
        }

        StorageManager storage = services.storageManager();
        ProtectionManager protection = services.protectionManager();
        RegionSelectionManager selections = services.regionSelectionManager();
        IslandGrid grid = new IslandGrid(config.gridPitchChunks(), config.maxSlotIndex());
        IslandStore islandStore = new IslandStore(plugin, config);
        PendingWipeStore pendingWipeStore = new PendingWipeStore(plugin);
        IslandManager islandManager = new IslandManager(config, grid, islandStore);
        IslandRegionBridge regionBridge = new IslandRegionBridge(protection, grid);
        IslandVisits visits = new IslandVisits();
        IslandInviteManager invites = new IslandInviteManager(Duration.ofSeconds(config.inviteTimeoutSeconds()));
        IslandHomes homes = new IslandHomes(islandManager, config.maxHomes(),
                config.maxHomes() + config.maxHomesPurchasable());
        SkyblockEconomy economy = new SkyblockEconomy(plugin, islandManager);
        economy.preload();
        SkyblockSpawn spawn = new SkyblockSpawn(plugin, spawnWorld -> {
            Region region = regionBridge.spawnRegion(spawnWorld);
            if (region == null || region.bounds() == null) return null;
            return new IslandGrid.ChunkBounds(region.bounds().minChunkX(), region.bounds().minChunkZ(),
                    region.bounds().maxChunkX(), region.bounds().maxChunkZ());
        });
        IslandDeletionStore deletionStore = new IslandDeletionStore(plugin);
        IslandDeletionService deletion = createDeletionService(plugin, world, grid, config,
                deletionStore, islandManager, regionBridge, visits, spawn, pendingWipeStore, storage,
                profile, economy);
        TypeRegistry typeRegistry = new TypeRegistry(plugin);
        typeRegistry.load();
        TemplateStore templateStore = new TemplateStore(plugin);
        ChallengeRegistry challengeRegistry = new ChallengeRegistry(plugin, typeRegistry);
        challengeRegistry.load();
        typeRegistry.difficulties().forEach(difficulty ->
                challengeRegistry.setMultiplier(difficulty.id(), difficulty.multiplier()));
        SkyblockPdc pdc = new SkyblockPdc();
        GeneratorRegistry generatorRegistry = new GeneratorRegistry(plugin);
        generatorRegistry.load();
        ShopCatalog shopCatalog = new ShopCatalog(plugin);
        shopCatalog.load();
        ChallengeService challengeService = new ChallengeService(challengeRegistry, islandManager, regionBridge,
                pdc, (island, amount) -> economy.credit(island, amount), changed -> { },
                (island, id) -> island.memberSet().stream().map(plugin.getServer()::getPlayer)
                        .filter(Objects::nonNull).forEach(player -> player.sendMessage(Messages.SKYBLOCK.challengeReady(id))));
        ShopService shopService = new ShopService(shopCatalog, economy, islandManager, challengeRegistry,
                pdc, worldKey);
        IslandBiomeService biomeService = new IslandBiomeService(plugin, islandManager, economy, config, world, 2);
        IslandCreationService creationService = new IslandCreationService(plugin, config, islandManager,
                regionBridge, typeRegistry, templateStore, world);
        AdminItemEditor adminItemEditor = new AdminItemEditor(plugin);
        IslandTracker tracker = new IslandTracker(challengeRegistry, pdc, islandManager,
                (island, key, value) -> challengeService.onCounterChanged(island));
        ShopService finalShopService = shopService;
        deletion.resumePending();
        islandStore.start();

        BukkitTask deletionTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, (Runnable) deletion::tick, 1L, 1L);
        Runtime runtime = new Runtime(config, world, profile, islandStore, pendingWipeStore, grid,
                islandManager, regionBridge, visits, invites, homes, spawn, deletion, deletionTask,
                storage, selections, typeRegistry, templateStore, challengeRegistry, challengeService,
                generatorRegistry, economy, shopCatalog, finalShopService, pdc, tracker, creationService, biomeService,
                adminItemEditor);
        services.setSkyblockManager(islandManager);
        services.setSkyblockEconomy(economy);
        services.setSkyblockRuntime(runtime);
        reconcileSpawnRegion(plugin, runtime);
        if (services.tabManagerOrNull() != null) {
            services.tabManagerOrNull().setSkyblockBalanceProvider(worldKey, player -> islandManager.forPlayer(player.getUniqueId())
                    .map(island -> java.util.OptionalDouble.of(economy.balance(island)))
                    .orElse(java.util.OptionalDouble.empty()));
        }
        if (services.teleportCommandManagerOrNull() != null) {
            services.teleportCommandManagerOrNull().setSkyblockGate(new me.beeliebub.tweaks.teleport.SkyblockTeleportGate() {
                @Override
                public boolean allowed(Player player, Location destination) {
                    if (player == null || destination == null) return false;
                    if (player.hasPermission(me.beeliebub.tweaks.permissions.Permissions.SKYBLOCK_BYPASS)
                            || player.hasPermission(me.beeliebub.tweaks.permissions.Permissions.ADMIN_SKYBLOCK)) return true;
                    boolean sourceSkyblock = player.getWorld() == world;
                    boolean destinationSkyblock = destination.getWorld() == world;
                    if (!sourceSkyblock && !destinationSkyblock) return true;
                    if (sourceSkyblock && !destinationSkyblock) return false;
                    return spawn.contains(destination.getChunk()) || islandManager.islandAt(destination).isPresent();
                }

                @Override
                public boolean allowedCommand(Player player, String label, String[] args) {
                    if (player == null || player.hasPermission(me.beeliebub.tweaks.permissions.Permissions.SKYBLOCK_BYPASS)
                            || player.hasPermission(me.beeliebub.tweaks.permissions.Permissions.ADMIN_SKYBLOCK)) return true;
                    if (!Set.of("tpa", "tpahere", "tpaccept", "back", "warp").contains(label.toLowerCase())) return true;
                    return player.getWorld() != world || islandManager.forPlayer(player.getUniqueId()).isPresent()
                            || spawn.contains(player.getLocation().getChunk());
                }
            });
        }
        registerCommands(plugin, runtime);
        registerListeners(plugin, runtime);
        plugin.getLogger().info("Skyblock enabled for world '" + worldKey
                + "' with frozen profile '" + profile + "'.");
    }

    private static IslandDeletionService createDeletionService(
            Tweaks plugin, World world, IslandGrid grid, SkyblockConfig config,
            IslandDeletionStore deletionStore, IslandManager islands, IslandRegionBridge regions,
            IslandVisits visits, SkyblockSpawn spawn, PendingWipeStore pendingWipes,
            StorageManager storage, String profile, SkyblockEconomy economy) {
        IslandDeletionService.PlayerEjector ejector = new IslandDeletionService.PlayerEjector() {
            @Override
            public java.util.Collection<Player> playersInside(World targetWorld, IslandGrid.ChunkBounds bounds) {
                return targetWorld.getPlayers().stream()
                        .filter(player -> bounds.contains(player.getLocation().getBlockX() >> 4,
                                player.getLocation().getBlockZ() >> 4))
                        .toList();
            }

            @Override
            public boolean eject(Player player, Location destination) {
                return player.teleport(destination);
            }
        };
        IslandDeletionService.RegionUnclaimer unclaimer = islandId -> {
            Island island = islands.byId(islandId).orElse(null);
            if (island == null) return true;
            ProtectionManager.UnclaimResult result = regions.delete(island, world).result();
            return result == ProtectionManager.UnclaimResult.OK
                    || result == ProtectionManager.UnclaimResult.UNKNOWN_REGION;
        };
        Map<ChunkKey, Integer> blockCursors = new HashMap<>();
        IslandDeletionService.ChunkSweeper sweeper = (targetWorld, chunkX, chunkZ) ->
                clearChunk(targetWorld, chunkX, chunkZ, blockCursors);
        IslandDeletionService.SlotRegistry slots = new IslandDeletionService.SlotRegistry() {
            @Override
            public void reserve(int slotIndex) {
                islands.blockSlot(slotIndex);
            }

            @Override
            public void release(int slotIndex) {
                islands.unblockSlot(slotIndex);
            }
        };
        IslandDeletionService.CompletionHandler completion = new IslandDeletionService.CompletionHandler() {
            @Override
            public void onCompleted(IslandDeletionService.DeletionProgress progress) {
                // The asynchronous completion hook below owns the actual finalization.
            }

            @Override
            public CompletableFuture<Void> completeAsync(IslandDeletionService.DeletionProgress progress) {
                Island island = islands.byId(progress.islandId()).orElse(null);
                if (island == null) return CompletableFuture.completedFuture(null);
                visits.clearIsland(island.id());
                List<CompletableFuture<Void>> wipes = new java.util.ArrayList<>();
                for (UUID member : island.memberSet()) {
                    Player online = Bukkit.getPlayer(member);
                    if (online != null) {
                        online.getInventory().clear();
                        online.getEnderChest().clear();
                        online.getOpenInventory().setCursor(new org.bukkit.inventory.ItemStack(Material.AIR));
                    }
                    wipes.add(pendingWipes.markPending(member));
                    wipes.add(storage.clearProfileData(member, profile));
                }
                wipes.add(economy.deleteAsync(island.id()));
                return CompletableFuture.allOf(wipes.toArray(CompletableFuture[]::new))
                        .thenRun(() -> {
                            islands.remove(island.id());
                        });
            }
        };
        return new IslandDeletionService(world, grid, config.deletionChunksPerTick(), deletionStore,
                ejector, spawn::location, unclaimer, sweeper, slots, completion, plugin.getLogger());
    }

    private static boolean clearChunk(World world, int chunkX, int chunkZ, Map<ChunkKey, Integer> cursors) {
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int total = Math.multiplyExact(16 * 16, maxY - minY);
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        int cursor = cursors.getOrDefault(key, 0);
        int end = Math.min(total, cursor + 8_192);
        for (int index = cursor; index < end; index++) {
            int y = minY + index / 256;
            int local = index % 256;
            int x = local & 15;
            int z = (local >>> 4) & 15;
            org.bukkit.block.Block block = chunk.getBlock(x, y, z);
            if (block.getType() != Material.AIR) block.setType(Material.AIR, false);
        }
        if (end >= total) {
            cursors.remove(key);
            return true;
        }
        cursors.put(key, end);
        return false;
    }

    private record ChunkKey(int x, int z) { }

    private static void registerCommands(Tweaks plugin, Runtime runtime) {
        SkyblockCommand skyblock = new SkyblockCommand(runtime);
        IslandCommand island = new IslandCommand(plugin, runtime);
        SkyblockAdminServices adminServices = SkyblockAdminServices.create(plugin, runtime);
        IslandAdminCommand admin = new IslandAdminCommand(plugin, runtime, adminServices);
        setCommand(plugin, "skyblock", skyblock);
        setCommand(plugin, "island", island);
        setCommand(plugin, "isadmin", admin);
        if (runtime.shopService() != null) setCommand(plugin, "sell", new SellCommand(runtime.shopService()));
        if (runtime.economy() != null) setCommand(plugin, "pay",
                new PayCommand(runtime.economy(), runtime.islandManager(), runtime.config().worldKey()));
    }

    private static void setCommand(Tweaks plugin, String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) throw new IllegalStateException("Missing Skyblock command declaration: " + name);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) command.setTabCompleter(completer);
    }

    private static void registerListeners(Tweaks plugin, Runtime runtime) {
        ContainmentListener containment = new ContainmentListener(areaProvider(runtime), runtime.visits(),
                me.beeliebub.tweaks.permissions.Permissions.SKYBLOCK_BYPASS,
                player -> player.sendMessage(Messages.SKYBLOCK.containment()),
                runtime.config().containmentMessageCooldownMs(), System::currentTimeMillis);
        SkyblockWorldListener worldRules = new SkyblockWorldListener(
                world -> world == runtime.world(),
                me.beeliebub.tweaks.permissions.Permissions.ADMIN_SKYBLOCK,
                new SkyblockWorldListener.SpawnRules() {
                    @Override
                    public boolean isSpawnRegion(Location location) {
                        return runtime.spawn().contains(location.getChunk());
                    }

                    @Override
                    public boolean isIslandTerrain(Location location) {
                        return runtime.islandManager().islandAt(location).isPresent();
                    }
                },
                new SkyblockWorldListener.MessageFacade() {
                    @Override
                    public void regionDenied(Player player) {
                        player.sendMessage(Messages.SKYBLOCK.invalidInput("/region is disabled in Skyblock"));
                    }

                    @Override
                    public void spawnRegionDenied(Player player) {
                        player.sendMessage(Messages.SKYBLOCK.spawnRegionCommandDenied());
                    }
                },
                runtime.visits());
        WipeApplyListener.ProfileWiper profileWiper = new WipeApplyListener.ProfileWiper() {
            @Override
            public boolean clearProfileData(UUID playerId, String frozenProfile) {
                runtime.storageManager().clearProfileData(playerId, frozenProfile);
                return true;
            }

            @Override
            public CompletableFuture<Boolean> clearProfileDataAsync(UUID playerId, String frozenProfile) {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) {
                    online.getInventory().clear();
                    online.getEnderChest().clear();
                }
                return runtime.storageManager().clearProfileData(playerId, frozenProfile)
                        .thenApply(ignored -> true);
            }
        };
        WipeApplyListener wipe = new WipeApplyListener(
                new WipeApplyListener.PendingWipeStore() {
                    @Override
                    public boolean isPending(UUID playerId) {
                        return runtime.pendingWipeStore().isPending(playerId);
                    }

                    @Override
                    public boolean remove(UUID playerId) {
                        runtime.pendingWipeStore().remove(playerId);
                        return true;
                    }
                },
                profileWiper,
                runtime.profile(),
                location -> isMissingIsland(runtime, location),
                runtime.spawn()::location,
                playerId -> CompletableFuture.allOf(
                        runtime.storageManager().loadPlayerInventoriesAsync(playerId),
                        runtime.pendingWipeStore().whenLoaded()),
                action -> plugin.getServer().getScheduler().runTask(plugin, action),
                plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(containment, plugin);
        if (runtime.adminItemEditor() != null) {
            plugin.getServer().getPluginManager().registerEvents(runtime.adminItemEditor(), plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(worldRules, plugin);
        plugin.getServer().getPluginManager().registerEvents(wipe, plugin);
        if (runtime.tracker() != null) {
            plugin.getServer().getPluginManager().registerEvents(
                    new TrackingGatherListener(runtime.islandManager(), runtime.tracker(), runtime.pdc(),
                            runtime.challengeRegistry()), plugin);
            plugin.getServer().getPluginManager().registerEvents(
                    new TrackingActionListener(plugin, runtime.islandManager(), runtime.tracker(),
                            runtime.challengeRegistry(), runtime.pdc()), plugin);
            plugin.getServer().getPluginManager().registerEvents(
                    new TrackingInteractionListener(runtime.islandManager(), runtime.tracker()), plugin);
            plugin.getServer().getPluginManager().registerEvents(
                    new TaintRetirement(runtime.pdc(), runtime.challengeRegistry(), runtime.islandManager()), plugin);
        }
        if (runtime.challengeService() != null) {
            plugin.getServer().getPluginManager().registerEvents(
                    new ChallengeProgressListener(runtime.islandManager(), runtime.challengeService()), plugin);
        }
        if (runtime.generatorRegistry() != null) {
            plugin.getServer().getPluginManager().registerEvents(
                    new CobbleGeneratorListener(runtime.islandManager(), runtime.generatorRegistry(),
                            runtime.config().worldKey()), plugin);
        }
    }

    private static ContainmentListener.AreaProvider areaProvider(Runtime runtime) {
        return new ContainmentListener.AreaProvider() {
            @Override
            public Optional<ContainmentListener.ContainmentArea> ownIsland(UUID playerId) {
                return runtime.islandManager().forPlayer(playerId).flatMap(island -> area(runtime, island));
            }

            @Override
            public Optional<ContainmentListener.ContainmentArea> island(String islandId) {
                return runtime.islandManager().byId(islandId).flatMap(island -> area(runtime, island));
            }

            @Override
            public Optional<ContainmentListener.ContainmentArea> spawnRegion(World world) {
                if (world != runtime.world() || runtime.spawn() == null || runtime.regionBridge() == null) {
                    return Optional.empty();
                }
                Region live = runtime.regionBridge().spawnRegion(world);
                if (live == null || live.bounds() == null) return Optional.empty();
                IslandGrid.ChunkBounds bounds = runtime.spawn().permittedBounds(world);
                return Optional.of(new ContainmentListener.ContainmentArea(null, world,
                        bounds, runtime.spawn().location(world)));
            }

            @Override
            public boolean isSkyblockWorld(World world) {
                return world == runtime.world();
            }

            @Override
            public boolean isDeletionInProgress(String islandId) {
                return runtime.deletion() != null && runtime.deletion().isDeleting(islandId);
            }
        };
    }

    private static Optional<ContainmentListener.ContainmentArea> area(Runtime runtime, Island island) {
        Location spawn = runtime.islandManager().spawnLocation(island, runtime.world());
        if (spawn == null) return Optional.empty();
        var bounds = runtime.grid().boundsFor(island.slotIndex(), island.size());
        return Optional.of(new ContainmentListener.ContainmentArea(island.id(), runtime.world(),
                new IslandGrid.ChunkBounds(bounds.minChunkX(), bounds.minChunkZ(),
                        bounds.maxChunkX(), bounds.maxChunkZ()), spawn));
    }

    private static boolean isMissingIsland(Runtime runtime, Location location) {
        if (location == null || location.getWorld() != runtime.world()
                || runtime.spawn().contains(location.getChunk())) return false;
        int slot = runtime.islandManager().slotFor(location);
        if (slot < 0 || runtime.islandManager().islandAt(location).isPresent()) return false;
        if (runtime.grid().isSpawnExcluded(slot)) return false;
        return runtime.grid().contains(slot, IslandSize.LARGE, location.getBlockX() >> 4,
                location.getBlockZ() >> 4);
    }

    /** Reconciles the durable spawn record with the live protection claim after bootstrap. */
    private static void reconcileSpawnRegion(Tweaks plugin, Runtime runtime) {
        if (runtime.spawn() == null || runtime.regionBridge() == null || runtime.world() == null) return;
        SkyblockSpawn spawn = runtime.spawn();
        final Region live;
        final SkyblockSpawn.SpawnData recorded;
        try {
            live = runtime.regionBridge().spawnRegion(runtime.world());
            recorded = spawn.data().orElse(null);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Skyblock spawn reconciliation could not inspect its state", error);
            return;
        }
        if (recorded == null && live == null) return;

        if (recorded != null && !matchesWorld(recorded.worldKey(), runtime.world())) {
            plugin.getLogger().warning("Skyblock spawn record names world '" + recorded.worldKey()
                    + "' but the configured world is '" + runtime.world().getName()
                    + "'; refusing cross-world recovery.");
            return;
        }

        if (recorded == null) {
            plugin.getLogger().warning("Skyblock spawn region '" + IslandRegionBridge.SPAWN_REGION_ID
                    + "' exists without a spawn record in world '" + runtime.world().getName() + "'.");
            return;
        }

        if (live == null) {
            if (recorded.owner() == null) {
                plugin.getLogger().warning("Skyblock spawn record in world '" + runtime.world().getName()
                        + "' has no owner; refusing to recreate region '"
                        + IslandRegionBridge.SPAWN_REGION_ID + "'.");
                return;
            }
            try {
                java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> pending =
                        new java.util.concurrent.atomic.AtomicReference<>();
                ProtectionManager.ClaimResult result = runtime.regionBridge().claimSpawn(runtime.world(), recorded,
                        recorded.bounds(), null, pending);
                if (result != ProtectionManager.ClaimResult.OK) {
                    plugin.getLogger().warning("Skyblock spawn region recovery failed: " + result);
                } else if (pending.get() != null) {
                    pending.get().whenComplete((ignored, error) -> {
                        if (error != null) plugin.getLogger().log(Level.WARNING,
                                "Skyblock spawn region recovery stamping failed", error);
                    });
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Skyblock spawn region recovery failed", error);
            }
            return;
        }

        if (live.bounds() == null) {
            plugin.getLogger().warning("Skyblock spawn region '" + IslandRegionBridge.SPAWN_REGION_ID
                    + "' has no bounds in world '" + runtime.world().getName() + "'.");
            return;
        }
        IslandGrid.ChunkBounds liveBounds = new IslandGrid.ChunkBounds(live.bounds().minChunkX(),
                live.bounds().minChunkZ(), live.bounds().maxChunkX(), live.bounds().maxChunkZ());
        if (!liveBounds.equals(recorded.bounds()) || !Objects.equals(live.owner(), recorded.owner())) {
            UUID owner = live.owner() == null ? recorded.owner() : live.owner();
            if (owner == null) {
                plugin.getLogger().warning("Skyblock spawn reconciliation found an ownerless live region; keeping the record unchanged.");
                return;
            }
            try {
                SkyblockSpawn.SpawnData reconciled = new SkyblockSpawn.SpawnData(
                        recorded.worldKey(), liveBounds, owner, recorded.x(), recorded.y(), recorded.z(),
                        recorded.yaw(), recorded.pitch());
                spawn.record(reconciled).whenComplete((ignored, error) -> {
                    if (error != null) plugin.getLogger().log(Level.WARNING,
                            "Skyblock spawn record reconciliation failed", error);
                });
                plugin.getLogger().info("Skyblock spawn recovery record reconciled to live region bounds and owner in world '"
                        + runtime.world().getName() + "'.");
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Skyblock spawn record reconciliation failed", error);
            }
        }
    }

    private static boolean matchesWorld(String configuredKey, World world) {
        return configuredKey.equalsIgnoreCase(world.getKey().asString())
                || configuredKey.equalsIgnoreCase(world.getName());
    }

    private static World findLoadedWorld(Tweaks plugin, String worldKey) {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getKey().asString().equalsIgnoreCase(worldKey)) return world;
        }
        return null;
    }

    private static boolean isExclusiveProfile(WorldProfileTable table, String skyblockWorldKey,
                                               String skyblockProfile) {
        for (String configuredWorldKey : table.worldKeysOrdered()) {
            if (configuredWorldKey.equalsIgnoreCase(skyblockWorldKey)) continue;
            if (skyblockProfile.equals(table.profileFor(configuredWorldKey))) return false;
        }
        return true;
    }

    private static void failGate(Tweaks plugin, String worldKey, String reason) {
        String command = "/tconfig world-profiles add " + worldKey + " "
                + REMEDIATION_PROFILE + " " + REMEDIATION_LABEL + " " + REMEDIATION_COLOR;
        plugin.getLogger().severe("Skyblock disabled: " + reason + ". Run exactly `" + command
                + "` and restart the server.");
    }

    /** Stops periodic work and gives queued persistence a bounded final flush. */
    public static void shutdown(Tweaks plugin, Runtime runtime) {
        if (runtime == null) return;
        if (runtime.adminItemEditor() != null) runtime.adminItemEditor().shutdown();
        if (runtime.deletionTask() != null) runtime.deletionTask().cancel();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_TIMEOUT_SECONDS);
        boolean interrupted = awaitFlush(plugin, "island", runtime.islandStore().shutdown(), deadline, false);
        if (runtime.spawn() != null) {
            interrupted |= awaitFlush(plugin, "spawn", runtime.spawn().flush(), deadline, interrupted);
        }
        if (runtime.deletion() != null) {
            interrupted |= awaitFlush(plugin, "deletion", runtime.deletion().flush(), deadline, interrupted);
        }
        interrupted |= awaitFlush(plugin, "pending-wipe", runtime.pendingWipeStore().shutdown(), deadline,
                interrupted);
        if (runtime.typeRegistry() != null) {
            interrupted |= awaitFlush(plugin, "type-registry", runtime.typeRegistry().flush(), deadline, interrupted);
        }
        if (runtime.challengeRegistry() != null) {
            interrupted |= awaitFlush(plugin, "challenge-registry", runtime.challengeRegistry().flush(), deadline,
                    interrupted);
        }
        if (runtime.generatorRegistry() != null) {
            interrupted |= awaitFlush(plugin, "generator-registry", runtime.generatorRegistry().flush(), deadline,
                    interrupted);
        }
        if (runtime.shopCatalog() != null) {
            interrupted |= awaitFlush(plugin, "shop-catalog", runtime.shopCatalog().flush(), deadline, interrupted);
        }
        if (runtime.biomeService() != null) runtime.biomeService().shutdown();
        if (runtime.economy() != null) {
            interrupted |= awaitFlush(plugin, "economy", runtime.economy().shutdown(), deadline, interrupted);
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static boolean awaitFlush(Tweaks plugin, String name, CompletableFuture<Void> flush,
                                      long deadline, boolean interrupted) {
        try {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) throw new TimeoutException();
            flush.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            plugin.getLogger().severe("Skyblock " + name
                    + " flush did not finish before the shutdown timeout.");
        } catch (InterruptedException error) {
            plugin.getLogger().warning("Skyblock " + name + " flush was interrupted during shutdown.");
            interrupted = true;
        } catch (ExecutionException error) {
            plugin.getLogger().log(Level.WARNING,
                    "Skyblock " + name + " flush failed during shutdown", error.getCause());
        }
        return interrupted;
    }
}
