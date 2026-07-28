package me.beeliebub.tweaks.minigames;

import me.beeliebub.tweaks.core.Messages;
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
            player.sendMessage(Messages.MINIGAMES.rewardClaimReminderSpacer());
            player.sendMessage(Messages.MINIGAMES.rewardClaimReminder(pending.size()));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String rewardName = rewardCommand.getEditingSessions().remove(player.getUniqueId());
        if (rewardName == null) return;

        ItemStack[] contents = event.getInventory().getContents();
        rewardManager.setRewardItems(rewardName, contents);
        player.sendMessage(Messages.MINIGAMES.rewardUpdated(rewardName));
    }
}
