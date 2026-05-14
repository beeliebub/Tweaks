package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// When a player throws a splash potion carrying dqc.dice:dice_converter, suppress that
// player's pickup of splash_potion item entities for a short window. Pattern mirrors
// ItemFilter but is intentionally separate: this is a transient timer per-thrower
// rather than a persisted per-player whitelist/blacklist.
public class DiceConverterListener implements Listener {

    static final long BLOCK_DURATION_MS = 2000L;

    private final Enchantment enchantment;
    private final Map<UUID, Long> pickupBlockedUntil = new ConcurrentHashMap<>();

    public DiceConverterListener(Tweaks plugin) {
        this.enchantment = resolveEnchantment(plugin);
    }

    private Enchantment resolveEnchantment(Tweaks plugin) {
        String raw = plugin.getConfig().getString("dice-converter");
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'dice-converter' key configured; dice converter pickup prevention disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().warning("Invalid dice-converter key '" + raw + "'; pickup prevention disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Dice converter enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (enchantment == null) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        ItemStack item = potion.getItem();
        if (item.getType() != Material.SPLASH_POTION) return;
        if (!(potion.getShooter() instanceof Player player)) return;
        if (!item.containsEnchantment(enchantment)) return;
        pickupBlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + BLOCK_DURATION_MS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getType() != Material.SPLASH_POTION) return;
        Long until = pickupBlockedUntil.get(player.getUniqueId());
        if (until == null) return;
        if (until <= System.currentTimeMillis()) {
            pickupBlockedUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pickupBlockedUntil.remove(event.getPlayer().getUniqueId());
    }

    // Test-only accessors.
    boolean isPickupBlocked(UUID playerId, long nowMillis) {
        Long until = pickupBlockedUntil.get(playerId);
        return until != null && until > nowMillis;
    }

    void markBlocked(UUID playerId, long blockedUntilMillis) {
        pickupBlockedUntil.put(playerId, blockedUntilMillis);
    }
}
