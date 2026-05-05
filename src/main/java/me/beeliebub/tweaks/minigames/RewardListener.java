package me.beeliebub.tweaks.minigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

// Saves reward items when an admin closes the reward-editing chest inventory,
// and notifies players of unclaimed rewards on join.
public class RewardListener implements Listener {

    private final RewardManager rewardManager;
    private final RewardCommand rewardCommand;

    public RewardListener(RewardManager rewardManager, RewardCommand rewardCommand) {
        this.rewardManager = rewardManager;
        this.rewardCommand = rewardCommand;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<String> pending = rewardManager.getPendingRewards(player.getUniqueId());

        if (!pending.isEmpty()) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("You have " + pending.size() + " unclaimed reward(s)! Type ", NamedTextColor.GOLD)
                    .append(Component.text("/reward claim", NamedTextColor.YELLOW))
                    .append(Component.text(" to collect them.", NamedTextColor.GOLD)));
        }
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