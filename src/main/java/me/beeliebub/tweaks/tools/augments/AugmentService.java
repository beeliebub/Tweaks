package me.beeliebub.tweaks.tools.augments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.xpbottle.ExperienceManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Transactional owner for slot purchases, gem attachment, toggles, and migration. */
public final class AugmentService {

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;
    private final AugmentLedger ledger;
    private final AugmentGemItem gemItem;
    private final SlotCalculator slotCalculator;
    private final AugmentCompatibility compatibility;
    private final AugmentLore lore;
    private final Consumer<ItemStack> durabilityStamp;
    private final Consumer<ItemStack> durabilityTailRefresh;

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry) {
        this(plugin, qualityRegistry, item -> {}, item -> {});
    }

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry,
                          Consumer<ItemStack> durabilityStamp) {
        this(plugin, qualityRegistry, durabilityStamp, item -> {});
    }

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry,
                          Consumer<ItemStack> durabilityStamp,
                          Consumer<ItemStack> durabilityTailRefresh) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
        this.durabilityStamp = durabilityStamp == null ? item -> {} : durabilityStamp;
        this.durabilityTailRefresh = durabilityTailRefresh == null ? item -> {} : durabilityTailRefresh;
        this.ledger = new AugmentLedger(plugin);
        this.gemItem = new AugmentGemItem(plugin);
        this.slotCalculator = new SlotCalculator(plugin, qualityRegistry);
        this.compatibility = new AugmentCompatibility(plugin, qualityRegistry);
        this.lore = new AugmentLore(ledger, slotCalculator, this.durabilityTailRefresh);
    }

    public AugmentLedger ledger() { return ledger; }
    public AugmentGemItem gemItem() { return gemItem; }
    public SlotCalculator slotCalculator() { return slotCalculator; }
    public AugmentLore lore() { return lore; }

    public boolean isCurse(Enchantment enchantment) {
        return enchantment != null && curseEnchantments().contains(enchantment);
    }

    /** Returns the current Paper curse tag as one immutable snapshot for a batch operation. */
    public Set<Enchantment> curseEnchantments() {
        return resolveCurseEnchantments();
    }

    public boolean hasNonCurseEnchantments(ItemStack item) {
        if (item == null || item.isEmpty() || item.getEnchantments().isEmpty()) return false;
        Set<Enchantment> curses = curseEnchantments();
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (!curses.contains(enchantment)) return true;
        }
        return false;
    }

    public static boolean hasAugmentsOrLegacy(ItemStack item) {
        return item != null && !item.isEmpty()
                && (AugmentLedger.hasLedger(item) || !item.getEnchantments().isEmpty());
    }

    public static boolean hasMeaningfulAugmentState(AugmentLedger ledger, ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return item.getEnchantments().isEmpty() ? ledger != null
                && (ledger.slots(item) > 0 || !ledger.entries(item).isEmpty() || !ledger.curses(item).isEmpty())
                : true;
    }

    public boolean compatibleForDisplay(ItemStack item, Enchantment enchantment, List<AugmentEntry> entries) {
        return compatibleForDisplay(item, enchantment, entries, isCurse(enchantment));
    }

    public boolean compatibleForDisplay(ItemStack item, AugmentGemItem.GemData data,
                                        List<AugmentEntry> entries) {
        if (item == null || item.isEmpty() || data == null || !ledgerStateValid(item)) return false;
        Set<Enchantment> knownCurses = curseEnchantments();
        boolean primaryCurse = knownCurses.contains(data.enchantment());
        if (!compatibleForDisplay(item, data.enchantment(), entries)) return false;
        Set<org.bukkit.NamespacedKey> bound = new HashSet<>();
        for (AugmentGemItem.CurseRider curse : ledger.curses(item)) {
            if (!isCurse(curse.enchantment())) continue;
            bound.add(curse.enchantment().getKey());
        }
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (knownCurses.contains(enchantment)) bound.add(enchantment.getKey());
        }
        List<AugmentGemItem.CurseRider> gemCurses = new ArrayList<>(data.curses());
        if (primaryCurse) {
            gemCurses.add(new AugmentGemItem.CurseRider(data.enchantment(), data.level()));
        }
        Set<org.bukkit.NamespacedKey> seen = new HashSet<>();
        for (AugmentGemItem.CurseRider curse : gemCurses) {
            if (curse == null || !knownCurses.contains(curse.enchantment())) return false;
            if (!seen.add(curse.enchantment().getKey()) || bound.contains(curse.enchantment().getKey())) return false;
        }
        return true;
    }

    private boolean compatibleForDisplay(ItemStack item, Enchantment enchantment,
                                         List<AugmentEntry> entries, boolean skipItemTypeCheck) {
        if (!ledger.migrated(item) && item != null && !item.getEnchantments().isEmpty()) {
            ItemStack legacyFree = item.clone();
            for (Enchantment existing : legacyFree.getEnchantments().keySet().toArray(Enchantment[]::new)) {
                legacyFree.removeEnchantment(existing);
            }
            return compatibility.canAttach(legacyFree, enchantment, entries, skipItemTypeCheck);
        }
        return compatibility.canAttach(item, enchantment, entries, skipItemTypeCheck);
    }

    public List<AugmentEntry> entries(ItemStack item) { return ledger.entries(item); }

    public boolean ledgerStateValid(ItemStack item) {
        if (item == null || item.isEmpty() || !ledger.recoveryStateValid(item, registry())) return false;
        // The configured capacity is the current domain for purchased slots.  Failing closed here
        // also makes a forged Integer.MAX_VALUE ledger harmless; lowering the setting temporarily
        // disables mutation of items whose recorded purchases exceed the new capacity.
        return ledger.slots(item) <= slotCalculator.capacity(item.getType());
    }

    public List<ItemStack> migrateToGems(Player player, ItemStack item) {
        return migrateToGems(player, item, List.of());
    }

    private List<ItemStack> migrateToGems(Player player, ItemStack item, List<ItemStack> removed) {
        if (item == null || item.isEmpty()) return List.of();
        if (!ledgerStateValid(item)) return null;
        if (ledger.migrated(item)) return List.of();
        if (AugmentLedger.hasLedger(item)) return null;
        if (item.getEnchantments().isEmpty()) return List.of();
        Set<Enchantment> curses = curseEnchantments();
        List<ItemStack> gems = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (curses.contains(entry.getKey())) continue;
            gems.add(gemItem.create(entry.getKey(), entry.getValue()));
        }
        if (!canFit(player, gems, removed)) return null;
        ledger.write(item, 0, List.of(), true);
        for (Enchantment enchantment : item.getEnchantments().keySet().toArray(Enchantment[]::new)) {
            if (!curses.contains(enchantment)) item.removeEnchantment(enchantment);
        }
        lore.update(item, qualityRegistry);
        durabilityStamp.accept(item);
        if (!gems.isEmpty()) player.sendMessage(Messages.TOOLS.augmentMigrated(gems.size()));
        addGems(player, gems);
        return List.copyOf(gems);
    }

    /** Populate the ledger without delivering gems before destructive recovery. */
    public boolean migrateLegacyForRecovery(ItemStack item) {
        return migrateLegacyForRecovery(item, recoveryPlan(item));
    }

    public boolean migrateLegacyForRecovery(ItemStack item, RecoveryPlan plan) {
        if (item == null || item.isEmpty() || plan == null || !plan.valid()) return false;
        RecoveryPlan current = recoveryPlan(item);
        if (!current.valid() || current.requiresLegacyMigration() != plan.requiresLegacyMigration()
                || !current.entries().equals(plan.entries())) return false;
        if (!plan.requiresLegacyMigration()) return true;
        Set<Enchantment> curses = curseEnchantments();
        ledger.write(item, ledger.slots(item), plan.entries(), true);
        for (Enchantment enchantment : item.getEnchantments().keySet().toArray(Enchantment[]::new)) {
            if (!curses.contains(enchantment)) item.removeEnchantment(enchantment);
        }
        lore.update(item, qualityRegistry);
        durabilityStamp.accept(item);
        return true;
    }

    /** Establishes the augment schema on a newly crafted damageable item without changing live enchants. */
    public boolean initializeCraftedItem(ItemStack item) {
        if (item == null || item.isEmpty() || AugmentLedger.hasLedger(item)) return false;
        List<AugmentEntry> entries = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0
                    || isCurse(entry.getKey())) continue;
            entries.add(new AugmentEntry(entry.getKey().getKey(), entry.getValue(), true));
        }
        ledger.write(item, 0, entries, true);
        durabilityStamp.accept(item);
        lore.update(item, qualityRegistry);
        return true;
    }

    public RecoveryPlan recoveryPlan(ItemStack item) {
        if (item == null || item.isEmpty() || !ledgerStateValid(item)) {
            return RecoveryPlan.invalid();
        }
        boolean migrated = ledger.migrated(item);
        List<AugmentEntry> attached = ledger.entries(item);
        if (migrated) {
            if (attached.isEmpty() || attached.stream().anyMatch(entry -> registry().get(entry.enchantmentKey()) == null)) {
                return RecoveryPlan.invalid();
            }
            if (!migratedEnchantmentsMatchLedger(item, attached)) return RecoveryPlan.invalid();
            return new RecoveryPlan(attached, false, true);
        }
        if (!attached.isEmpty()) return RecoveryPlan.invalid();
        Set<Enchantment> curses = curseEnchantments();
        List<AugmentEntry> legacy = item.getEnchantments().entrySet().stream()
                .filter(entry -> !curses.contains(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> new AugmentEntry(entry.getKey().getKey(), entry.getValue(), true))
                .toList();
        if (legacy.isEmpty()) return RecoveryPlan.invalid();
        return new RecoveryPlan(legacy, true, true);
    }

    private boolean migratedEnchantmentsMatchLedger(ItemStack item, List<AugmentEntry> attached) {
        Set<Enchantment> curses = curseEnchantments();
        for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
            Enchantment real = enchantment.getKey();
            if (curses.contains(real)) continue;
            AugmentEntry recorded = attached.stream()
                    .filter(entry -> entry.enchantmentKey().equals(real.getKey()))
                    .findFirst().orElse(null);
            if (recorded == null || !recorded.active()
                    || recorded.level() != enchantment.getValue()) return false;
        }
        return true;
    }

    public boolean purchaseSlot(Player player, ItemStack item) {
        if (!enabled() || item == null || item.isEmpty()) return false;
        if (!ledgerStateValid(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        int capacity = slotCalculator.capacity(item.getType());
        int purchased = ledger.slots(item);
        if (purchased >= capacity) {
            player.sendMessage(Messages.TOOLS.augmentNoSlots());
            return false;
        }
        int next = purchased + 1;
        int cost = slotCalculator.price(next);
        if (cost < 0) {
            player.sendMessage(Messages.TOOLS.augmentPurchaseRejected());
            return false;
        }
        int level = player.getLevel();
        if (level < cost) {
            player.sendMessage(Messages.TOOLS.augmentPurchaseRejected());
            return false;
        }
        ExperienceManager experience = new ExperienceManager(player);
        long delta;
        try {
            delta = (long) experience.getXpForLevel(level)
                    - experience.getXpForLevel(level - cost);
        } catch (IllegalArgumentException unsupportedXpRange) {
            player.sendMessage(Messages.TOOLS.augmentPurchaseUnsafeXp());
            return false;
        }
        if (!experience.canChangeExp(-delta)) {
            player.sendMessage(Messages.TOOLS.augmentPurchaseUnsafeXp());
            return false;
        }
        boolean firstLedger = !AugmentLedger.hasLedger(item);
        List<AugmentEntry> previousEntries = ledger.entries(item);
        boolean previousMigrated = ledger.migrated(item);
        ledger.write(item, next, previousEntries, true);
        try {
            experience.changeExp(-delta);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            ledger.write(item, purchased, previousEntries, previousMigrated);
            player.sendMessage(Messages.TOOLS.augmentPurchaseUnsafeXp());
            return false;
        }
        lore.update(item, qualityRegistry);
        if (firstLedger) durabilityStamp.accept(item);
        player.sendMessage(Messages.TOOLS.augmentPurchase(cost, next));
        return true;
    }

    public boolean attach(Player player, ItemStack item, ItemStack gem) {
        if (!enabled() || item == null || gem == null) return false;
        if (!ledgerStateValid(item) || ledger.hasMalformedCurses(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        AugmentGemItem.GemData data = gemItem.read(gem);
        if (data == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        List<AugmentEntry> current = new ArrayList<>(ledger.entries(item));
        Set<Enchantment> knownCurses = curseEnchantments();
        List<AugmentGemItem.CurseRider> curses = new ArrayList<>(data.curses());
        if (knownCurses.contains(data.enchantment())) {
            curses.add(new AugmentGemItem.CurseRider(data.enchantment(), data.level()));
        }
        Set<org.bukkit.NamespacedKey> boundCurses = new HashSet<>();
        for (AugmentGemItem.CurseRider curse : ledger.curses(item)) {
            boundCurses.add(curse.enchantment().getKey());
        }
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (knownCurses.contains(enchantment)) boundCurses.add(enchantment.getKey());
        }
        Set<org.bukkit.NamespacedKey> incomingCurses = new HashSet<>();
        boolean callbackNeeded = !AugmentLedger.hasLedger(item);
        for (AugmentGemItem.CurseRider curse : curses) {
            if (curse == null || curse.enchantment() == null || !knownCurses.contains(curse.enchantment())
                    || curse.level() <= 0) {
                player.sendMessage(Messages.TOOLS.augmentIncompatible());
                return false;
            }
            if (!incomingCurses.add(curse.enchantment().getKey()) || boundCurses.contains(curse.enchantment().getKey())) {
                player.sendMessage(Messages.TOOLS.augmentCurseAlreadyAttached(
                        Messages.TOOLS.enchantmentName(curse.enchantment(), curse.level())));
                return false;
            }
        }
        if (ledger.curses(item).size() + curses.size() > AugmentLedger.MAX_CURSE_ENTRIES) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }

        Enchantment augment = knownCurses.contains(data.enchantment()) ? null : data.enchantment();
        if (augment != null && current.size() >= AugmentLedger.MAX_ATTACHED_ENTRIES) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        boolean needsMigration = !ledger.migrated(item);
        if (needsMigration && AugmentLedger.hasLedger(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        ItemStack compatibilityItem = item;
        List<ItemStack> migrationGems = List.of();
        if (needsMigration) {
            compatibilityItem = item.clone();
            List<ItemStack> legacy = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
                if (knownCurses.contains(enchantment.getKey())) continue;
                legacy.add(gemItem.create(enchantment.getKey(), enchantment.getValue()));
                compatibilityItem.removeEnchantment(enchantment.getKey());
            }
            migrationGems = List.copyOf(legacy);
            ItemStack consumedGem = gem.clone();
            consumedGem.setAmount(1);
            if (!migrationGems.isEmpty() && !canFit(player, migrationGems, List.of(consumedGem))) {
                player.sendMessage(Messages.TOOLS.inventoryFull());
                return false;
            }
        }
        if (augment != null) {
            if (!compatibility.canAttach(compatibilityItem, augment, current, false)) {
                player.sendMessage(Messages.TOOLS.augmentIncompatible());
                return false;
            }
            long weight = slotCalculator.qualityWeight(augment);
            if ((long) slotCalculator.used(current) + weight > ledger.slots(item)) {
                player.sendMessage(Messages.TOOLS.augmentNoSlots());
                return false;
            }
        }

        ItemStack consumedGem = gem.clone();
        consumedGem.setAmount(1);
        if (needsMigration && !migrationGems.isEmpty()
                && migrateToGems(player, item, List.of(consumedGem)) == null) {
            player.sendMessage(Messages.TOOLS.inventoryFull());
            return false;
        }
        if (needsMigration && !migrationGems.isEmpty()) callbackNeeded = false;

        if (!curses.isEmpty() && !ledger.appendCurses(item, curses)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }

        boolean active = augment != null && current.stream().noneMatch(entry -> {
            Enchantment existing = registry().get(entry.enchantmentKey());
            return entry.active() && existing != null && compatibility.sameQualityBase(existing, augment);
        });
        if (augment != null) {
            current.add(new AugmentEntry(augment.getKey(), data.level(), active));
            ledger.write(item, ledger.slots(item), current, true);
            if (active) item.addUnsafeEnchantment(augment, data.level());
        } else if (!ledger.migrated(item)) {
            // A curse-only gem is free but still establishes the augment schema on a plain item.
            ledger.write(item, ledger.slots(item), current, true);
        }
        for (AugmentGemItem.CurseRider curse : curses) {
            item.addUnsafeEnchantment(curse.enchantment(), curse.level());
        }
        lore.update(item, qualityRegistry);
        gem.setAmount(gem.getAmount() - 1);
        if (augment == null) {
            player.sendMessage(Messages.TOOLS.augmentCursesAttached(curses.size()));
        } else {
            player.sendMessage(Messages.TOOLS.augmentAttached(
                    Messages.TOOLS.enchantmentName(augment, data.level()), curses.size()));
        }
        if (callbackNeeded && AugmentLedger.hasLedger(item)) durabilityStamp.accept(item);
        return true;
    }

    public boolean toggle(Player player, ItemStack item, int entryIndex) {
        if (!enabled() || item == null || item.isEmpty() || !ledgerStateValid(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        List<AugmentEntry> current = new ArrayList<>(ledger.entries(item));
        if (entryIndex < 0 || entryIndex >= current.size()) return false;
        AugmentEntry entry = current.get(entryIndex);
        Enchantment enchantment = registry().get(entry.enchantmentKey());
        if (enchantment == null) return false;
        if (entry.active()) {
            item.removeEnchantment(enchantment);
            current.set(entryIndex, new AugmentEntry(entry.enchantmentKey(), entry.level(), false));
            ledger.write(item, ledger.slots(item), current, ledger.migrated(item));
            lore.update(item, qualityRegistry);
            player.sendMessage(Messages.TOOLS.augmentDetached(
                    Messages.TOOLS.enchantmentName(enchantment, entry.level())));
            return true;
        }
        List<AugmentEntry> others = new ArrayList<>(current);
        others.remove(entryIndex);
        for (AugmentEntry other : others) {
            Enchantment existing = registry().get(other.enchantmentKey());
            if (other.active() && existing != null) {
                if (compatibility.sameQualityBase(existing, enchantment)
                        || !compatibility.canAttach(item, enchantment, List.of(other))) {
                    player.sendMessage(Messages.TOOLS.augmentIncompatible());
                    return false;
                }
            }
        }
        item.addUnsafeEnchantment(enchantment, entry.level());
        current.set(entryIndex, new AugmentEntry(entry.enchantmentKey(), entry.level(), true));
        ledger.write(item, ledger.slots(item), current, ledger.migrated(item));
        lore.update(item, qualityRegistry);
        return true;
    }

    public List<GemLocation> inventoryGems(Player player) {
        List<GemLocation> result = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) if (gemItem.read(storage[i]) != null) result.add(new GemLocation(i, storage[i]));
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) if (gemItem.read(armor[i]) != null) result.add(new GemLocation(36 + i, armor[i]));
        if (gemItem.read(player.getInventory().getItemInOffHand()) != null) result.add(new GemLocation(40, player.getInventory().getItemInOffHand()));
        return result;
    }

    public boolean canFit(Player player, List<ItemStack> items) {
        return canFit(player, items, List.of());
    }

    public boolean canFit(Player player, List<ItemStack> items, List<ItemStack> removed) {
        if (player == null) return false;
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) simulated.add(item == null ? null : item.clone());
        if (removed != null) {
            for (ItemStack source : removed) removeFromSimulation(simulated, source);
        }
        for (ItemStack incoming : items) {
            if (incoming == null || incoming.isEmpty()) continue;
            int remaining = incoming.getAmount();
            for (ItemStack existing : simulated) {
                if (existing != null && existing.isSimilar(incoming)) {
                    int room = Math.max(0, Math.min(existing.getMaxStackSize(), incoming.getMaxStackSize()) - existing.getAmount());
                    int moved = Math.min(room, remaining);
                    existing.setAmount(existing.getAmount() + moved);
                    remaining -= moved;
                    if (remaining == 0) break;
                }
            }
            if (remaining > 0) {
                for (int i = 0; i < simulated.size() && remaining > 0; i++) {
                    if (simulated.get(i) == null || simulated.get(i).isEmpty()) {
                        ItemStack placed = incoming.clone();
                        placed.setAmount(Math.min(placed.getMaxStackSize(), remaining));
                        simulated.set(i, placed);
                        remaining -= placed.getAmount();
                    }
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void removeFromSimulation(List<ItemStack> simulated, ItemStack source) {
        if (source == null || source.isEmpty()) return;
        int remaining = source.getAmount();
        for (int i = 0; i < simulated.size() && remaining > 0; i++) {
            ItemStack existing = simulated.get(i);
            if (existing == null || existing.isEmpty() || !existing.isSimilar(source)) continue;
            int removed = Math.min(existing.getAmount(), remaining);
            remaining -= removed;
            if (removed == existing.getAmount()) simulated.set(i, null);
            else existing.setAmount(existing.getAmount() - removed);
        }
    }

    public void addGems(Player player, List<ItemStack> gems) {
        for (ItemStack gem : gems) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(gem);
            for (ItemStack leftover : leftovers.values()) {
                if (leftover == null || leftover.isEmpty()) continue;
                plugin.getLogger().log(Level.WARNING,
                        "Dropping an augment gem because the inventory changed during delivery");
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public void resyncActiveEnchantments(ItemStack item) {
        if (item == null || item.isEmpty() || !ledgerStateValid(item)) return;
        for (AugmentEntry entry : ledger.entries(item)) {
            if (!entry.active()) continue;
            Enchantment enchantment = registry().get(entry.enchantmentKey());
            if (enchantment != null) item.addUnsafeEnchantment(enchantment, entry.level());
        }
    }

    /**
     * Applies an enchanting-table result transaction after vanilla has completed the event.
     * The listener owns only event timing and inventory write-back; all item mutation stays here.
     */
    public boolean stripTableEnchantments(Player player, ItemStack item,
                                          Map<Enchantment, Integer> snapshot) {
        return stripTableEnchantments(player, item, snapshot, ThreadLocalRandom.current(), Map.of());
    }

    public boolean stripTableEnchantments(Player player, ItemStack item,
                                          Map<Enchantment, Integer> snapshot, Random random) {
        return stripTableEnchantments(player, item, snapshot, random, Map.of());
    }

    public boolean stripTableEnchantments(Player player, ItemStack item,
                                           Map<Enchantment, Integer> snapshot, Random random,
                                           Map<Enchantment, Integer> preExistingEnchantments) {
        return stripTableEnchantmentsResult(player, item, snapshot, random, preExistingEnchantments, false) != null;
    }

    public GemBatchResult prepareTableGems(Map<Enchantment, Integer> snapshot, Random random) {
        return createGemBatchResult(snapshot, random);
    }

    public ItemStack stripTableEnchantmentsResult(Player player, ItemStack item,
                                                  Map<Enchantment, Integer> snapshot, Random random,
                                                  Map<Enchantment, Integer> preExistingEnchantments,
                                                  boolean normalizePlainBook) {
        return stripTableEnchantmentsResult(player, item, snapshot, preExistingEnchantments,
                normalizePlainBook, prepareTableGems(snapshot, random).gems());
    }

    public ItemStack stripTableEnchantmentsResult(Player player, ItemStack item,
                                                  Map<Enchantment, Integer> snapshot,
                                                  Map<Enchantment, Integer> preExistingEnchantments,
                                                  boolean normalizePlainBook, List<ItemStack> preparedGems) {
        if (player == null || item == null || item.isEmpty() || snapshot == null || snapshot.isEmpty()
                || !ledgerStateValid(item)) return null;
        boolean storedBook = item.getType() == Material.ENCHANTED_BOOK
                && item.getItemMeta() instanceof EnchantmentStorageMeta;
        for (Map.Entry<Enchantment, Integer> entry : snapshot.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) return null;
            if (storedBook) {
                EnchantmentStorageMeta storage = (EnchantmentStorageMeta) item.getItemMeta();
                if (storage.getStoredEnchantLevel(entry.getKey()) != entry.getValue()) return null;
            } else if (item.getEnchantmentLevel(entry.getKey()) != entry.getValue()) return null;
        }
        List<ItemStack> gems = preparedGems == null ? List.of() : List.copyOf(preparedGems);
        if (gems.isEmpty() || !canFit(player, gems)) return null;
        ItemStack result = item;
        if (storedBook) {
            if (normalizePlainBook) result = item.clone();
            EnchantmentStorageMeta storage = (EnchantmentStorageMeta) result.getItemMeta();
            for (Enchantment enchantment : snapshot.keySet()) storage.removeStoredEnchant(enchantment);
            result.setItemMeta(storage);
            if (normalizePlainBook) result = result.withType(Material.BOOK);
        } else {
            for (Enchantment enchantment : snapshot.keySet()) item.removeEnchantment(enchantment);
        }
        if (preExistingEnchantments != null) {
            for (Map.Entry<Enchantment, Integer> entry : preExistingEnchantments.entrySet()) {
                if (snapshot.containsKey(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0) {
                    result.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                }
            }
        }
        resyncActiveEnchantments(result);
        updateLore(result);
        addGems(player, gems);
        return result;
    }

    public List<ItemStack> createGemBatch(Map<Enchantment, Integer> rolled, Random random) {
        return createGemBatchResult(rolled, random).gems();
    }

    public GemBatchResult createGemBatchResult(Map<Enchantment, Integer> rolled, Random random) {
        if (rolled == null || rolled.isEmpty()) return GemBatchResult.empty();
        Collection<Enchantment> resolvedCurses = curseEnchantments();
        List<Map.Entry<Enchantment, Integer>> curses = new ArrayList<>();
        List<Map.Entry<Enchantment, Integer>> nonCurses = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : rolled.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            if (resolvedCurses.contains(entry.getKey())) curses.add(entry);
            else nonCurses.add(entry);
        }
        if (curses.isEmpty() && nonCurses.isEmpty()) return GemBatchResult.empty();
        if (curses.size() > AugmentGemItem.MAX_CURSE_RIDERS) {
            return GemBatchResult.refused(GemBatchFailure.TOO_MANY_CURSE_RIDERS);
        }
        if (nonCurses.isEmpty()) {
            Map.Entry<Enchantment, Integer> primary = curses.get(0);
            List<AugmentGemItem.CurseRider> riders = new ArrayList<>();
            for (int i = 1; i < curses.size(); i++) {
                Map.Entry<Enchantment, Integer> rider = curses.get(i);
                riders.add(new AugmentGemItem.CurseRider(rider.getKey(), rider.getValue()));
            }
            ItemStack gem = gemItem.create(primary.getKey(), primary.getValue(), riders);
            return gem == null ? GemBatchResult.refused(GemBatchFailure.GEM_CREATION_FAILED)
                    : GemBatchResult.success(List.of(gem));
        }
        List<List<AugmentGemItem.CurseRider>> riders = new ArrayList<>();
        for (int i = 0; i < nonCurses.size(); i++) riders.add(new ArrayList<>());
        for (Map.Entry<Enchantment, Integer> curse : curses) {
            int index = (random == null ? ThreadLocalRandom.current() : random).nextInt(nonCurses.size());
            riders.get(index).add(new AugmentGemItem.CurseRider(curse.getKey(), curse.getValue()));
        }
        List<ItemStack> gems = new ArrayList<>();
        for (int i = 0; i < nonCurses.size(); i++) {
            Map.Entry<Enchantment, Integer> entry = nonCurses.get(i);
            ItemStack gem = gemItem.create(entry.getKey(), entry.getValue(), riders.get(i));
            if (gem == null) return GemBatchResult.refused(GemBatchFailure.GEM_CREATION_FAILED);
            gems.add(gem);
        }
        return GemBatchResult.success(gems);
    }

    public void updateLore(ItemStack item) {
        lore.update(item, qualityRegistry);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tools.augments.enabled", true);
    }

    static Set<Enchantment> resolveCurseEnchantments() {
        try {
            Collection<Enchantment> values = registry().getTagValues(EnchantmentTagKeys.CURSE);
            if (values != null && !values.isEmpty()) return Set.copyOf(values);
            // MockBukkit and older Paper registry fixtures may not expose vanilla tags even
            // though the enchantment metadata still identifies the built-in curses.
            return fallbackCurseEnchantments();
        } catch (RuntimeException unavailable) {
            return fallbackCurseEnchantments();
        }
    }

    private static Set<Enchantment> fallbackCurseEnchantments() {
        Set<Enchantment> result = new HashSet<>();
        try {
            registry().stream().filter(Enchantment::isCursed).forEach(result::add);
        } catch (RuntimeException ignored) {
            // The two built-in constants below still provide a safe fallback for registry fixtures.
        }
        result.add(Enchantment.BINDING_CURSE);
        result.add(Enchantment.VANISHING_CURSE);
        return Set.copyOf(result);
    }

    private static org.bukkit.Registry<Enchantment> registry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    public record GemLocation(int slot, ItemStack item) {}

    public enum GemBatchFailure {
        NONE,
        EMPTY,
        TOO_MANY_CURSE_RIDERS,
        GEM_CREATION_FAILED
    }

    public record GemBatchResult(List<ItemStack> gems, GemBatchFailure failure) {
        public GemBatchResult {
            gems = gems == null ? List.of() : List.copyOf(gems);
            failure = failure == null ? GemBatchFailure.GEM_CREATION_FAILED : failure;
        }

        public static GemBatchResult empty() {
            return new GemBatchResult(List.of(), GemBatchFailure.EMPTY);
        }

        public static GemBatchResult success(List<ItemStack> gems) {
            return new GemBatchResult(gems, GemBatchFailure.NONE);
        }

        public static GemBatchResult refused(GemBatchFailure failure) {
            return new GemBatchResult(List.of(), failure);
        }

        public boolean refused() {
            return failure == GemBatchFailure.TOO_MANY_CURSE_RIDERS
                    || failure == GemBatchFailure.GEM_CREATION_FAILED;
        }
    }

    public record RecoveryPlan(List<AugmentEntry> entries, boolean requiresLegacyMigration, boolean valid) {
        public RecoveryPlan {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public static RecoveryPlan invalid() {
            return new RecoveryPlan(List.of(), false, false);
        }
    }
}
