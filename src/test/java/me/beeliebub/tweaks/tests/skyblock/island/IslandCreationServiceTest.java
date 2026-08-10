package me.beeliebub.tweaks.tests.skyblock.island;

import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandCreationService;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandRegionBridge;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.template.TemplateStore;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IslandCreationServiceTest {
    private static final NamespacedKey FOREIGN_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("foreign-plugin:creation-kit"));

    private ServerMock server;
    private JavaPlugin plugin;
    private World world;
    private Player player;
    private PlayerInventory inventory;
    private IslandManager islands;
    private IslandRegionBridge regions;
    private TypeRegistry types;
    private IslandCreationService service;
    private Island pendingIsland;
    private Location playerLocation;
    private List<LogRecord> logRecords;
    private Handler logHandler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("island-creation-test");
        world = mock(World.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        islands = mock(IslandManager.class);
        regions = mock(IslandRegionBridge.class);
        types = mock(TypeRegistry.class);

        UUID owner = UUID.randomUUID();
        pendingIsland = Island.create(owner, 0, IslandSize.SMALL);
        playerLocation = new Location(world, 0.5, 65.0, 0.5);

        when(player.getUniqueId()).thenReturn(owner);
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

        IslandGrid grid = mock(IslandGrid.class);
        when(grid.chunkBoundsFor(0, IslandSize.SMALL))
                .thenReturn(new IslandGrid.ChunkBounds(0, 0, 0, 0));
        when(islands.grid()).thenReturn(grid);
        when(islands.createPendingIsland(owner)).thenReturn(Optional.of(pendingIsland));
        when(islands.spawnLocation(any(Island.class), same(world))).thenReturn(playerLocation);
        when(regions.create(any(Island.class), same(world), any()))
                .thenReturn(ProtectionManager.ClaimResult.OK);

        SkyblockConfig config = mock(SkyblockConfig.class);
        when(config.islandY()).thenReturn(64);
        service = new IslandCreationService(plugin, config, islands, regions, types,
                mock(TemplateStore.class), world, 1);

        Logger logger = plugin.getLogger();
        logger.setUseParentHandlers(false);
        logRecords = new ArrayList<>();
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logRecords.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(logHandler);
    }

    @AfterEach
    void tearDown() {
        if (plugin != null && logHandler != null) plugin.getLogger().removeHandler(logHandler);
        MockBukkit.unmock();
    }

    @Test
    void grantsACloneWithAllStoredMetadataAndContainerContents() {
        ItemStack original = richContainerItem();
        IslandType.KitItem stored = new IslandType.KitItem(original);
        IslandType type = type("rich", List.of(stored));
        AtomicReference<ItemStack> received = new AtomicReference<>();
        AtomicReference<ItemStack> passedToInventory = new AtomicReference<>();
        when(inventory.addItem(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack granted = invocation.getArgument(0);
            passedToInventory.set(granted);
            received.set(granted.clone());
            granted.setAmount(1);
            return new HashMap<Integer, ItemStack>();
        });

        IslandCreationService.CreationResult completed = complete(type);

        assertEquals(IslandCreationService.Status.COMPLETED, completed.status());
        assertNotSame(original, passedToInventory.get());
        assertEquals(original.serialize(), received.get().serialize());
        assertEquals(2, stored.itemStack().getAmount(),
                "inventory mutation must not alter the stored kit definition");

        ItemMeta actualMeta = received.get().getItemMeta();
        assertEquals(Component.text("Packed starter"), actualMeta.displayName());
        assertEquals(List.of(Component.text("Handle with care")), actualMeta.lore());
        assertEquals(3, actualMeta.getEnchantLevel(Enchantment.UNBREAKING));
        assertEquals(7102, actualMeta.getCustomModelData());
        assertEquals(41, actualMeta.getPersistentDataContainer()
                .get(FOREIGN_KEY, PersistentDataType.INTEGER));
        ShulkerBox shulker = (ShulkerBox) ((BlockStateMeta) actualMeta).getBlockState();
        assertEquals(new ItemStack(Material.DIAMOND, 4), shulker.getInventory().getItem(0));
    }

    @Test
    void dropsEveryInventoryOverflowStackAtThePlayersFeet() {
        ItemStack firstOverflow = new ItemStack(Material.COBBLESTONE, 64);
        ItemStack secondOverflow = new ItemStack(Material.OAK_SAPLING, 3);
        HashMap<Integer, ItemStack> overflow = new HashMap<>();
        overflow.put(0, firstOverflow);
        overflow.put(1, secondOverflow);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(overflow);

        complete(type("overflow", List.of(new IslandType.KitItem("STONE", 64))));

        verify(world).dropItemNaturally(same(playerLocation), same(firstOverflow));
        verify(world).dropItemNaturally(same(playerLocation), same(secondOverflow));
    }

    @Test
    void kitEntryFailureWarnsContinuesAndNeverRollsBackPublishedIsland() {
        IslandType.KitItem broken = mock(IslandType.KitItem.class);
        when(broken.itemStack()).thenThrow(new IllegalStateException("broken kit entry"));
        IslandType type = type("faulty", List.of(
                broken,
                new IslandType.KitItem("DIAMOND", 3)));

        IslandCreationService.CreationResult completed = complete(type);

        assertEquals(IslandCreationService.Status.COMPLETED, completed.status());
        verify(inventory).addItem(argThat((ItemStack item) -> item.getType() == Material.DIAMOND
                && item.getAmount() == 3));
        verify(islands).publishCreated(argThat(island -> island.id().equals(pendingIsland.id())
                && island.typeId().equals("faulty")));
        verify(regions, never()).delete(any(Island.class), same(world));
        verify(islands, never()).remove(pendingIsland.id());

        LogRecord warning = logRecords.stream()
                .filter(record -> record.getLevel() == Level.WARNING)
                .findFirst()
                .orElseThrow();
        assertTrue(warning.getMessage().contains(pendingIsland.id()));
        assertTrue(warning.getMessage().contains("faulty"));
        assertNotNull(warning.getThrown());
    }

    private IslandCreationService.CreationResult complete(IslandType type) {
        when(types.type(type.id())).thenReturn(Optional.of(type));
        AtomicReference<IslandCreationService.CreationResult> completion = new AtomicReference<>();

        IslandCreationService.CreationResult started = service.begin(
                player, type.id(), "normal", completion::set);
        assertEquals(IslandCreationService.Status.STARTED, started.status());
        server.getScheduler().performOneTick();

        return Objects.requireNonNull(completion.get(), "creation did not complete on its scheduled tick");
    }

    private static IslandType type(String id, List<IslandType.KitItem> kit) {
        return new IslandType(id, id, Set.of("normal"), "", kit, "PLAINS", Set.of());
    }

    private static ItemStack richContainerItem() {
        ItemStack item = new ItemStack(Material.PURPLE_SHULKER_BOX, 2);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        meta.displayName(Component.text("Packed starter"));
        meta.lore(List.of(Component.text("Handle with care")));
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.setCustomModelData(7102);
        meta.getPersistentDataContainer().set(FOREIGN_KEY, PersistentDataType.INTEGER, 41);
        ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
        shulker.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 4));
        meta.setBlockState(shulker);
        item.setItemMeta(meta);
        return item;
    }
}
