package me.beeliebub.tweaks.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
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
import org.bukkit.inventory.meta.ItemMeta;
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
    private final Consumer<ItemStack> durabilityFullRepair;
    private final AugmentPendingConfirmations pendingConfirmations;

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry) {
        this(plugin, qualityRegistry, item -> {}, item -> {}, new AugmentPendingConfirmations(plugin));
    }

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry,
                          Consumer<ItemStack> durabilityStamp) {
        this(plugin, qualityRegistry, durabilityStamp, item -> {}, new AugmentPendingConfirmations(plugin));
    }

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry,
                          Consumer<ItemStack> durabilityStamp,
                          Consumer<ItemStack> durabilityTailRefresh) {
        this(plugin, qualityRegistry, durabilityStamp, durabilityTailRefresh,
                new AugmentPendingConfirmations(plugin));
    }

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry,
                          Consumer<ItemStack> durabilityStamp,
                          Consumer<ItemStack> durabilityTailRefresh,
                          AugmentPendingConfirmations pendingConfirmations) {
        this(plugin, qualityRegistry, durabilityStamp, durabilityTailRefresh,
                item -> {}, pendingConfirmations);
    }

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry,
                          Consumer<ItemStack> durabilityStamp,
                          Consumer<ItemStack> durabilityTailRefresh,
                          Consumer<ItemStack> durabilityFullRepair,
                          AugmentPendingConfirmations pendingConfirmations) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
        this.durabilityStamp = durabilityStamp == null ? item -> {} : durabilityStamp;
        this.durabilityTailRefresh = durabilityTailRefresh == null ? item -> {} : durabilityTailRefresh;
        this.durabilityFullRepair = durabilityFullRepair == null ? item -> {} : durabilityFullRepair;
        this.pendingConfirmations = pendingConfirmations == null
                ? new AugmentPendingConfirmations(plugin) : pendingConfirmations;
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
    public AugmentPendingConfirmations pendingConfirmations() { return pendingConfirmations; }

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
        List<ItemStack> gems = computeLegacyGems(item);
        if (gems == null) return null;
        if (!canFit(player, gems, removed)) return null;
        ledger.write(item, 0, List.of(), true);
        foldLiveCursesIntoLedger(item);
        stripLegacyEnchantments(item);
        lore.update(item, qualityRegistry);
        durabilityStamp.accept(item);
        if (!gems.isEmpty()) player.sendMessage(Messages.TOOLS.augmentMigrated(gems.size()));
        addGems(player, gems);
        return List.copyOf(gems);
    }

    /** Builds the gems represented by the item's non-curse live enchantments without mutating it. */
    public List<ItemStack> computeLegacyGems(ItemStack item) {
        if (item == null || item.isEmpty()) return List.of();
        List<ItemStack> gems = new ArrayList<>();
        Set<Enchantment> curses = curseEnchantments();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0
                    || curses.contains(entry.getKey())) continue;
            ItemStack gem = gemItem.create(entry.getKey(), entry.getValue());
            if (gem == null) return null;
            gems.add(gem);
        }
        return List.copyOf(gems);
    }

    /** Removes exactly the non-curse live enchantments eligible for legacy gem migration. */
    public void stripLegacyEnchantments(ItemStack item) {
        if (item == null || item.isEmpty()) return;
        Set<Enchantment> curses = curseEnchantments();
        for (Enchantment enchantment : item.getEnchantments().keySet().toArray(Enchantment[]::new)) {
            if (!curses.contains(enchantment)) item.removeEnchantment(enchantment);
        }
    }

    private boolean foldLiveCursesIntoLedger(ItemStack item) {
        List<AugmentGemItem.CurseRider> curses = liveCurseRiders(item);
        return curses.isEmpty() || ledger.appendCurses(item, curses);
    }

    private List<AugmentGemItem.CurseRider> liveCurseRiders(ItemStack item) {
        if (item == null || item.isEmpty()) return List.of();
        Set<Enchantment> knownCurses = curseEnchantments();
        List<AugmentGemItem.CurseRider> curses = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0
                    && knownCurses.contains(entry.getKey())) {
                curses.add(new AugmentGemItem.CurseRider(entry.getKey(), entry.getValue()));
            }
        }
        return List.copyOf(curses);
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
        foldLiveCursesIntoLedger(item);
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
        int purchasedSlots = item.getEnchantments().isEmpty() ? 1 : 0;
        if (slotCalculator.capacity(item.getType()) == 0) purchasedSlots = 0;
        ledger.write(item, purchasedSlots, entries, true);
        foldLiveCursesIntoLedger(item);
        durabilityStamp.accept(item);
        lore.update(item, qualityRegistry);
        return true;
    }

    /**
     * Reconciles augment state on a smithing-table result. A plain upgrade target that has no
     * ledger yet is initialized like any other newly delivered damageable. An item that carried
     * its ledger through the transform keeps every purchase and attachment untouched, but its
     * slot indicator is re-rendered: the new material can raise the capacity ceiling (a diamond
     * tool upgraded to netherite gains the netherite slot maximum, still bought one slot at a
     * time), and the stale lore would otherwise keep showing the old ceiling until the next
     * purchase or toggle.
     */
    public boolean reconcileSmithingResult(ItemStack item) {
        if (!enabled() || item == null || item.isEmpty()) return false;
        if (!AugmentLedger.hasLedger(item)) return initializeCraftedItem(item);
        if (!ledgerStateValid(item)) return false;
        updateLore(item);
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
        return purchaseSlotAtCost(player, item, cost, next);
    }

    private boolean purchaseSlotAtCost(Player player, ItemStack item, int cost, int next) {
        int level = player.getLevel();
        if (cost < 0 || level < cost) {
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
        ItemMeta previousMeta = item.getItemMeta();
        List<ItemStack> legacyGems = computeLegacyGems(item);
        if (legacyGems == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        boolean migrated = previousMigrated || legacyGems.isEmpty();
        ledger.write(item, next, previousEntries, migrated);
        if (!foldLiveCursesIntoLedger(item)) {
            item.setItemMeta(previousMeta);
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        try {
            experience.changeExp(-delta);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            item.setItemMeta(previousMeta);
            player.sendMessage(Messages.TOOLS.augmentPurchaseUnsafeXp());
            return false;
        }
        if (migrated) lore.update(item, qualityRegistry);
        if (firstLedger && migrated) durabilityStamp.accept(item);
        player.sendMessage(Messages.TOOLS.augmentPurchase(cost, next));
        return true;
    }

    /** Confirms the quoted slot-one migration and returns the still-held item on success. */
    public ItemStack confirmSlotOneUnlock(Player player) {
        if (player == null) return null;
        if (!enabled()) {
            pendingConfirmations.cancel(player.getUniqueId());
            player.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
            return null;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        AugmentPendingConfirmations.PendingRequest request = pendingConfirmations.validFor(player, held);
        if (request == null) {
            pendingConfirmations.cancel(player.getUniqueId());
            player.sendMessage(Messages.TOOLS.augmentConfirmationExpired());
            return null;
        }
        pendingConfirmations.cancel(player.getUniqueId());
        if (held == null || held.isEmpty() || AugmentLedger.hasLedger(held)
                || gemItem.read(held) != null || !ledgerStateValid(held)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return null;
        }
        if (slotCalculator.capacity(held.getType()) < 1) {
            player.sendMessage(Messages.TOOLS.augmentNoSlots());
            return null;
        }
        List<ItemStack> legacyGems = computeLegacyGems(held);
        if (legacyGems == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return null;
        }
        if (legacyGems.isEmpty()) {
            player.sendMessage(Messages.TOOLS.augmentConfirmationExpired());
            return null;
        }
        if (!canFit(player, legacyGems)) {
            player.sendMessage(Messages.TOOLS.inventoryFull());
            return null;
        }
        int next = ledger.slots(held) + 1;
        if (!purchaseSlotAtCost(player, held, request.quotedCost(), next)) return null;
        stripLegacyEnchantments(held);
        ledger.markMigrated(held);
        lore.update(held, qualityRegistry);
        // Converting a legacy tool stamps the custom durability projection and clears the wear it
        // accumulated as a plain tool, so it lands in the player's hand at full projected durability.
        durabilityFullRepair.accept(held);
        player.sendMessage(Messages.TOOLS.augmentMigrated(legacyGems.size()));
        addGems(player, legacyGems);
        return held;
    }

    public boolean cancelPending(Player player) {
        if (player == null) return false;
        boolean cancelled = pendingConfirmations.cancel(player.getUniqueId());
        if (cancelled) player.sendMessage(Messages.TOOLS.augmentConfirmationCancelled());
        return cancelled;
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
                        Messages.TOOLS.enchantmentName(curse.enchantment())));
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
            migrationGems = computeLegacyGems(item);
            if (migrationGems == null) {
                player.sendMessage(Messages.TOOLS.augmentIncompatible());
                return false;
            }
            if (!migrationGems.isEmpty()) {
                player.sendMessage(Messages.TOOLS.augmentNoSlots());
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
                    Messages.TOOLS.augmentEnchantmentName(augment, data.level()), curses.size()));
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
                    Messages.TOOLS.augmentEnchantmentName(enchantment, entry.level())));
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
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (gemItem.read(item) != null) result.add(new GemLocation(i, item));
        }
        for (int i = 0; i < 4; i++) {
            ItemStack item = player.getInventory().getItem(36 + i);
            if (gemItem.read(item) != null) result.add(new GemLocation(36 + i, item));
        }
        ItemStack offHand = player.getInventory().getItem(40);
        if (gemItem.read(offHand) != null) result.add(new GemLocation(40, offHand));
        return result;
    }

    public boolean canFit(Player player, List<ItemStack> items) {
        return canFit(player, items, List.of());
    }

    public boolean canFit(Player player, List<ItemStack> items, List<ItemStack> removed) {
        if (player == null) return false;
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) simulated.add(copyForSimulation(item));
        if (removed != null) {
            for (ItemStack source : removed) removeFromSimulation(simulated, source);
        }
        for (ItemStack incoming : items) {
            if (incoming == null || incoming.isEmpty()) continue;
            int remaining = incoming.getAmount();
            for (ItemStack existing : simulated) {
                if (sameStackForFit(existing, incoming)) {
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
                        ItemStack placed = copyForSimulation(incoming);
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
            if (!sameStackForFit(existing, source)) continue;
            int removed = Math.min(existing.getAmount(), remaining);
            remaining -= removed;
            if (removed == existing.getAmount()) simulated.set(i, null);
            else existing.setAmount(existing.getAmount() - removed);
        }
    }

    public void addGems(Player player, List<ItemStack> gems) {
        for (ItemStack gem : gems) {
            if (gem == null || gem.isEmpty()) continue;
            ItemEnchantments storedEnchantments = gem.getData(DataComponentTypes.STORED_ENCHANTMENTS);
            int remaining = gem.getAmount();
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                ItemStack existing = player.getInventory().getItem(slot);
                if (!sameStackForFit(existing, gem)) continue;
                int room = Math.max(0, Math.min(existing.getMaxStackSize(), gem.getMaxStackSize())
                        - existing.getAmount());
                int moved = Math.min(room, remaining);
                if (moved <= 0) continue;
                existing.setAmount(existing.getAmount() + moved);
                restoreGemComponent(existing, storedEnchantments);
                remaining -= moved;
            }
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                ItemStack existing = player.getInventory().getItem(slot);
                if (existing != null && !existing.isEmpty()) continue;
                int moved = Math.min(gem.getMaxStackSize(), remaining);
                ItemStack placed = copyForSimulation(gem);
                placed.setAmount(moved);
                player.getInventory().setItem(slot, placed);
                restoreGemComponent(player.getInventory().getItem(slot), storedEnchantments);
                remaining -= moved;
            }
            if (remaining > 0) {
                ItemStack leftover = copyForSimulation(gem);
                leftover.setAmount(remaining);
                plugin.getLogger().log(Level.WARNING,
                        "Dropping an augment gem because the inventory changed during delivery");
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private ItemStack copyForSimulation(ItemStack item) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        restoreGemComponent(copy, item.getData(DataComponentTypes.STORED_ENCHANTMENTS));
        return copy;
    }

    private boolean sameStackForFit(ItemStack first, ItemStack second) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()
                || !first.isSimilar(second)) return false;
        if (gemItem.isGem(first) || gemItem.isGem(second)) {
            return gemItem.isGem(first) && gemItem.isGem(second)
                    && sameStoredEnchantments(first, second);
        }
        return true;
    }

    private boolean sameStoredEnchantments(ItemStack first, ItemStack second) {
        ItemEnchantments firstEnchantments = first.getData(DataComponentTypes.STORED_ENCHANTMENTS);
        ItemEnchantments secondEnchantments = second.getData(DataComponentTypes.STORED_ENCHANTMENTS);
        if (firstEnchantments == null || secondEnchantments == null) {
            return firstEnchantments == secondEnchantments;
        }
        return firstEnchantments.enchantments().equals(secondEnchantments.enchantments());
    }

    private void restoreGemComponent(ItemStack item, ItemEnchantments storedEnchantments) {
        if (item != null && storedEnchantments != null) {
            item.setData(DataComponentTypes.STORED_ENCHANTMENTS, storedEnchantments);
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
