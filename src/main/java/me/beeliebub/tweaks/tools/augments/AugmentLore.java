package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Renders and replaces only the exact lore lines owned by the augment feature. */
public final class AugmentLore {

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
        existing.removeIf(this::isOwnedLine);
        int capacity = slots.capacity(item.getType());
        int purchased = ledger.slots(item);
        List<AugmentEntry> entries = ledger.entries(item);
        int used = slots.used(entries);
        existing.add(Messages.TOOLS.augmentSlotLore(slots.slotDots(purchased, used, capacity)));
        for (AugmentEntry entry : entries) {
            var enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(entry.enchantmentKey());
            String name = enchantment == null ? entry.enchantmentKey().toString() : enchantment.getKey().getKey();
            existing.add(Messages.TOOLS.augmentEntryLore(name, entry.level(), entry.active()));
        }
        meta.lore(existing);
        item.setItemMeta(meta);
    }

    private boolean isOwnedLine(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return plain.matches("Augment Slots: [◌○●]*")
                || plain.matches("Augment: [A-Za-z0-9_.:-]+ [1-9][0-9]* [○●]");
    }
}
