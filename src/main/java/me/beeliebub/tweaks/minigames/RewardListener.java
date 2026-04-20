package me.beeliebub.tweaks.minigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class RewardListener implements Listener {

    private final RewardManager rewardManager;
    private final RewardCommand rewardCommand;

    public RewardListener(RewardManager rewardManager, RewardCommand rewardCommand) {
        this.rewardManager = rewardManager;
        this.rewardCommand = rewardCommand;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String rewardName = rewardCommand.getEditingSessions().remove(player.getUniqueId());
        if (rewardName == null) return;

        ItemStack[] contents = event.getInventory().getContents();
        rewardManager.setRewardItems(rewardName, contents);
        player.sendMessage(Component.text("Reward '" + rewardName + "' updated!").color(NamedTextColor.GREEN));
    }
}