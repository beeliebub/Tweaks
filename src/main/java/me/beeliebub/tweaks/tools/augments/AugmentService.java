package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.xpbottle.ExperienceManager;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Transactional owner for slot purchases, gem attachment, toggles, and migration. */
public final class AugmentService {

    private final Tweaks plugin;
    private final QualityRegistry qualityRegistry;
    private final AugmentLedger ledger;
    private final AugmentGemItem gemItem;
    private final SlotCalculator slotCalculator;
    private final AugmentCompatibility compatibility;
    private final AugmentLore lore;

    public AugmentService(Tweaks plugin, QualityRegistry qualityRegistry) {
        this.plugin = plugin;
        this.qualityRegistry = qualityRegistry;
        this.ledger = new AugmentLedger(plugin);
        this.gemItem = new AugmentGemItem(plugin);
        this.slotCalculator = new SlotCalculator(plugin, qualityRegistry);
        this.compatibility = new AugmentCompatibility(plugin, qualityRegistry);
        this.lore = new AugmentLore(ledger, slotCalculator);
    }

    public AugmentLedger ledger() { return ledger; }
    public AugmentGemItem gemItem() { return gemItem; }
    public SlotCalculator slotCalculator() { return slotCalculator; }
    public AugmentLore lore() { return lore; }

    public static boolean hasAugmentsOrLegacy(ItemStack item) {
        return item != null && !item.isEmpty()
                && (AugmentLedger.hasLedger(item) || !item.getEnchantments().isEmpty());
    }

    public boolean compatibleForDisplay(ItemStack item, Enchantment enchantment, List<AugmentEntry> entries) {
        if (!ledger.migrated(item) && item != null && !item.getEnchantments().isEmpty()) {
            ItemStack legacyFree = item.clone();
            for (Enchantment existing : legacyFree.getEnchantments().keySet().toArray(Enchantment[]::new)) {
                legacyFree.removeEnchantment(existing);
            }
            return compatibility.canAttach(legacyFree, enchantment, entries);
        }
        return compatibility.canAttach(item, enchantment, entries);
    }

    public List<AugmentEntry> entries(ItemStack item) { return ledger.entries(item); }

    public List<ItemStack> migrateToGems(Player player, ItemStack item) {
        if (item == null || item.isEmpty() || ledger.migrated(item)) return List.of();
        List<ItemStack> gems = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            gems.add(gemItem.create(entry.getKey(), entry.getValue()));
        }
        if (!canFit(player, gems)) return null;
        List<AugmentEntry> migrated = item.getEnchantments().entrySet().stream()
                .map(entry -> new AugmentEntry(entry.getKey().getKey(), entry.getValue(), true)).toList();
        ledger.write(item, ledger.slots(item), migrated, true);
        for (Enchantment enchantment : item.getEnchantments().keySet().toArray(Enchantment[]::new)) {
            item.removeEnchantment(enchantment);
        }
        lore.update(item, qualityRegistry);
        addGems(player, gems);
        return List.copyOf(gems);
    }

    /** D60 migration path: populate the ledger without delivering gems before destructive recovery. */
    public boolean migrateLegacyForRecovery(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        if (ledger.migrated(item) && item.getEnchantments().isEmpty()) return true;
        List<AugmentEntry> migrated = item.getEnchantments().entrySet().stream()
                .map(entry -> new AugmentEntry(entry.getKey().getKey(), entry.getValue(), true)).toList();
        ledger.write(item, ledger.slots(item), migrated, true);
        for (Enchantment enchantment : item.getEnchantments().keySet().toArray(Enchantment[]::new)) {
            item.removeEnchantment(enchantment);
        }
        lore.update(item, qualityRegistry);
        return true;
    }

    public boolean purchaseSlot(Player player, ItemStack item) {
        if (!enabled() || item == null || item.isEmpty()) return false;
        int capacity = slotCalculator.capacity(item.getType());
        int purchased = ledger.slots(item);
        int next = purchased + 1;
        if (next > capacity) {
            player.sendMessage(Messages.TOOLS.augmentNoSlots());
            return false;
        }
        int cost = slotCalculator.price(next);
        int level = player.getLevel();
        if (level < cost) {
            player.sendMessage(Messages.TOOLS.augmentPurchaseRejected());
            return false;
        }
        int delta = new ExperienceManager(player).getXpForLevel(level)
                - new ExperienceManager(player).getXpForLevel(level - cost);
        ledger.setSlots(item, next);
        new ExperienceManager(player).changeExp(-delta);
        lore.update(item, qualityRegistry);
        player.sendMessage(Messages.TOOLS.augmentPurchase(cost, next));
        return true;
    }

    public boolean attach(Player player, ItemStack item, ItemStack gem) {
        if (!enabled() || item == null || gem == null) return false;
        if (!ledger.migrated(item) && migrateToGems(player, item) == null) {
            player.sendMessage(Messages.TOOLS.inventoryFull());
            return false;
        }
        AugmentGemItem.GemData data = gemItem.read(gem);
        if (data == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        List<AugmentEntry> current = new ArrayList<>(ledger.entries(item));
        if (!compatibility.canAttach(item, data.enchantment(), current)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return false;
        }
        int weight = slotCalculator.qualityWeight(data.enchantment());
        if (slotCalculator.used(current) + weight > ledger.slots(item)) {
            player.sendMessage(Messages.TOOLS.augmentNoSlots());
            return false;
        }
        boolean active = current.stream().noneMatch(entry -> {
            Enchantment existing = registry().get(entry.enchantmentKey());
            return entry.active() && existing != null && compatibility.sameQualityBase(existing, data.enchantment());
        });
        AugmentEntry added = new AugmentEntry(data.enchantment().getKey(), data.level(), active);
        current.add(added);
        ledger.write(item, ledger.slots(item), current, ledger.migrated(item));
        if (active) item.addUnsafeEnchantment(data.enchantment(), data.level());
        lore.update(item, qualityRegistry);
        gem.setAmount(gem.getAmount() - 1);
        player.sendMessage(Messages.TOOLS.augmentAttached(data.enchantment().getKey().toString(), data.level()));
        return true;
    }

    public boolean toggle(Player player, ItemStack item, int entryIndex) {
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
            player.sendMessage(Messages.TOOLS.augmentDetached(enchantment.getKey().toString()));
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
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) simulated.add(item == null ? null : item.clone());
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

    public void addGems(Player player, List<ItemStack> gems) {
        for (ItemStack gem : gems) player.getInventory().addItem(gem);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tools.augments.enabled", true);
    }

    private org.bukkit.Registry<Enchantment> registry() {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
    }

    public record GemLocation(int slot, ItemStack item) {}
}
