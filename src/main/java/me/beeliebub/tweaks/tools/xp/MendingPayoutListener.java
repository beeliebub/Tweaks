package me.beeliebub.tweaks.tools.xp;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceMutationResult;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.xpbottle.ExperienceManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        int original = event.getAmount();
        if (original <= 0 || !settings.mendingEnabled()) return;
        Enchantment mending = settings.mending();
        if (mending == null || !hasMending(event.getPlayer(), mending)) return;

        long dollars;
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
        if (dollars > 0) event.getPlayer().sendMessage(Messages.TOOLS.mendingPaid(original, dollars));
    }

    private static boolean hasMending(Player player, Enchantment mending) {
        List<ItemStack> items = new ArrayList<>();
        items.add(player.getInventory().getItemInMainHand());
        items.add(player.getInventory().getItemInOffHand());
        items.addAll(List.of(player.getInventory().getArmorContents()));
        return items.stream().anyMatch(item -> item != null && !item.isEmpty() && item.containsEnchantment(mending));
    }
}
