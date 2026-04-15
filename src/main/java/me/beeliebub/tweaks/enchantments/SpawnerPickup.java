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

public class SpawnerPickup implements Listener {

    private static final double DROP_CHANCE = 0.20;
    private static final int BREAK_AT = 5;
    private static final String LORE_PREFIX = "Spawner Uses Remaining: ";

    private final Enchantment enchantment;
    private final NamespacedKey counterKey;

    public SpawnerPickup(Tweaks plugin) {
        String raw = plugin.getConfig().getString("spawner-pickup");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.counterKey = new NamespacedKey(plugin, "spawner_pickup_count");
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'spawner-pickup' key configured; spawner-pickup enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid spawner-pickup key '" + raw + "'; spawner-pickup enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Spawner-pickup enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        if (ThreadLocalRandom.current().nextDouble() >= DROP_CHANCE) return;

        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SPAWNER));

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int count = pdc.getOrDefault(counterKey, PersistentDataType.INTEGER, 0) + 1;

        if (count >= BREAK_AT) {
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            pdc.set(counterKey, PersistentDataType.INTEGER, count);
            updateUsesLore(meta, BREAK_AT - count);
            tool.setItemMeta(meta);
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