package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

// Gives a 0.5% chance to drop a spawn egg when killing a mob with this enchantment.
// The tool breaks after 5 successful egg drops, with remaining uses shown in lore.
public class EggCollector implements Listener {

    private static final int BREAK_AT = 5;
    private static final String LORE_PREFIX = "Egg Collector Uses Remaining: ";

    private final Tweaks plugin;
    private final Enchantment enchantment;
    private final NamespacedKey counterKey;

    public EggCollector(Tweaks plugin) {
        this.plugin = plugin;
        String raw = plugin.getConfig().getString("egg-collector");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.counterKey = new NamespacedKey(plugin, "egg_collector_count");
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'egg-collector' key configured; egg-collector enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid egg-collector key '" + raw + "'; egg-collector enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Egg-collector enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (enchantment == null) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack tool = killer.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        double dropChance = plugin.getConfig().getDouble("egg-collector-drop-chance", 0.5) / 100.0;
        if (ThreadLocalRandom.current().nextDouble() >= dropChance) return;

        EntityType type = event.getEntityType();
        Material spawnEgg = Material.matchMaterial(type.getKey().getKey() + "_spawn_egg");
        if (spawnEgg == null) return;

        event.getDrops().add(new ItemStack(spawnEgg));

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int count = pdc.getOrDefault(counterKey, PersistentDataType.INTEGER, 0) + 1;

        if (count >= BREAK_AT) {
            killer.getInventory().setItemInMainHand(null);
            killer.getWorld().playSound(killer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            pdc.set(counterKey, PersistentDataType.INTEGER, count);
            updateUsesLore(meta, BREAK_AT - count);
            tool.setItemMeta(meta);
        }
    }

    private void updateUsesLore(ItemMeta meta, int remaining) {
        List<Component> existing = meta.lore();
        List<Component> lore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith(LORE_PREFIX));
        lore.add(Component.text(LORE_PREFIX + remaining)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }
}