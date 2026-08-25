package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingPaths;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

// Gives a 20% chance to drop a spawner block when mined with this enchantment.
// The tool breaks after 5 successful spawner pickups, with remaining uses shown in lore.
public class SpawnerPickup implements Listener {

    private static final String LORE_PREFIX = "Spawner Uses Remaining: ";

    private final Tweaks plugin;
    private final Enchantment enchantment;
    private final NamespacedKey counterKey;

    public SpawnerPickup(Tweaks plugin) {
        this.plugin = plugin;
        this.enchantment = EnchantmentResolver.resolve(plugin, "spawner-pickup", "spawner pickup");
        this.counterKey = new NamespacedKey(plugin, "spawner_pickup_count");
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }


    // Package-private (not private) so a config knob test can exercise the percent/clamp
    // conversion directly without widening visibility to public.
    double dropChance() {
        double percent = plugin.getConfig().getDouble("enchantments.spawner-pickup.drop-chance-percent", 20.0);
        return Math.max(0.0, Math.min(100.0, percent)) / 100.0;
    }

    int breakAt() {
        return Math.max(1, plugin.getConfig().getInt("enchantments.spawner-pickup.uses", 5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        if (ThreadLocalRandom.current().nextDouble() >= dropChance()) return;

        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SPAWNER));

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int count = pdc.getOrDefault(counterKey, PersistentDataType.INTEGER, 0) + 1;

        int breakAt = breakAt();
        if (count >= breakAt) {
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            pdc.set(counterKey, PersistentDataType.INTEGER, count);
            updateUsesLore(meta, breakAt - count);
            tool.setItemMeta(meta);
        }
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) {
            String actorName = player.getName();
            eventLog.logHot(LoggingPaths.ENCHANTMENTS_SPAWNER,
                    new HotPathEventBuffer.HotKey(player.getUniqueId(), "spawner", null), actorName);
        }
    }

    private void updateUsesLore(ItemMeta meta, int remaining) {
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith(LORE_PREFIX));
        lore.add(Component.text(LORE_PREFIX + remaining)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }
}
