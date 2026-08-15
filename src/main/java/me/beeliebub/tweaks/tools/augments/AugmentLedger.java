package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Sole owner of the augment PDC ledger and its conservative versioned decoder. */
public final class AugmentLedger {

    private static final String VERSION = "v1";
    private final Tweaks plugin;
    private final NamespacedKey slotsKey;
    private final NamespacedKey attachedKey;
    private final NamespacedKey migratedKey;

    public AugmentLedger(Tweaks plugin) {
        this.plugin = plugin;
        this.slotsKey = new NamespacedKey(plugin, "augment_slots_purchased");
        this.attachedKey = new NamespacedKey(plugin, "augment_attached");
        this.migratedKey = new NamespacedKey(plugin, "augment_migrated");
    }

    public static boolean hasLedger(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.getNamespace().equals("tweaks")
                        && (key.getKey().equals("augment_slots_purchased")
                        || key.getKey().equals("augment_attached")
                        || key.getKey().equals("augment_migrated")));
    }

    public int slots(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) return 0;
        try { return Math.max(0, pdc.getOrDefault(slotsKey, PersistentDataType.INTEGER, 0)); }
        catch (IllegalArgumentException e) { warn("Invalid augment slot ledger"); return 0; }
    }

    public boolean migrated(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) return false;
        try {
            Byte marker = pdc.get(migratedKey, PersistentDataType.BYTE);
            return marker != null && marker == (byte) 1;
        }
        catch (IllegalArgumentException e) { warn("Invalid augment migration marker"); return false; }
    }

    public List<AugmentEntry> entries(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) return List.of();
        List<String> raw;
        try {
            raw = pdc.getOrDefault(attachedKey, PersistentDataType.LIST.strings(), List.of());
        } catch (IllegalArgumentException e) {
            warn("Invalid augment entry list; entries were skipped");
            return List.of();
        }
        List<AugmentEntry> entries = new ArrayList<>();
        for (String encoded : raw) {
            AugmentEntry parsed = parse(encoded);
            if (parsed != null) entries.add(parsed);
        }
        return List.copyOf(entries);
    }

    public void write(ItemStack item, int slots, List<AugmentEntry> entries, boolean migrated) {
        if (item == null || item.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(slotsKey, PersistentDataType.INTEGER, Math.max(0, slots));
        List<String> encoded = new ArrayList<>();
        for (AugmentEntry entry : entries) {
            if (entry == null || entry.enchantmentKey() == null || entry.level() <= 0) continue;
            encoded.add(encode(entry));
        }
        pdc.set(attachedKey, PersistentDataType.LIST.strings(), encoded);
        if (migrated) pdc.set(migratedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    public void setSlots(ItemStack item, int slots) {
        write(item, slots, entries(item), migrated(item));
    }

    public void markMigrated(ItemStack item) {
        write(item, slots(item), entries(item), true);
    }

    public String encode(AugmentEntry entry) {
        return VERSION + "|" + entry.enchantmentKey() + "|" + entry.level() + "|" + entry.active();
    }

    private AugmentEntry parse(String encoded) {
        if (encoded == null) return null;
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 4 || !VERSION.equals(fields[0])) {
            warn("Skipping unknown or malformed augment entry");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(fields[1]);
        int level;
        if (key == null) {
            warn("Skipping augment entry with invalid enchantment key: " + fields[1]);
            return null;
        }
        try { level = Integer.parseInt(fields[2]); }
        catch (NumberFormatException e) { warn("Skipping augment entry with invalid level"); return null; }
        if (level <= 0 || !(fields[3].equals("true") || fields[3].equals("false"))) {
            warn("Skipping augment entry with invalid state");
            return null;
        }
        return new AugmentEntry(key, level, Boolean.parseBoolean(fields[3]));
    }

    private PersistentDataContainer pdc(ItemStack item) {
        return item == null || item.isEmpty() || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer();
    }

    private void warn(String message) {
        plugin.getLogger().log(Level.WARNING, message);
    }
}
