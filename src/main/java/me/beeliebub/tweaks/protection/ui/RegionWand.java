package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Creates and gives the configured region-selection wand. */
public final class RegionWand {

    private static final String SELECTION_TOOL_PATH = "protection.selection-tool";
    private static final Material DEFAULT_TOOL = Material.STONE_AXE;

    private final JavaPlugin plugin;

    public RegionWand(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Gives one selection wand to {@code player}, or drops it when the inventory is full. */
    public void give(Player player) {
        Objects.requireNonNull(player, "player");
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> giveOnMainThread(player));
            return;
        }
        giveOnMainThread(player);
    }

    private void giveOnMainThread(Player player) {
        ItemStack wand = new ItemStack(selectionTool());
        wand.editMeta(meta -> meta.displayName(Messages.PROTECTION.text(Text.WAND_NAME)
                .decoration(TextDecoration.ITALIC, false)));

        var overflow = player.getInventory().addItem(wand);
        if (!overflow.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), wand);
            player.sendMessage(Messages.PROTECTION.text(Text.WAND_FULL));
        } else {
            player.sendMessage(Messages.PROTECTION.text(Text.WAND_SUCCESS));
        }
    }

    private Material selectionTool() {
        if (plugin instanceof Tweaks tweaks) {
            Material liveTool = tweaks.getProtectionSelectionTool();
            if (liveTool != null) return liveTool;
        }

        String configuredTool = plugin.getConfig().getString(SELECTION_TOOL_PATH, DEFAULT_TOOL.name());
        Material resolvedTool = Material.matchMaterial(configuredTool);
        return resolvedTool == null ? DEFAULT_TOOL : resolvedTool;
    }
}
