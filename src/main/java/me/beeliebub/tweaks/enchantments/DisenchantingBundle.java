package me.beeliebub.tweaks.enchantments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom feature for bundles with lore.
 * Placing an enchanted item into such a bundle strips all enchantments from the item.
 * Enchantments are processed from highest tier to lowest.
 * The first one is always given back as an enchanted book.
 * Subsequent enchants have a decreasing chance (80%, 60%...) to be given back.
 * If a roll fails, no book is given, and the chance resets to 80% for the next enchant.
 */
public class DisenchantingBundle implements Listener {

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;
    private final SpawnerPickup spawnerPickup;
    private final EggCollector eggCollector;

    public DisenchantingBundle(Tweaks plugin, QualityRegistry qualityRegistry, SpawnerPickup spawnerPickup, EggCollector eggCollector) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
        this.spawnerPickup = spawnerPickup;
        this.eggCollector = eggCollector;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Case 1: Clicking a bundle with an enchanted item on cursor
        if (isBundleWithLore(clicked) && isEnchanted(cursor)) {
            event.setCancelled(true);
            if (isRestricted(cursor)) {
                sendRestrictedMessage(player);
                return;
            }
            processDisenchant(player, cursor, clicked);
        }
        // Case 2: Clicking an enchanted item with a bundle on cursor
        else if (isBundleWithLore(cursor) && isEnchanted(clicked)) {
            event.setCancelled(true);
            if (isRestricted(clicked)) {
                sendRestrictedMessage(player);
                return;
            }
            processDisenchant(player, clicked, cursor);
        }
        }

        // Spawner Pickup and Egg Collector store their remaining-uses counter on the tool itself,
        // and the tool self-destructs after 5 successful uses. Stripping them via the bundle would
        // let players sidestep that wear-down cost, so we refuse the operation entirely.
        private boolean isRestricted(ItemStack item) {
        if (item == null) return false;
        Enchantment sp = spawnerPickup == null ? null : spawnerPickup.getEnchantment();
        Enchantment ec = eggCollector == null ? null : eggCollector.getEnchantment();
        if (sp != null && item.containsEnchantment(sp)) return true;
        if (ec != null && item.containsEnchantment(ec)) return true;
        return false;
        }

        private void sendRestrictedMessage(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        player.sendMessage(Component.text("The bundle recoils — that tool's magic resists extraction.", NamedTextColor.RED));
        }

        private boolean isBundleWithLore(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore();
        }

        private boolean isEnchanted(ItemStack item) {
        return item != null && !item.getEnchantments().isEmpty();
        }

        private void processDisenchant(Player player, ItemStack item, ItemStack bundle) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        List<EnchantEntry> sortedEnchants = new ArrayList<>();

        for (var entry : enchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();
            QualityTier tier = qualityRegistry.getTier(ench);
            sortedEnchants.add(new EnchantEntry(ench, level, tier));
        }

        // Sort by tier descending, then shuffle within same tier
        sortedEnchants.sort(Comparator.comparing(EnchantEntry::tier, (t1, t2) -> {
            int v1 = (t1 == null) ? -1 : t1.ordinal();
            int v2 = (t2 == null) ? -1 : t2.ordinal();
            return Integer.compare(v2, v1);
        }));

        // Shuffle within tiers to handle the "randomly select one if multiple at same highest tier"
        List<EnchantEntry> finalizedOrder = new ArrayList<>();
        int i = 0;
        while (i < sortedEnchants.size()) {
            QualityTier currentTier = sortedEnchants.get(i).tier();
            List<EnchantEntry> sameTier = new ArrayList<>();
            while (i < sortedEnchants.size() && sortedEnchants.get(i).tier() == currentTier) {
                sameTier.add(sortedEnchants.get(i));
                i++;
            }
            Collections.shuffle(sameTier);
            finalizedOrder.addAll(sameTier);
        }

        // Process rolls
        double currentChance = 1.0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> booksToGive = new ArrayList<>();
        int successfulRolls = 0;
        int failedRolls = 0;

        for (int j = 0; j < finalizedOrder.size(); j++) {
            EnchantEntry entry = finalizedOrder.get(j);
            boolean success = random.nextDouble() < currentChance;

            if (success) {
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                meta.addStoredEnchant(entry.enchant(), entry.level(), true);
                book.setItemMeta(meta);
                booksToGive.add(book);

                successfulRolls++;
                // Next chance decreases if successful
                if (j == 0) {
                    currentChance = 0.8; // 100% -> 80%
                } else {
                    currentChance = Math.max(0.0, currentChance - 0.2);
                }
            } else {
                failedRolls++;
                // If fails, chance stays at current level (requested behavior)
            }
        }

        // Strip all enchants
        for (Enchantment ench : enchants.keySet()) {
            item.removeEnchantment(ench);
        }

        // Consume bundle
        bundle.setAmount(bundle.getAmount() - 1);

        // Give books
        for (ItemStack book : booksToGive) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
            for (ItemStack remaining : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }


        // Feedback
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.sendMessage(Component.text("The bundle consumes the magic of your item...", NamedTextColor.DARK_PURPLE)
                .append(Component.newline())
                .append(Component.text("Extracted " + successfulRolls + " enchantment(s).", NamedTextColor.LIGHT_PURPLE))
                .append(failedRolls > 0 ? Component.text(" (" + failedRolls + " lost to the void)", NamedTextColor.GRAY) : Component.empty()));
    }

    private record EnchantEntry(Enchantment enchant, int level, QualityTier tier) {}
}
