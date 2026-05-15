package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionListener;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProtectionListenerTest {

    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    private ProtectionManager protection;
    private ProtectionListener listener;

    @BeforeEach
    void newManager() {
        protection = new ProtectionManager(mock(Tweaks.class));
        listener = new ProtectionListener(protection);
    }

    // ---- helpers ----

    private static Location locationInProtectedChunk(List<String> pdcIds) {
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);
        doReturn(pdcIds).when(pdc)
                .getOrDefault(any(NamespacedKey.class), any(), eq(List.of()));
        Location loc = mock(Location.class);
        when(loc.getChunk()).thenReturn(chunk);
        return loc;
    }

    private static Block blockAt(Location loc, Material mat) {
        Block b = mock(Block.class);
        when(b.getLocation()).thenReturn(loc);
        when(b.getType()).thenReturn(mat);
        return b;
    }

    private static Player player(UUID uuid) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        return p;
    }

    private void registerLockedRegion() {
        protection.regions().put("home",
                new Region("home", UUID.randomUUID(), List.of(), EnumSet.noneOf(RegionFlag.class)));
    }

    // ---- BlockBreakEvent ----

    @Test
    void blockBreakCancelledWhenNotAllowed() {
        registerLockedRegion();
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.STONE);
        Player p = player(PLAYER);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockBreak(event);

        verify(event).setCancelled(true);
    }

    @Test
    void blockBreakAllowedInWilderness() {
        Block b = blockAt(locationInProtectedChunk(List.of()), Material.STONE);
        Player p = player(PLAYER);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockBreak(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void blockBreakHonoursAllowListWhenBaseFlagDenies() {
        // Region locks BLOCK_BREAK by default but whitelists grass blocks via
        // ALLOW_BLOCK_BREAK; the listener must consult the material-aware
        // path on the manager and let grass through.
        protection.regions().put("home", new Region("home", UUID.randomUUID(), List.of(),
                java.util.Map.of(), java.util.Map.of(
                        RegionFlag.ALLOW_BLOCK_BREAK, java.util.Set.of(Material.GRASS_BLOCK)),
                null));
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.GRASS_BLOCK);
        Player p = player(PLAYER);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockBreak(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void blockBreakBlockedByDenyListEvenWhenBaseFlagPermits() {
        // BLOCK_BREAK=true at DEFAULT, but DENY_BLOCK_BREAK protects beacons.
        protection.regions().put("home", new Region("home", UUID.randomUUID(), List.of(),
                java.util.Map.of(RegionFlag.BLOCK_BREAK,
                        java.util.Map.of(me.beeliebub.tweaks.protection.FlagTarget.DEFAULT, true)),
                java.util.Map.of(RegionFlag.DENY_BLOCK_BREAK, java.util.Set.of(Material.BEACON)),
                null));
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.BEACON);
        Player p = player(PLAYER);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockBreak(event);

        verify(event).setCancelled(true);
    }

    // ---- BlockPlaceEvent ----

    @Test
    void blockPlaceCancelledWhenNotAllowed() {
        registerLockedRegion();
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.STONE);
        Player p = player(PLAYER);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockPlace(event);

        verify(event).setCancelled(true);
    }

    @Test
    void blockPlaceHonoursAllowListWhenBaseFlagDenies() {
        protection.regions().put("home", new Region("home", UUID.randomUUID(), List.of(),
                java.util.Map.of(), java.util.Map.of(
                        RegionFlag.ALLOW_BLOCK_PLACE, java.util.Set.of(Material.TORCH)),
                null));
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.TORCH);
        Player p = player(PLAYER);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void blockPlaceBlockedByDenyListEvenWhenBaseFlagPermits() {
        protection.regions().put("home", new Region("home", UUID.randomUUID(), List.of(),
                java.util.Map.of(RegionFlag.BLOCK_PLACE,
                        java.util.Map.of(me.beeliebub.tweaks.protection.FlagTarget.DEFAULT, true)),
                java.util.Map.of(RegionFlag.DENY_BLOCK_PLACE, java.util.Set.of(Material.TNT)),
                null));
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.TNT);
        Player p = player(PLAYER);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockPlace(event);

        verify(event).setCancelled(true);
    }

    @Test
    void blockPlaceAllowedWhenFlagSet() {
        protection.regions().put("home",
                new Region("home", UUID.randomUUID(), List.of(), EnumSet.of(RegionFlag.BLOCK_PLACE)));
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.STONE);
        Player p = player(PLAYER);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
    }

    // ---- PlayerInteractEvent ----

    @Test
    void interactIgnoredForNonRightClickAction() {
        registerLockedRegion();
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
        verify(event, never()).getClickedBlock();
    }

    @Test
    void interactIgnoredForAirClick() {
        registerLockedRegion();
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(null);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void interactCancelledForContainerInLockedRegion() {
        registerLockedRegion();
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.CHEST);
        Player p = player(PLAYER);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void interactWithUnmappedMaterialIsLeftAlone() {
        registerLockedRegion();
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.CRAFTING_TABLE);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(b);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
        verify(event, never()).getPlayer();
    }

    @Test
    void interactCancelledForDoorWhenInteractFlagUnset() {
        registerLockedRegion();
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.OAK_DOOR);
        Player p = player(PLAYER);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void interactCancelledForLeverWhenRedstoneFlagUnset() {
        registerLockedRegion();
        Block b = blockAt(locationInProtectedChunk(List.of("home")), Material.LEVER);
        Player p = player(PLAYER);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(b);
        when(event.getPlayer()).thenReturn(p);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
    }

    // ---- Explosion events ----

    @Test
    void entityExplosionStripsProtectedBlocksFromList() {
        registerLockedRegion();
        Location protectedLoc = locationInProtectedChunk(List.of("home"));
        Location wildLoc = locationInProtectedChunk(List.of());
        Block protectedBlock = blockAt(protectedLoc, Material.STONE);
        Block wildBlock = blockAt(wildLoc, Material.STONE);

        List<Block> blocks = new ArrayList<>(List.of(protectedBlock, wildBlock));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(blocks);

        listener.onEntityExplode(event);

        assertEquals(List.of(wildBlock), blocks,
                "protected block must be filtered out; wilderness block survives");
    }

    @Test
    void entityExplosionWithFlagSetKeepsAllBlocks() {
        protection.regions().put("home",
                new Region("home", UUID.randomUUID(), List.of(), EnumSet.of(RegionFlag.EXPLOSION)));
        Block b1 = blockAt(locationInProtectedChunk(List.of("home")), Material.STONE);
        Block b2 = blockAt(locationInProtectedChunk(List.of()), Material.STONE);

        List<Block> blocks = new ArrayList<>(List.of(b1, b2));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(blocks);

        listener.onEntityExplode(event);

        assertEquals(2, blocks.size());
    }

    @Test
    void blockExplosionUsesSameFilteringPath() {
        registerLockedRegion();
        Block protectedBlock = blockAt(locationInProtectedChunk(List.of("home")), Material.STONE);
        Block wildBlock = blockAt(locationInProtectedChunk(List.of()), Material.STONE);

        List<Block> blocks = new ArrayList<>(List.of(protectedBlock, wildBlock));
        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        when(event.blockList()).thenReturn(blocks);

        listener.onBlockExplode(event);

        assertEquals(List.of(wildBlock), blocks);
    }
}
