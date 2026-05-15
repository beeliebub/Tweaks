package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

// Translates left/right wand clicks into RegionSelection updates.
//
// Click semantics (WorldEdit-style):
//   * LEFT_CLICK_BLOCK  → Pos1
//   * RIGHT_CLICK_BLOCK → Pos2
// Both are cancelled at LOWEST priority so the click neither breaks the
// block (survival or creative) nor activates it (a wand-clicked button
// must not toggle the button). We additionally cancel BlockBreakEvent for
// the same reason — creative mode breaks blocks instantly via a separate
// event path that PlayerInteractEvent cancellation doesn't cover.
//
// Corner restriction: only blocks at chunk-corner coordinates (xMod and
// zMod both ∈ {0, 15}) accept the click. Non-corner clicks emit a chat
// hint and leave the selection untouched. The constraint exists to give
// the player a visible "snap" — clicking the middle of a chunk would
// resolve to the same chunk, but the user would have no tactile sense of
// which chunk they were actually anchoring.
public final class SelectionWandListener implements Listener {

    private final Tweaks plugin;
    private final RegionSelectionManager selections;

    public SelectionWandListener(Tweaks plugin, RegionSelectionManager selections) {
        this.plugin = plugin;
        this.selections = selections;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!isWand(event.getItem())) return;

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // We're handling this click; suppress block-break / block-use side effects.
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!GeometryUtil.isChunkCornerBlock(block.getX(), block.getZ())) {
            player.sendMessage(Component.text(
                    "That block isn't a chunk corner. Click the 2x2 corner of any chunk.",
                    NamedTextColor.RED));
            return;
        }

        long chunkKey = GeometryUtil.chunkKey(
                GeometryUtil.blockToChunk(block.getX()),
                GeometryUtil.blockToChunk(block.getZ()));
        RegionSelection sel = selections.getOrCreate(player, block.getWorld());

        if (left) {
            sel.setPos1(chunkKey);
            announce(player, "Pos1", chunkKey, sel);
        } else {
            sel.setPos2(chunkKey);
            announce(player, "Pos2", chunkKey, sel);
        }
    }

    // Creative mode left-clicks bypass PlayerInteractEvent cancellation and
    // fire BlockBreakEvent directly. Mirror the cancel there so the wand
    // never breaks blocks regardless of game mode.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null) return false;
        return item.getType() == plugin.getProtectionSelectionTool();
    }

    private static void announce(Player player, String label, long chunkKey, RegionSelection sel) {
        int cx = GeometryUtil.chunkX(chunkKey);
        int cz = GeometryUtil.chunkZ(chunkKey);
        player.sendMessage(Component.text(label + " set at chunk (" + cx + ", " + cz + ").",
                NamedTextColor.GREEN));
        if (sel.isComplete()) {
            int cx1 = GeometryUtil.chunkX(sel.pos1());
            int cz1 = GeometryUtil.chunkZ(sel.pos1());
            int cx2 = GeometryUtil.chunkX(sel.pos2());
            int cz2 = GeometryUtil.chunkZ(sel.pos2());
            int chunks = (Math.abs(cx1 - cx2) + 1) * (Math.abs(cz1 - cz2) + 1);
            player.sendMessage(Component.text(
                    "Selection covers " + chunks + " chunk" + (chunks == 1 ? "" : "s") + ". Run /region claim <name> to commit.",
                    NamedTextColor.GRAY));
        }
    }
}
