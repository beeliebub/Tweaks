package me.beeliebub.tweaks.tools.augments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Renders augment lore while replacing only the explicitly owned component block. */
public final class AugmentLore {

    private final AugmentLedger ledger;
    private final SlotCalculator slots;
    private final Consumer<ItemStack> durabilityTailRefresh;
    private boolean refreshingTail;

    public AugmentLore(AugmentLedger ledger, SlotCalculator slots) {
        this(ledger, slots, item -> {});
    }

    public AugmentLore(AugmentLedger ledger, SlotCalculator slots,
                       Consumer<ItemStack> durabilityTailRefresh) {
        this.ledger = ledger;
        this.slots = slots;
        this.durabilityTailRefresh = durabilityTailRefresh == null ? item -> {} : durabilityTailRefresh;
    }

    public void update(ItemStack item, QualityRegistry qualityRegistry) {
        if (item == null || item.isEmpty() || !AugmentLedger.hasLedger(item)) return;
        ItemMeta meta = item.getItemMeta();
        List<Component> existing = meta.hasLore() && meta.lore() != null
                ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        removeOwnedBlock(existing, ledger.ownedLore(item));
        int capacity = slots.capacity(item.getType());
        int purchased = ledger.slots(item);
        List<AugmentEntry> entries = ledger.entries(item);
        int used = slots.used(entries);
        List<Component> generated = new ArrayList<>();
        generated.add(Messages.TOOLS.augmentSlotLore(slots.slotDots(purchased, used, capacity)));
        for (AugmentEntry entry : entries) {
            var enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(entry.enchantmentKey());
            Component name = enchantment == null
                    ? Messages.TOOLS.augmentEnchantmentName(entry.enchantmentKey(), entry.level())
                    : Messages.TOOLS.augmentEnchantmentName(enchantment, entry.level());
            int weight = enchantment == null
                    ? slots.qualityWeight((QualityTier) null) : slots.qualityWeight(enchantment);
            boolean quality = qualityRegistry != null && enchantment != null
                    && qualityRegistry.getTier(enchantment) != null;
            generated.add(Messages.TOOLS.augmentEntryLore(name, entry.active(), quality,
                    slots.entryDots(weight, entry.active())));
        }
        Set<Enchantment> accounted = new HashSet<>();
        for (AugmentEntry entry : entries) {
            if (!entry.active()) continue;
            Enchantment enchantment = registry().get(entry.enchantmentKey());
            if (enchantment != null) accounted.add(enchantment);
        }
        for (AugmentGemItem.CurseRider curse : ledger.curses(item)) {
            accounted.add(curse.enchantment());
            generated.add(Messages.TOOLS.augmentCurseLore(
                    Messages.TOOLS.enchantmentName(curse.enchantment())));
        }
        int renderedForeign = 0;
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (renderedForeign >= AugmentLedger.MAX_ATTACHED_ENTRIES) break;
            if (!accounted.contains(enchantment)) {
                generated.add(Messages.TOOLS.augmentForeignEnchantLore(
                        Messages.TOOLS.augmentEnchantmentName(enchantment,
                                item.getEnchantmentLevel(enchantment))));
                renderedForeign++;
            }
        }
        existing.addAll(generated);
        meta.lore(existing);
        item.setItemMeta(meta);
        TooltipDisplay existingTooltip = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        TooltipDisplay.Builder tooltip = TooltipDisplay.tooltipDisplay()
                .hideTooltip(existingTooltip != null && existingTooltip.hideTooltip());
        if (existingTooltip != null && existingTooltip.hiddenComponents() != null) {
            tooltip.hiddenComponents(new HashSet<>(existingTooltip.hiddenComponents()));
        }
        tooltip.addHiddenComponents(DataComponentTypes.ENCHANTMENTS);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltip.build());
        ledger.setOwnedLore(item, generated.stream().map(Component::toString).toList());
        if (refreshingTail) return;
        refreshingTail = true;
        try {
            durabilityTailRefresh.accept(item);
        } finally {
            refreshingTail = false;
        }
    }

    private org.bukkit.Registry<Enchantment> registry() {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
    }

    private static void removeOwnedBlock(List<Component> existing, List<String> owned) {
        if (owned == null || owned.isEmpty() || owned.size() > existing.size()) return;
        for (int start = existing.size() - owned.size(); start >= 0; start--) {
            boolean matches = true;
            for (int offset = 0; offset < owned.size(); offset++) {
                Component candidate = existing.get(start + offset);
                if (!owned.get(offset).equals(candidate.toString())) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            for (int offset = 0; offset < owned.size(); offset++) existing.remove(start);
            return;
        }
    }
}
