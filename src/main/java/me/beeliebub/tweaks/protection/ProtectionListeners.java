package me.beeliebub.tweaks.protection;

import io.papermc.paper.event.entity.EntityMoveEvent;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.protection.ui.RegionSelection;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import me.beeliebub.tweaks.utils.GeometryUtil;
import me.beeliebub.tweaks.utils.PDCUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Consolidates the three protection-related listeners that previously lived in
// separate files: ChunkListener (lazy stamp / orphan cleanup), ProtectionListener
// (block/interact/explosion/spawn/damage gating), and SelectionWandListener
// (wand-tool pos1/pos2 selection). Each section preserves the priority/cancel
// semantics of its original listener exactly.
public final class ProtectionListeners implements Listener {

    // Materials that, when right-clicked, open an inventory or otherwise
    // grant access to stored items. Routed to CONTAINER_ACCESS.
    private static final Set<Material> CONTAINERS = EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.HOPPER,
            Material.DROPPER,
            Material.DISPENSER,
            Material.BREWING_STAND,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BEACON
    );

    private static final Set<Material> REDSTONE_INPUTS = EnumSet.of(
            Material.LEVER
    );

    private final Tweaks plugin;
    private final ProtectionManager protection;
    private final RegionSelectionManager selections;

    public ProtectionListeners(Tweaks plugin, ProtectionManager protection, RegionSelectionManager selections) {
        this.plugin = plugin;
        this.protection = protection;
        this.selections = selections;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        protection.clearProtectionBypass(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protection.clearProtectionBypass(event.getPlayer().getUniqueId());
    }

    // ─── ChunkListener (lazy stamp + orphan cleanup) ──────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String stampKey = ProtectionManager.stampKey(chunk.getWorld().getName(), chunk.getChunkKey());

        Set<String> pending = protection.pendingStamps().remove(stampKey);
        if (pending != null && !pending.isEmpty()) {
            PDCUtil.append(chunk, pending, protection.regionPointersKey());
        }

        Set<String> orphaned = protection.orphanedRegions();
        List<String> current = PDCUtil.read(chunk, protection.regionPointersKey());
        if (current.isEmpty()) return;

        Set<String> deadOnThisChunk = null;
        for (String id : current) {
            if (orphaned.contains(ProtectionManager.keyOf(chunk.getWorld().getName(), id))) {
                if (deadOnThisChunk == null) deadOnThisChunk = new HashSet<>();
                deadOnThisChunk.add(id);
                continue;
            }

            var region = protection.byName(chunk.getWorld(), id);
            if (region == null) {
                // An unresolved id may belong to a malformed or temporarily unloaded
                // region; preserve its pointer so a later reload can recover it.
                continue;
            }

            if (region.bounds() != null && !region.bounds().contains(chunk.getX(), chunk.getZ())) {
                if (deadOnThisChunk == null) deadOnThisChunk = new HashSet<>();
                deadOnThisChunk.add(id);
            }
        }
        if (deadOnThisChunk != null) PDCUtil.remove(chunk, deadOnThisChunk, protection.regionPointersKey());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        protection.globalRegion(event.getWorld());
    }

    // ─── ProtectionListener (block/interact/explosion/spawn/damage) ───────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protection.isBlockActionAllowed(
                event.getBlock().getLocation(),
                event.getPlayer().getUniqueId(),
                event.getBlock().getType(),
                RegionFlag.BLOCK_BREAK)) {
            event.setCancelled(true);
            notifyAdminBypassHint(event.getPlayer());
            logDenied(LoggingPaths.PROTECTION_BLOCK_BREAK, event.getPlayer(),
                    event.getBlock().getLocation(), RegionFlag.BLOCK_BREAK);
        }
    }

    // Creative mode left-clicks bypass PlayerInteractEvent cancellation and
    // fire BlockBreakEvent directly. Mirror the cancel here so the selection
    // wand never breaks blocks regardless of game mode.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSelectionWandBreak(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protection.isBlockActionAllowed(
                event.getBlock().getLocation(),
                event.getPlayer().getUniqueId(),
                event.getBlock().getType(),
                RegionFlag.BLOCK_PLACE)) {
            event.setCancelled(true);
            notifyAdminBypassHint(event.getPlayer());
            logDenied(LoggingPaths.PROTECTION_BLOCK_PLACE, event.getPlayer(),
                    event.getBlock().getLocation(), RegionFlag.BLOCK_PLACE);
        }
    }

    // Combined PlayerInteractEvent handler. Wand selection runs first at LOWEST
    // priority (matching the original SelectionWandListener), and only when the
    // held item is the wand does it consume the event. The protection gate runs
    // afterwards on a separate handler at LOW priority — see onPlayerInteract.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSelectionWandInteract(PlayerInteractEvent event) {
        if (!isWand(event.getItem())) return;

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        long chunkKey = GeometryUtil.chunkKey(
                GeometryUtil.blockToChunk(block.getX()),
                GeometryUtil.blockToChunk(block.getZ()));
        RegionSelection sel = selections.getOrCreate(player, block.getWorld());

        if (left) {
            sel.setPos1(chunkKey);
            announce(player, Text.SELECTION_POS1, chunkKey, sel);
        } else {
            sel.setPos2(chunkKey);
            announce(player, Text.SELECTION_POS2, chunkKey, sel);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        RegionFlag needed = interactionFlag(block.getType());
        if (needed == null) return;

        if (!protection.isAllowed(
                block.getLocation(),
                event.getPlayer().getUniqueId(),
                needed)) {
            event.setCancelled(true);
            notifyAdminBypassHint(event.getPlayer());
            logDenied(LoggingPaths.PROTECTION_CONTAINER, event.getPlayer(),
                    block.getLocation(), needed);
        }
    }

    // Map a clicked block's material to the protection flag that gates it.
    // Returns null for materials we don't gate.
    static RegionFlag interactionFlag(Material mat) {
        if (CONTAINERS.contains(mat)) return RegionFlag.CONTAINER_ACCESS;
        if (Tag.SHULKER_BOXES.isTagged(mat)) return RegionFlag.CONTAINER_ACCESS;

        if (REDSTONE_INPUTS.contains(mat)) return RegionFlag.REDSTONE;
        if (Tag.BUTTONS.isTagged(mat)) return RegionFlag.REDSTONE;
        if (Tag.PRESSURE_PLATES.isTagged(mat)) return RegionFlag.REDSTONE;

        if (Tag.DOORS.isTagged(mat)) return RegionFlag.INTERACT;
        if (Tag.TRAPDOORS.isTagged(mat)) return RegionFlag.INTERACT;
        if (Tag.FENCE_GATES.isTagged(mat)) return RegionFlag.INTERACT;
        if (Tag.BEDS.isTagged(mat)) return RegionFlag.INTERACT;

        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    private void filterExplosion(java.util.List<Block> blocks) {
        Map<Long, Boolean> verdicts = new HashMap<>();
        blocks.removeIf(block -> {
            Chunk chunk = block.getChunk();
            // Mocked or synthetic Block implementations may not expose a
            // Chunk; retain the correctness path without making the hot path
            // throw before protection can evaluate the location.
            long cacheKey = chunk == null ? System.identityHashCode(block) : chunk.getChunkKey();
            boolean allowed = verdicts.computeIfAbsent(cacheKey,
                    ignored -> protection.isAllowed(block.getLocation(), null, RegionFlag.EXPLOSION));
            return !allowed;
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        var loc = event.getLocation();
        var type = event.getEntityType();
        var applicable = protection.regionsAt(loc);
        if (protection.isEntityListed(applicable, RegionFlag.DENY_MOB_SPAWN, type)) {
            event.setCancelled(true);
            return;
        }
        if (protection.isEntityListed(applicable, RegionFlag.ALLOW_MOB_SPAWN, type)) {
            return;
        }
        if (!protection.isAllowed(applicable, loc, null, RegionFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (protection.isExplicitlyAllowed(
                player.getLocation(), player.getUniqueId(), RegionFlag.INVINCIBILITY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (protection.isExplicitlyAllowed(
                player.getLocation(), player.getUniqueId(), RegionFlag.INVINCIBILITY)) {
            event.setCancelled(true);
        }
    }

    // ─── ENTRY flag (movement restriction) ───────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getWorld() == to.getWorld()
                && GeometryUtil.blockToChunk(from.getBlockX()) == GeometryUtil.blockToChunk(to.getBlockX())
                && GeometryUtil.blockToChunk(from.getBlockZ()) == GeometryUtil.blockToChunk(to.getBlockZ())) return;
        Player player = event.getPlayer();
        if (!protection.isAllowed(to, player.getUniqueId(), RegionFlag.ENTRY)) {
            event.setCancelled(true);
            player.sendActionBar(Messages.PROTECTION.text(Text.ENTRY_DENIED));
            notifyAdminBypassHint(player);
            logDenied(LoggingPaths.PROTECTION_ENTRY, player, to, RegionFlag.ENTRY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobMove(EntityMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        RegionFlag flag;
        if (event.getEntity() instanceof Enemy) {
            flag = RegionFlag.HOSTILE_MOB_ENTRY;
        } else if (event.getEntity() instanceof Mob) {
            flag = RegionFlag.PASSIVE_MOB_ENTRY;
        } else {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getWorld() == to.getWorld()
                && GeometryUtil.blockToChunk(from.getBlockX()) == GeometryUtil.blockToChunk(to.getBlockX())
                && GeometryUtil.blockToChunk(from.getBlockZ()) == GeometryUtil.blockToChunk(to.getBlockZ())) return;

        if (!protection.isAllowed(to, null, flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location destination = event.getTo();
        if (destination == null) return;
        if (!protection.isAllowed(destination, player.getUniqueId(), RegionFlag.ENTRY)) {
            event.setCancelled(true);
            player.sendActionBar(Messages.PROTECTION.text(Text.ENTRY_DENIED));
            notifyAdminBypassHint(player);
            logDenied(LoggingPaths.PROTECTION_ENTRY, player, destination, RegionFlag.ENTRY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location respawnLoc = event.getRespawnLocation();
        org.bukkit.World world = respawnLoc.getWorld();
        if (world == null) return;
        int chunkX = GeometryUtil.blockToChunk(respawnLoc.getBlockX());
        int chunkZ = GeometryUtil.blockToChunk(respawnLoc.getBlockZ());
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        if (!protection.isAllowed(respawnLoc, event.getPlayer().getUniqueId(), RegionFlag.ENTRY)) {
            event.setRespawnLocation(world.getSpawnLocation());
        }
    }

    // ─── Selection wand helpers ───────────────────────────────────────────────

    private boolean isWand(ItemStack item) {
        if (item == null) return false;
        return item.getType() == plugin.getProtectionSelectionTool();
    }

    private void logDenied(String path, Player player, Location location, RegionFlag flag) {
        ConsoleEventLog eventLog = plugin.getConsoleEventLog();
        if (eventLog == null || !eventLog.enabled(path)) return;
        eventLog.logHot(path, new HotPathEventBuffer.HotKey(
                        player.getUniqueId(), protection.loggingRegionId(location), flag),
                player.getName());
    }

    private void notifyAdminBypassHint(Player player) {
        if (player.hasPermission(me.beeliebub.tweaks.permissions.Permissions.PROTECTION_ADMIN)
                && !protection.isProtectionBypassEnabled(player.getUniqueId())) {
            player.sendActionBar(Messages.PROTECTION.text(Text.BYPASS_REQUIRED));
        }
    }

    private static void announce(Player player, Text label, long chunkKey, RegionSelection sel) {
        int cx = GeometryUtil.chunkX(chunkKey);
        int cz = GeometryUtil.chunkZ(chunkKey);
        player.sendMessage(Messages.PROTECTION.text(Text.SELECTION_POINT,
                Messages.PROTECTION.value(label), cx, cz));
        if (sel.isComplete()) {
            int cx1 = GeometryUtil.chunkX(sel.pos1());
            int cz1 = GeometryUtil.chunkZ(sel.pos1());
            int cx2 = GeometryUtil.chunkX(sel.pos2());
            int cz2 = GeometryUtil.chunkZ(sel.pos2());
            int chunks = (Math.abs(cx1 - cx2) + 1) * (Math.abs(cz1 - cz2) + 1);
            player.sendMessage(Messages.PROTECTION.text(Text.SELECTION_COVERAGE,
                    chunks, chunks == 1 ? "" : "s"));
        }
    }
}
