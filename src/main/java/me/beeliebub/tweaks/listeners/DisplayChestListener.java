package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.managers.DisplayChestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DisplayChestListener implements Listener {

    private final DisplayChestManager displayChestManager;

    public DisplayChestListener(DisplayChestManager displayChestManager) {
        this.displayChestManager = displayChestManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        boolean isSetup = displayChestManager.isSetupMode(player.getUniqueId());
        boolean isRemoval = displayChestManager.isRemovalMode(player.getUniqueId());

        if (!isSetup && !isRemoval) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getState() instanceof Chest) {
                event.setCancelled(true);
                if (isSetup) {
                    if (displayChestManager.isEmbedSide(player.getUniqueId())) {
                        // Side mode: embed on the clicked face. Fall back to a
                        // sensible default if the event somehow carries no
                        // face (shouldn't happen on a RIGHT/LEFT_CLICK_BLOCK,
                        // but be defensive).
                        BlockFace face = event.getBlockFace();
                        if (face == null) face = BlockFace.UP;
                        displayChestManager.processChestSide(block, face, player);
                    } else {
                        displayChestManager.processChest(block, player);
                    }
                    player.sendMessage(Component.text("Display chest generated/updated!").color(NamedTextColor.GREEN));
                } else {
                    displayChestManager.removeDisplay(block);
                    player.sendMessage(Component.text("Display chest removed!").color(NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (displayChestManager.isSetupMode(event.getPlayer().getUniqueId())) {
            displayChestManager.toggleSetupMode(event.getPlayer().getUniqueId());
        }
        if (displayChestManager.isRemovalMode(event.getPlayer().getUniqueId())) {
            displayChestManager.toggleRemovalMode(event.getPlayer().getUniqueId());
        }
    }
}