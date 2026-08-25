package me.beeliebub.tweaks.tools.xp;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Level;

/** Converts XP-orb pickup XP into economy credit while a Mending item is equipped. */
public final class MendingPayoutListener implements Listener {

    private final Tweaks plugin;
    private final EconomyManager economy;
    private final XpSettings settings;

    public MendingPayoutListener(Tweaks plugin, EconomyManager economy, XpSettings settings) {
        this.plugin = plugin;
        this.economy = economy;
        this.settings = settings;
    }

    /**
     * An experience orb repairs a Mending item before granting its remaining experience, so
     * cancelling the mend is the only way to route the orb's full value into the payout.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent event) {
        if (settings.mendingEnabled() && settings.mending() != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        int original = event.getAmount();
        long dollars;
        try {
            if (original <= 0 || !settings.mendingEnabled()) return;
            Enchantment mending = settings.mending();
            if (mending == null || !hasMending(event.getPlayer(), mending)) return;

            try {
                dollars = Math.multiplyExact((long) original, (long) settings.dollarsPerXp());
            } catch (ArithmeticException e) {
                plugin.getLogger().log(Level.SEVERE, "Mending payout overflow for player "
                        + event.getPlayer().getUniqueId() + "; XP was not consumed.", e);
                event.setAmount(original);
                return;
            }
            UUID playerId = event.getPlayer().getUniqueId();
            if (economy.addBalance(playerId, dollars) != BalanceMutationResult.APPLIED) {
                plugin.getLogger().severe("Mending payout rejected for player " + playerId + "; XP was not consumed.");
                event.setAmount(original);
                return;
            }
            event.setAmount(0);
        } catch (RuntimeException failure) {
            event.setAmount(original);
            plugin.getLogger().log(Level.SEVERE, "Mending payout failed for player "
                    + event.getPlayer().getUniqueId() + " with " + original
                    + " XP; XP was not consumed.", failure);
            return;
        }
        if (dollars > 0) {
            try {
                event.getPlayer().sendMessage(Messages.TOOLS.mendingPaid(original, dollars));
            } catch (RuntimeException notificationFailure) {
                plugin.getLogger().log(Level.WARNING, "Mending payout notification failed for player "
                        + event.getPlayer().getUniqueId() + ".", notificationFailure);
            }
        }
    }

    private static boolean hasMending(Player player, Enchantment mending) {
        if (containsMending(player.getInventory().getItemInMainHand(), mending)
                || containsMending(player.getInventory().getItemInOffHand(), mending)) return true;
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null) return false;
        for (ItemStack item : armor) {
            if (containsMending(item, mending)) return true;
        }
        return false;
    }

    private static boolean containsMending(ItemStack item, Enchantment mending) {
        return item != null && !item.isEmpty() && item.containsEnchantment(mending);
    }
}
