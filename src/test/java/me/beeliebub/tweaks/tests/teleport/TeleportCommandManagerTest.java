package me.beeliebub.tweaks.tests.teleport;

import me.beeliebub.tweaks.Point;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.managers.StorageManager;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.teleport.TeleportCommandManager;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Mirrors what the eleven removed per-command tests covered, exercised through the
// consolidated dispatcher. Each @Nested group corresponds to one command label
// (or label family for TPA / back).
class TeleportCommandManagerTest {

    // Pure-Mockito test bed shared by the simple per-label nests. Doesn't touch MockBukkit.
    private static TeleportCommandManager simpleManager(StorageManager storage, int maxHomes) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("tweaks");
        return new TeleportCommandManager(plugin, storage, mock(ResourceHuntItems.class), maxHomes);
    }

    private static Player playerWithUuid(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    private static Player playerInWorld(String worldKey) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        NamespacedKey key = mock(NamespacedKey.class);
        when(key.asString()).thenReturn(worldKey);
        when(world.getKey()).thenReturn(key);
        Location loc = new Location(world, 0, 0, 0, 0f, 0f);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    private static ArgumentMatcher<Component> componentContains(String needle) {
        return c -> c != null
                && PlainTextComponentSerializer.plainText().serialize(c)
                        .toLowerCase().contains(needle.toLowerCase());
    }

    // ============================================================
    // /home
    // ============================================================
    @Nested
    class HomeCommand {
        private final StorageManager storage = mock(StorageManager.class);
        private final TeleportCommandManager mgr = simpleManager(storage, 3);
        private final Command bukkit = mock(Command.class);

        @Test void rejectsConsoleSender() {
            mgr.onCommand(mock(ConsoleCommandSender.class), bukkit, "home", new String[0]);
            verify(storage, never()).getHome(any(), any());
        }

        @Test void noArgUsesDefaultHomeName() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(storage.getHome(uuid, "default")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "home", new String[0]);
            verify(storage).getHome(uuid, "default");
        }

        @Test void singleArgUsesNamedHome() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(storage.getHome(uuid, "base")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "home", new String[]{"base"});
            verify(storage).getHome(uuid, "base");
        }

        @Test void warnsWhenHomeNotFound() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(storage.getHome(uuid, "ghost")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "home", new String[]{"ghost"});
            verify(player, never()).teleportAsync(any());
        }

        @Test void teleportsToResolvedHome() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            World world = mock(World.class);
            when(storage.getHome(uuid, "base"))
                    .thenReturn(Optional.of(new Point("world", 1.0, 64.0, 1.0, 0f, 0f)));
            when(player.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getWorld("world")).thenReturn(world);
                mgr.onCommand(player, bukkit, "home", new String[]{"base"});
            }
            verify(player).teleportAsync(any(Location.class));
        }

        @Test void adminCanTeleportToAnotherPlayersHome() {
            UUID adminUuid = UUID.randomUUID();
            UUID targetUuid = UUID.randomUUID();
            World world = mock(World.class);
            Player admin = playerWithUuid(adminUuid);
            when(admin.hasPermission(Permissions.ADMIN_HOME)).thenReturn(true);
            when(admin.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(storage.getHome(targetUuid, "base"))
                    .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
                bukkit2.when(() -> Bukkit.getWorld("world")).thenReturn(world);
                mgr.onCommand(admin, bukkit, "home", new String[]{"Bob", "base"});
            }
            verify(storage).getHome(targetUuid, "base");
        }

        @Test void nonAdminTwoArgInvocationShowsUsageHint() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(player.hasPermission(Permissions.ADMIN_HOME)).thenReturn(false);
            mgr.onCommand(player, bukkit, "home", new String[]{"Bob", "base"});
            verify(storage, never()).getHome(any(), any());
        }

        @Test void tabCompleteOffersOwnHomesForAdmin() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(player.hasPermission(Permissions.ADMIN_HOME)).thenReturn(true);
            when(storage.getHomes(uuid)).thenReturn(Set.of("base", "mine"));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(Bukkit::getOnlinePlayers).thenReturn(java.util.List.of());
                var result = mgr.onTabComplete(player, bukkit, "home", new String[]{"b"});
                assertTrue(result.contains("base"));
                assertFalse(result.contains("mine"));
            }
        }
    }

    // ============================================================
    // /sethome
    // ============================================================
    @Nested
    class SetHomeCommand {
        private final StorageManager storage = mock(StorageManager.class);
        private final TeleportCommandManager mgr = simpleManager(storage, 3);
        private final Command bukkit = mock(Command.class);

        @Test void rejectsConsoleSender() {
            mgr.onCommand(mock(ConsoleCommandSender.class), bukkit, "sethome", new String[0]);
            verify(storage, never()).setHome(any(), anyString(), any());
        }

        @Test void refusesToSetHomeInResourceWorld() {
            Player player = playerInWorld("jass:resource");
            mgr.onCommand(player, bukkit, "sethome", new String[0]);
            verify(storage, never()).setHome(any(), anyString(), any());
        }

        @Test void setsDefaultHomeWhenNoNameGiven() {
            UUID uuid = UUID.randomUUID();
            Player player = playerInWorld("world");
            when(player.getUniqueId()).thenReturn(uuid);
            when(storage.getHomeCount(uuid)).thenReturn(0);
            when(storage.getHome(uuid, "default")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "sethome", new String[0]);
            verify(storage).setHome(eq(uuid), eq("default"), any(Point.class));
        }

        @Test void enforcesMaxHomesLimitForNonBypassers() {
            UUID uuid = UUID.randomUUID();
            Player player = playerInWorld("world");
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.hasPermission(Permissions.BYPASS_HOMES)).thenReturn(false);
            when(storage.getHomeCount(uuid)).thenReturn(3);
            when(storage.getHome(uuid, "another")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "sethome", new String[]{"another"});
            verify(storage, never()).setHome(any(), anyString(), any());
        }

        @Test void allowsOverwritingExistingHomeAtLimit() {
            UUID uuid = UUID.randomUUID();
            Player player = playerInWorld("world");
            when(player.getUniqueId()).thenReturn(uuid);
            when(storage.getHomeCount(uuid)).thenReturn(3);
            when(storage.getHome(uuid, "base"))
                    .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
            mgr.onCommand(player, bukkit, "sethome", new String[]{"base"});
            verify(storage).setHome(eq(uuid), eq("base"), any(Point.class));
        }

        @Test void bypassPermissionSkipsLimitCheck() {
            UUID uuid = UUID.randomUUID();
            Player player = playerInWorld("world");
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.hasPermission(Permissions.BYPASS_HOMES)).thenReturn(true);
            when(storage.getHomeCount(uuid)).thenReturn(99);
            when(storage.getHome(uuid, "extra")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "sethome", new String[]{"extra"});
            verify(storage).setHome(eq(uuid), eq("extra"), any(Point.class));
        }

        @Test void adminCanSetHomeForAnotherPlayerByName() {
            UUID targetUuid = UUID.randomUUID();
            Player admin = playerInWorld("world");
            when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
            when(admin.hasPermission(Permissions.ADMIN_SETHOME)).thenReturn(true);
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
                mgr.onCommand(admin, bukkit, "sethome", new String[]{"Bob", "base"});
            }
            verify(storage).setHome(eq(targetUuid), eq("base"), any(Point.class));
        }
    }

    // ============================================================
    // /delhome
    // ============================================================
    @Nested
    class DelHomeCommand {
        private final StorageManager storage = mock(StorageManager.class);
        private final TeleportCommandManager mgr = simpleManager(storage, 3);
        private final Command bukkit = mock(Command.class);

        @Test void rejectsConsoleSender() {
            mgr.onCommand(mock(ConsoleCommandSender.class), bukkit, "delhome", new String[]{"base"});
            verify(storage, never()).delHome(any(), anyString());
        }

        @Test void deletesNamedHomeForCallingPlayer() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(storage.getHome(uuid, "base"))
                    .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
            mgr.onCommand(player, bukkit, "delhome", new String[]{"base"});
            verify(storage).delHome(uuid, "base");
        }

        @Test void deletesDefaultHomeWhenCalledWithNoArgs() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(storage.getHome(uuid, "default"))
                    .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
            mgr.onCommand(player, bukkit, "delhome", new String[0]);
            verify(storage).delHome(uuid, "default");
        }

        @Test void warnsWhenHomeDoesNotExist() {
            UUID uuid = UUID.randomUUID();
            Player player = playerWithUuid(uuid);
            when(storage.getHome(uuid, "ghost")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "delhome", new String[]{"ghost"});
            verify(storage, never()).delHome(any(), anyString());
        }

        @Test void adminCanDeleteAnotherPlayersHome() {
            UUID targetUuid = UUID.randomUUID();
            Player admin = playerWithUuid(UUID.randomUUID());
            when(admin.hasPermission(Permissions.ADMIN_DELHOME)).thenReturn(true);
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(storage.getHome(targetUuid, "base"))
                    .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
                mgr.onCommand(admin, bukkit, "delhome", new String[]{"Bob", "base"});
            }
            verify(storage).delHome(targetUuid, "base");
        }
    }

    // ============================================================
    // /homes
    // ============================================================
    @Nested
    class HomesCommand {
        private final StorageManager storage = mock(StorageManager.class);
        private final TeleportCommandManager mgr = simpleManager(storage, 3);
        private final Command bukkit = mock(Command.class);

        @Test void consoleWithoutTargetIsToldToProvideOne() {
            ConsoleCommandSender console = mock(ConsoleCommandSender.class);
            mgr.onCommand(console, bukkit, "homes", new String[0]);
            verify(storage, never()).getHomes(any());
        }

        @Test void playerWithNoHomesGetsEmptyMessage() {
            UUID uuid = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.getName()).thenReturn("Bee");
            when(storage.getHomes(uuid)).thenReturn(Set.of());
            mgr.onCommand(player, bukkit, "homes", new String[0]);
            verify(player).sendMessage(argThat(componentContains("Bee has no homes")));
        }

        @Test void playerWithHomesGetsListedNames() {
            UUID uuid = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.getName()).thenReturn("Bee");
            Set<String> homes = new LinkedHashSet<>();
            homes.add("base"); homes.add("mine");
            when(storage.getHomes(uuid)).thenReturn(homes);
            mgr.onCommand(player, bukkit, "homes", new String[0]);
            verify(player).sendMessage(argThat(componentContains("base")));
            verify(player).sendMessage(argThat(componentContains("mine")));
        }

        @Test void adminCanQueryAnotherPlayerByName() {
            Player admin = mock(Player.class);
            when(admin.hasPermission(Permissions.ADMIN_HOMES)).thenReturn(true);
            when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
            when(admin.getName()).thenReturn("Admin");
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.getName()).thenReturn("Bob");
            when(storage.getHomes(targetUuid)).thenReturn(Set.of("hideout"));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
                mgr.onCommand(admin, bukkit, "homes", new String[]{"Bob"});
            }
            verify(admin).sendMessage(argThat(componentContains("Bob")));
            verify(admin).sendMessage(argThat(componentContains("hideout")));
        }

        @Test void nonAdminFallsBackToSelfIfPlayer() {
            UUID uuid = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.getName()).thenReturn("Bee");
            when(player.hasPermission(Permissions.ADMIN_HOMES)).thenReturn(false);
            when(storage.getHomes(uuid)).thenReturn(Set.of("base"));
            mgr.onCommand(player, bukkit, "homes", new String[]{"Bob"});
            verify(storage).getHomes(uuid);
        }
    }

    // ============================================================
    // /warp, /setwarp, /delwarp, /warps
    // ============================================================
    @Nested
    class Warps {
        private final StorageManager storage = mock(StorageManager.class);
        private final TeleportCommandManager mgr = simpleManager(storage, 3);
        private final Command bukkit = mock(Command.class);

        @Test void warpRejectsConsole() {
            mgr.onCommand(mock(ConsoleCommandSender.class), bukkit, "warp", new String[]{"spawn"});
            verify(storage, never()).getWarp(any());
        }

        @Test void warpRejectsMissingArgument() {
            Player player = mock(Player.class);
            mgr.onCommand(player, bukkit, "warp", new String[0]);
            verify(storage, never()).getWarp(any());
        }

        @Test void warpTeleportsAsyncWhenResolved() {
            Player player = mock(Player.class);
            World world = mock(World.class);
            when(storage.getWarp("spawn"))
                    .thenReturn(Optional.of(new Point("world", 1.0, 2.0, 3.0, 4f, 5f)));
            when(player.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getWorld("world")).thenReturn(world);
                mgr.onCommand(player, bukkit, "warp", new String[]{"spawn"});
            }
            ArgumentCaptor<Location> locCaptor = ArgumentCaptor.forClass(Location.class);
            verify(player).teleportAsync(locCaptor.capture());
            assertSame(world, locCaptor.getValue().getWorld());
            assertEquals(1.0, locCaptor.getValue().getX());
        }

        @Test void warpTabCompleteFiltersByPrefix() {
            when(storage.getWarps()).thenReturn(Set.of("spawn", "shop"));
            var result = mgr.onTabComplete(mock(Player.class), bukkit, "warp", new String[]{"sp"});
            assertTrue(result.contains("spawn"));
            assertFalse(result.contains("shop"));
        }

        @Test void setwarpRejectsWithoutAdminPermission() {
            Player player = mock(Player.class);
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(player.getLocation()).thenReturn(new Location(world, 1.0, 2.0, 3.0, 0f, 0f));
            when(player.hasPermission(Permissions.ADMIN_SETWARP)).thenReturn(false);
            mgr.onCommand(player, bukkit, "setwarp", new String[]{"spawn"});
            verify(storage, never()).setWarp(any(), any());
        }

        @Test void setwarpUsesPlayerLocation() {
            Player player = mock(Player.class);
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(player.getLocation()).thenReturn(new Location(world, 1.0, 2.0, 3.0, 0f, 0f));
            when(player.hasPermission(Permissions.ADMIN_SETWARP)).thenReturn(true);
            mgr.onCommand(player, bukkit, "setwarp", new String[]{"home"});
            ArgumentCaptor<Point> pc = ArgumentCaptor.forClass(Point.class);
            verify(storage).setWarp(eq("home"), pc.capture());
            assertEquals("world", pc.getValue().worldName());
        }

        @Test void delwarpRejectsWithoutPermission() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission(Permissions.ADMIN_DELWARP)).thenReturn(false);
            mgr.onCommand(sender, bukkit, "delwarp", new String[]{"spawn"});
            verify(storage, never()).delWarp(any());
        }

        @Test void delwarpDeletesExistingWarp() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission(Permissions.ADMIN_DELWARP)).thenReturn(true);
            when(storage.getWarp("spawn"))
                    .thenReturn(Optional.of(new Point("world", 0, 0, 0, 0f, 0f)));
            mgr.onCommand(sender, bukkit, "delwarp", new String[]{"spawn"});
            verify(storage).delWarp("spawn");
        }

        @Test void warpsAnnouncesEmptyWhenNoneRegistered() {
            when(storage.getWarps()).thenReturn(Set.of());
            CommandSender sender = mock(CommandSender.class);
            mgr.onCommand(sender, bukkit, "warps", new String[0]);
            verify(sender).sendMessage(argThat(componentContains("no warps")));
        }

        @Test void warpsListsRegisteredWarps() {
            Set<String> warps = new LinkedHashSet<>();
            warps.add("spawn"); warps.add("shop");
            when(storage.getWarps()).thenReturn(warps);
            CommandSender sender = mock(CommandSender.class);
            mgr.onCommand(sender, bukkit, "warps", new String[0]);
            verify(sender).sendMessage(argThat(componentContains("spawn")));
            verify(sender).sendMessage(argThat(componentContains("shop")));
        }
    }

    // ============================================================
    // /spawn
    // ============================================================
    @Nested
    class SpawnCommand {
        private final StorageManager storage = mock(StorageManager.class);
        private final TeleportCommandManager mgr = simpleManager(storage, 3);
        private final Command bukkit = mock(Command.class);

        @Test void rejectsConsoleSender() {
            mgr.onCommand(mock(ConsoleCommandSender.class), bukkit, "spawn", new String[0]);
            verify(storage, never()).getWarp(any());
        }

        @Test void warnsWhenSpawnNotConfigured() {
            Player player = mock(Player.class);
            when(storage.getWarp("spawn")).thenReturn(Optional.empty());
            mgr.onCommand(player, bukkit, "spawn", new String[0]);
            verify(player, never()).teleportAsync(any());
        }

        @Test void teleportsToSpawnWhenConfigured() {
            Player player = mock(Player.class);
            World world = mock(World.class);
            when(storage.getWarp("spawn"))
                    .thenReturn(Optional.of(new Point("world", 1.0, 2.0, 3.0, 4f, 5f)));
            try (MockedStatic<Bukkit> bukkit2 = mockStatic(Bukkit.class)) {
                bukkit2.when(() -> Bukkit.getWorld("world")).thenReturn(world);
                mgr.onCommand(player, bukkit, "spawn", new String[0]);
            }
            verify(player).teleportAsync(any(Location.class));
        }
    }

    // ============================================================
    // /back  (uses MockBukkit because PersistentDataContainer + scheduler are needed)
    // ============================================================
    @Nested
    class Back {
        private ServerMock server;
        private Tweaks plugin;
        private TeleportCommandManager mgr;
        private NamespacedKey backKey;
        private final Command bukkit = mock(Command.class);

        @BeforeEach void setUp() {
            server = MockBukkit.mock();
            plugin = MockBukkit.load(Tweaks.class);
            mgr = new TeleportCommandManager(plugin, mock(StorageManager.class), mock(ResourceHuntItems.class), 3);
            backKey = new NamespacedKey(plugin, "back_location");
        }

        @AfterEach void tearDown() {
            MockBukkit.unmock();
        }

        @Test void onTeleportSavesLocation() {
            PlayerMock player = server.addPlayer();
            Location from = new Location(player.getWorld(), 10, 64, 10);
            Location to = new Location(player.getWorld(), 20, 64, 20);
            mgr.onTeleport(new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.COMMAND));
            String stored = player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING);
            assertNotNull(stored);
            assertTrue(stored.contains("10.0,64.0,10.0"));
        }

        @Test void onTeleportIgnoresExcludedCauses() {
            PlayerMock player = server.addPlayer();
            Location from = new Location(player.getWorld(), 10, 64, 10);
            Location to = new Location(player.getWorld(), 20, 64, 20);
            mgr.onTeleport(new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.EXIT_BED));
            assertNull(player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING));
            mgr.onTeleport(new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.DISMOUNT));
            assertNull(player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING));
        }

        @Test void onDeathSavesLocation() {
            PlayerMock player = server.addPlayer();
            player.teleport(new Location(player.getWorld(), 5, 70, 5));
            PlayerDeathEvent event = mock(PlayerDeathEvent.class);
            when(event.getEntity()).thenReturn(player);
            mgr.onDeath(event);
            String stored = player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING);
            assertNotNull(stored);
            assertTrue(stored.contains("5.0,70.0,5.0"));
        }

        @Test void backCommandTeleportsPlayer() {
            PlayerMock player = server.addPlayer();
            String serialized = player.getWorld().getName() + ",100,64,100,0,0";
            player.getPersistentDataContainer().set(backKey, PersistentDataType.STRING, serialized);
            mgr.onCommand(player, bukkit, "back", new String[0]);
            assertEquals(100, player.getLocation().getX());
            assertEquals(64, player.getLocation().getY());
            assertEquals(100, player.getLocation().getZ());
        }

        @Test void backCommandFailsWithNoLocation() {
            PlayerMock player = server.addPlayer();
            mgr.onCommand(player, bukkit, "back", new String[0]);
            assertNotNull(player.nextMessage());
        }
    }

    // ============================================================
    // /tpa, /tpahere, /tpaccept, /tpdeny
    // ============================================================
    @Nested
    class Tpa {
        private ServerMock server;
        private Tweaks plugin;
        private TeleportCommandManager mgr;
        private final Command bukkit = mock(Command.class);

        @BeforeEach void setUp() {
            server = MockBukkit.mock();
            plugin = MockBukkit.load(Tweaks.class);
            mgr = new TeleportCommandManager(plugin, mock(StorageManager.class), mock(ResourceHuntItems.class), 3);
        }

        @AfterEach void tearDown() {
            MockBukkit.unmock();
        }

        @Test void tpaRequestSentSuccessfully() {
            PlayerMock sender = server.addPlayer("Sender");
            PlayerMock target = server.addPlayer("Target");
            sender.nextComponentMessage();
            target.nextComponentMessage();
            assertTrue(mgr.onCommand(sender, bukkit, "tpa", new String[]{"Target"}));
            MessageAssert.assertMessageSent(sender, "TPA request sent to Target");
            MessageAssert.assertMessageSent(target, "Sender wants to teleport to you");
        }

        @Test void tpahereRequestUsesCorrectWording() {
            PlayerMock sender = server.addPlayer("Sender");
            PlayerMock target = server.addPlayer("Target");
            sender.nextComponentMessage();
            target.nextComponentMessage();
            assertTrue(mgr.onCommand(sender, bukkit, "tpahere", new String[]{"Target"}));
            MessageAssert.assertMessageSent(target, "Sender wants you to teleport to them");
        }

        @Test void tpaToSelfFails() {
            PlayerMock sender = server.addPlayer("Sender");
            sender.nextComponentMessage();
            mgr.onCommand(sender, bukkit, "tpa", new String[]{"Sender"});
            MessageAssert.assertMessageSent(sender, "You can't send a TPA request to yourself");
        }

        @Test void tpaDenyNotifiesRequester() {
            PlayerMock sender = server.addPlayer("Sender");
            PlayerMock target = server.addPlayer("Target");
            sender.nextComponentMessage();
            target.nextComponentMessage();
            mgr.onCommand(sender, bukkit, "tpa", new String[]{"Target"});
            sender.nextComponentMessage();
            target.nextComponentMessage();
            target.nextComponentMessage();
            assertTrue(mgr.onCommand(target, bukkit, "tpdeny", new String[0]));
            MessageAssert.assertMessageSent(target, "TPA request denied");
            MessageAssert.assertMessageSent(sender, "Target denied your TPA request");
        }

        @Test void tpacceptWithNoRequestFails() {
            PlayerMock target = server.addPlayer("Target");
            target.nextComponentMessage();
            assertTrue(mgr.onCommand(target, bukkit, "tpaccept", new String[0]));
            MessageAssert.assertMessageSent(target, "You have no pending TPA requests");
        }
    }
}
