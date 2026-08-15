package me.beeliebub.tweaks.enchantments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.EnchantmentResolver;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentService;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Destructive bundle recovery for augment ledgers, including legacy migration. */
public final class DisenchantingBundle implements Listener {

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;
    private final AugmentService augments;
    private final SpawnerPickup legacySpawnerPickup;
    private final EggCollector legacyEggCollector;
    private final Enchantment configuredSpawnerPickup;
    private final Enchantment configuredEggCollector;

    /** Compatibility constructor retained for the pre-augment tests and legacy callers. */
    public DisenchantingBundle(Tweaks plugin, QualityRegistry qualityRegistry,
                               SpawnerPickup spawnerPickup, EggCollector eggCollector) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
        this.augments = null;
        this.legacySpawnerPickup = spawnerPickup;
        this.legacyEggCollector = eggCollector;
        this.configuredSpawnerPickup = null;
        this.configuredEggCollector = null;
    }

    public DisenchantingBundle(Tweaks plugin, QualityRegistry qualityRegistry, AugmentService augments) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
        this.augments = augments;
        this.legacySpawnerPickup = null;
        this.legacyEggCollector = null;
        this.configuredSpawnerPickup = EnchantmentResolver.resolve(plugin, "spawner-pickup", "spawner pickup");
        this.configuredEggCollector = EnchantmentResolver.resolve(plugin, "egg-collector", "egg collector");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT || !(event.getWhoClicked() instanceof Player player)) return;
        if (augments != null && !augments.enabled()) return;
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isBundleWithLore(clicked) && isCandidate(cursor)) {
            event.setCancelled(true);
            if (isRestricted(cursor)) { refuse(player); return; }
            process(player, cursor, clicked);
        } else if (isBundleWithLore(cursor) && isCandidate(clicked)) {
            event.setCancelled(true);
            if (isRestricted(clicked)) { refuse(player); return; }
            process(player, clicked, cursor);
        }
    }

    private boolean isCandidate(ItemStack item) {
        return item != null && !item.isEmpty()
                && (augments == null ? !item.getEnchantments().isEmpty() : AugmentService.hasAugmentsOrLegacy(item));
    }

    private boolean isRestricted(ItemStack item) {
        if (item == null) return false;
        Enchantment sp = legacySpawnerPickup == null ? configuredSpawnerPickup : legacySpawnerPickup.getEnchantment();
        Enchantment ec = legacyEggCollector == null ? configuredEggCollector : legacyEggCollector.getEnchantment();
        return (sp != null && item.containsEnchantment(sp)) || (ec != null && item.containsEnchantment(ec));
    }

    private void refuse(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        player.sendMessage(Messages.TOOLS.augmentIncompatible());
    }

    private boolean isBundleWithLore(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore();
    }

    private void process(Player player, ItemStack item, ItemStack bundle) {
        if (augments == null) {
            for (Enchantment enchantment : item.getEnchantments().keySet()) item.removeEnchantment(enchantment);
            bundle.setAmount(bundle.getAmount() - 1);
            return;
        }

        List<AugmentEntry> existing = new ArrayList<>(augments.entries(item));
        boolean needsLegacyMigration = !item.getEnchantments().isEmpty()
                && (!augments.ledger().migrated(item) || existing.isEmpty());
        if (needsLegacyMigration) {
            List<ItemStack> possible = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                possible.add(augments.gemItem().create(entry.getKey(), entry.getValue()));
            }
            if (!augments.canFit(player, possible)) {
                player.sendMessage(Messages.TOOLS.augmentBundleRefused());
                return;
            }
            augments.migrateLegacyForRecovery(item);
        }

        List<AugmentEntry> entries = new ArrayList<>(augments.entries(item));
        if (entries.isEmpty()) {
            refuse(player);
            return;
        }
        entries.sort(Comparator.comparingInt(this::tierOrdinal).reversed());
        List<ItemStack> recovered = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            AugmentEntry entry = entries.get(i);
            if (ThreadLocalRandom.current().nextDouble() * 100.0 >= Math.max(0, 100 - i * 20)) continue;
            Enchantment enchantment = registry().get(entry.enchantmentKey());
            if (enchantment != null) recovered.add(augments.gemItem().create(enchantment, entry.level()));
        }
        if (!augments.canFit(player, recovered)) {
            player.sendMessage(Messages.TOOLS.augmentBundleRefused());
            return;
        }
        item.setAmount(0);
        bundle.setAmount(bundle.getAmount() - 1);
        augments.addGems(player, recovered);
        player.sendMessage(Messages.TOOLS.augmentBundleResult(recovered.size()));
    }

    private int tierOrdinal(AugmentEntry entry) {
        Enchantment enchantment = registry().get(entry.enchantmentKey());
        QualityTier tier = enchantment == null || qualityRegistry == null ? null : qualityRegistry.getTier(enchantment);
        return tier == null ? -1 : tier.ordinal();
    }

    private org.bukkit.Registry<Enchantment> registry() {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
    }
}
