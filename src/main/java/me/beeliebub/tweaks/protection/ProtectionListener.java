package me.beeliebub.tweaks.protection;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumSet;
import java.util.Set;

// Wires the hybrid protection lookups into actual Bukkit events.
//
// Event-priority rationale (from the architectural plan):
//   * BlockBreakEvent / BlockPlaceEvent → LOWEST. Cancel before custom-tool
//     plugins waste cycles computing drop tables or applying durability for
//     actions that will not occur. ignoreCancelled yields to higher-order
//     protection plugins (WorldGuard, spawn protection) that may have
//     already vetoed the event.
//   * PlayerInteractEvent → LOW. Anti-cheat / packet validators run at
//     LOWEST to validate line-of-sight + reach; we run one step later so
//     the event we evaluate represents a physically-valid interaction.
//   * Explosion events → LOWEST. We mutate the block list rather than
//     cancelling, so other plugins on higher priorities still see a
//     coherent (but filtered) blockList.
public final class ProtectionListener implements Listener {

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

    // Materials that trigger redstone state changes when right-clicked.
    // Routed to REDSTONE.
    private static final Set<Material> REDSTONE_INPUTS = EnumSet.of(
            Material.LEVER
    );

    private final ProtectionManager protection;

    public ProtectionListener(ProtectionManager protection) {
        this.protection = protection;
    }

    // ------------------------------------------------------------------
    // 4.2 Block Event Listeners
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protection.isBlockActionAllowed(
                event.getBlock().getLocation(),
                event.getPlayer().getUniqueId(),
                event.getBlock().getType(),
                RegionFlag.BLOCK_BREAK)) {
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
        }
    }

    // ------------------------------------------------------------------
    // 4.3 Interaction Listeners
    // ------------------------------------------------------------------

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
        }
    }

    // Map a clicked block's material to the protection flag that gates it.
    // Returns null for materials we don't gate (e.g. crafting table —
    // accessing it doesn't expose anything region-bound).
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

    // ------------------------------------------------------------------
    // 4.4 Explosion Listeners
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    // Strip protected blocks from the explosion's destruction list rather
    // than cancelling the whole event — TNT in unprotected wilderness
    // adjacent to a claim should still pop the wilderness blocks.
    private void filterExplosion(java.util.List<Block> blocks) {
        blocks.removeIf(b -> !protection.isAllowed(
                b.getLocation(), null, RegionFlag.EXPLOSION));
    }
}
