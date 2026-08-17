package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.EggCollector;
import me.beeliebub.tweaks.enchantments.EnchantmentResolver;
import me.beeliebub.tweaks.enchantments.SpawnerPickup;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Destructive bundle recovery for augment ledgers, including legacy migration. */
public final class DisenchantingBundle implements Listener {

    private final QualityRegistry qualityRegistry;
    private final AugmentService augments;
    private final SpawnerPickup legacySpawnerPickup;
    private final EggCollector legacyEggCollector;
    private final Enchantment configuredSpawnerPickup;
    private final Enchantment configuredEggCollector;
    private final Random random;

    public DisenchantingBundle(Tweaks plugin, QualityRegistry qualityRegistry,
                               SpawnerPickup spawnerPickup, EggCollector eggCollector) {
        this.qualityRegistry = qualityRegistry;
        this.augments = null;
        this.legacySpawnerPickup = spawnerPickup;
        this.legacyEggCollector = eggCollector;
        this.configuredSpawnerPickup = null;
        this.configuredEggCollector = null;
        this.random = ThreadLocalRandom.current();
    }

    public DisenchantingBundle(Tweaks plugin, QualityRegistry qualityRegistry, AugmentService augments) {
        this(plugin, qualityRegistry, augments, ThreadLocalRandom.current());
    }

    public DisenchantingBundle(Tweaks plugin, QualityRegistry qualityRegistry,
                               AugmentService augments, Random random) {
        this.qualityRegistry = qualityRegistry;
        this.augments = augments;
        this.legacySpawnerPickup = null;
        this.legacyEggCollector = null;
        this.configuredSpawnerPickup = EnchantmentResolver.resolve(plugin, "spawner-pickup", "spawner pickup");
        this.configuredEggCollector = EnchantmentResolver.resolve(plugin, "egg-collector", "egg collector");
        this.random = Objects.requireNonNull(random, "random");
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
                && (augments == null ? !item.getEnchantments().isEmpty()
                : AugmentService.hasMeaningfulAugmentState(augments.ledger(), item));
    }

    private boolean isRestricted(ItemStack item) {
        if (item == null) return false;
        Enchantment sp = legacySpawnerPickup == null ? configuredSpawnerPickup : legacySpawnerPickup.getEnchantment();
        Enchantment ec = legacyEggCollector == null ? configuredEggCollector : legacyEggCollector.getEnchantment();
        if ((sp != null && item.containsEnchantment(sp)) || (ec != null && item.containsEnchantment(ec))) return true;
        if (augments == null) return false;
        for (AugmentEntry entry : augments.entries(item)) {
            Enchantment enchantment = registry().get(entry.enchantmentKey());
            if ((sp != null && sp.equals(enchantment)) || (ec != null && ec.equals(enchantment))) return true;
        }
        return false;
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

        AugmentService.RecoveryPlan plan = augments.recoveryPlan(item);
        if (!plan.valid()) {
            refuse(player);
            return;
        }
        List<AugmentEntry> entries = new ArrayList<>(plan.entries());
        entries.sort(Comparator.comparingInt(this::tierOrdinal).reversed()
                .thenComparing(entry -> entry.enchantmentKey().toString()));
        List<ItemStack> possible = new ArrayList<>();
        for (AugmentEntry entry : entries) {
            Enchantment enchantment = registry().get(entry.enchantmentKey());
            if (enchantment == null) {
                refuse(player);
                return;
            }
            possible.add(augments.gemItem().create(enchantment, entry.level()));
        }
        if (!augments.canFit(player, possible, List.of(item))) {
            player.sendMessage(Messages.TOOLS.augmentBundleRefused());
            return;
        }

        List<AugmentEntry> recoveredEntries = recoverEntries(entries);
        List<ItemStack> recovered = new ArrayList<>();
        for (AugmentEntry entry : recoveredEntries) {
            Enchantment enchantment = registry().get(entry.enchantmentKey());
            if (enchantment != null) recovered.add(augments.gemItem().create(enchantment, entry.level()));
        }
        if (!augments.migrateLegacyForRecovery(item, plan)) {
            refuse(player);
            return;
        }
        item.setAmount(0);
        bundle.setAmount(bundle.getAmount() - 1);
        augments.addGems(player, recovered);
        player.sendMessage(Messages.TOOLS.augmentBundleResult(recovered.size()));
    }

    private List<AugmentEntry> recoverEntries(List<AugmentEntry> entries) {
        if (entries.isEmpty()) return List.of();
        int highestTier = tierOrdinal(entries.getFirst());
        int highestCount = 0;
        while (highestCount < entries.size() && tierOrdinal(entries.get(highestCount)) == highestTier) {
            highestCount++;
        }
        int guaranteedIndex = random.nextInt(highestCount);
        List<AugmentEntry> recovered = new ArrayList<>();
        recovered.add(entries.get(guaranteedIndex));
        int successfulRecoveries = 1;
        for (int i = 0; i < entries.size(); i++) {
            if (i == guaranteedIndex) continue;
            int chance = Math.max(0, 100 - successfulRecoveries * 20);
            if (chance == 0 || random.nextDouble() * 100.0 >= chance) continue;
            recovered.add(entries.get(i));
            successfulRecoveries++;
        }
        return List.copyOf(recovered);
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
