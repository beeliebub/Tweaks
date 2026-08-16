package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Renders augment lore while replacing only the explicitly owned component block. */
public final class AugmentLore {

    private static final String OWNERSHIP_MARKER = "\u2063";
    private final AugmentLedger ledger;
    private final SlotCalculator slots;

    public AugmentLore(AugmentLedger ledger, SlotCalculator slots) {
        this.ledger = ledger;
        this.slots = slots;
    }

    public void update(ItemStack item, QualityRegistry qualityRegistry) {
        if (item == null || item.isEmpty()) return;
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
            String name = enchantment == null ? entry.enchantmentKey().toString() : enchantment.getKey().getKey();
            generated.add(Messages.TOOLS.augmentEntryLore(name, entry.level(), entry.active()));
        }
        for (AugmentGemItem.CurseRider curse : ledger.curses(item)) {
            generated.add(Messages.TOOLS.augmentCurseLore(curse.enchantment().getKey().getKey()));
        }
        generated.replaceAll(AugmentLore::markOwned);
        existing.addAll(generated);
        meta.lore(existing);
        item.setItemMeta(meta);
        ledger.setOwnedLore(item, generated.stream().map(Component::toString).toList());
    }

    private static void removeOwnedBlock(List<Component> existing, List<String> owned) {
        if (owned == null || owned.isEmpty() || owned.size() > existing.size()) return;
        for (int start = existing.size() - owned.size(); start >= 0; start--) {
            boolean matches = true;
            for (int offset = 0; offset < owned.size(); offset++) {
                Component candidate = existing.get(start + offset);
                if (!candidate.toString().contains(OWNERSHIP_MARKER)
                        || !owned.get(offset).equals(candidate.toString())) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            for (int offset = 0; offset < owned.size(); offset++) existing.remove(start);
            return;
        }
    }

    private static Component markOwned(Component line) {
        return line.append(Component.text(OWNERSHIP_MARKER));
    }
}
