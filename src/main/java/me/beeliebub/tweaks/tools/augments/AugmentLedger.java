package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/** Sole owner of the augment PDC ledger and its conservative versioned decoder. */
public final class AugmentLedger {

    private static final String VERSION = "v1";
    /** Bounds hostile or accidentally oversized PDC data before it reaches menus or mutations. */
    public static final int MAX_ATTACHED_ENTRIES = 256;
    public static final int MAX_CURSE_ENTRIES = AugmentGemItem.MAX_CURSE_RIDERS;
    // One generated line per slot entry, one bounded catch-all line per entry, plus curses and the header.
    private static final int MAX_OWNED_LORE_LINES = MAX_ATTACHED_ENTRIES * 2 + MAX_CURSE_ENTRIES + 1;
    private final Tweaks plugin;
    private final NamespacedKey slotsKey;
    private final NamespacedKey attachedKey;
    private final NamespacedKey migratedKey;
    private final NamespacedKey cursesKey;
    private final NamespacedKey ownedLoreKey;

    public AugmentLedger(Tweaks plugin) {
        this.plugin = plugin;
        this.slotsKey = new NamespacedKey(plugin, "augment_slots_purchased");
        this.attachedKey = new NamespacedKey(plugin, "augment_attached");
        this.migratedKey = new NamespacedKey(plugin, "augment_migrated");
        this.cursesKey = new NamespacedKey(plugin, "augment_curses");
        this.ownedLoreKey = new NamespacedKey(plugin, "augment_owned_lore");
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
        if (raw.size() > MAX_ATTACHED_ENTRIES) {
            warn("Oversized augment entry list; entries were skipped");
            return List.of();
        }
        List<AugmentEntry> entries = new ArrayList<>();
        for (String encoded : raw) {
            AugmentEntry parsed = parse(encoded);
            if (parsed != null) entries.add(parsed);
        }
        return List.copyOf(entries);
    }

    public List<AugmentGemItem.CurseRider> curses(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) return List.of();
        List<String> raw;
        try {
            raw = pdc.getOrDefault(cursesKey, PersistentDataType.LIST.strings(), List.of());
        } catch (IllegalArgumentException e) {
            warn("Invalid augment curse list; curses were skipped");
            return List.of();
        }
        if (raw.size() > MAX_CURSE_ENTRIES) {
            warn("Oversized augment curse list; curses were skipped");
            return List.of();
        }
        List<AugmentGemItem.CurseRider> curses = new ArrayList<>();
        for (String encoded : raw) {
            AugmentGemItem.CurseRider parsed = parseCurse(encoded);
            if (parsed != null) curses.add(parsed);
        }
        return List.copyOf(curses);
    }

    /** Returns the exact component fingerprints owned by the augment lore renderer. */
    public List<String> ownedLore(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) return List.of();
        try {
            List<String> owned = pdc.getOrDefault(ownedLoreKey, PersistentDataType.LIST.strings(), List.of());
            if (owned == null || owned.size() > MAX_OWNED_LORE_LINES) {
                warn("Oversized augment lore ownership metadata; existing lore was preserved");
                return List.of();
            }
            return owned.stream().filter(value -> value != null).toList();
        } catch (IllegalArgumentException malformed) {
            warn("Invalid augment lore ownership metadata; existing lore was preserved");
            return List.of();
        }
    }

    public void setOwnedLore(ItemStack item, List<String> fingerprints) {
        if (item == null || item.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> safe = fingerprints == null ? List.of() : fingerprints.stream()
                .filter(value -> value != null)
                .toList();
        if (safe.size() > MAX_OWNED_LORE_LINES) {
            warn("Oversized augment lore ownership metadata was not written");
            return;
        }
        pdc.set(ownedLoreKey, PersistentDataType.LIST.strings(), safe);
        item.setItemMeta(meta);
    }

    public boolean hasMalformedCurses(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null || !pdc.getKeys().contains(cursesKey)) return false;
        try {
            pdc.get(cursesKey, PersistentDataType.LIST.strings());
            return false;
        } catch (IllegalArgumentException e) {
            warn("Invalid augment curse list; attachment was refused");
            return true;
        }
    }

    /**
     * Validates every owned ledger field before a destructive recovery operation can use it.
     * Ordinary reads intentionally remain tolerant and continue to skip isolated bad records.
     */
    public boolean recoveryStateValid(ItemStack item, Registry<Enchantment> enchantments) {
        PersistentDataContainer container = pdc(item);
        if (container == null) return true;

        Set<NamespacedKey> attachedKeys = new HashSet<>();
        Set<NamespacedKey> curseKeys = new HashSet<>();
        Set<Enchantment> knownCurses = AugmentService.resolveCurseEnchantments();

        if (container.getKeys().contains(slotsKey)) {
            if (!container.has(slotsKey, PersistentDataType.INTEGER)) {
                warn("Invalid augment slot ledger; recovery was refused");
                return false;
            }
            Integer slots = container.get(slotsKey, PersistentDataType.INTEGER);
            if (slots == null || slots < 0) {
                warn("Invalid augment slot value; recovery was refused");
                return false;
            }
        }
        if (container.getKeys().contains(migratedKey)) {
            if (!container.has(migratedKey, PersistentDataType.BYTE)) {
                warn("Invalid augment migration marker; recovery was refused");
                return false;
            }
            Byte marker = container.get(migratedKey, PersistentDataType.BYTE);
            if (marker == null || marker != (byte) 1) {
                warn("Invalid augment migration marker; recovery was refused");
                return false;
            }
        }
        if (container.getKeys().contains(attachedKey)) {
            if (!container.has(attachedKey, PersistentDataType.LIST.strings())) {
                warn("Invalid augment entry list; recovery was refused");
                return false;
            }
            List<String> raw = container.get(attachedKey, PersistentDataType.LIST.strings());
            if (raw == null || raw.size() > MAX_ATTACHED_ENTRIES) {
                warn("Oversized augment entry list; recovery was refused");
                return false;
            }
            for (String encoded : raw) {
                AugmentEntry entry = parse(encoded);
                Enchantment resolved = entry == null || enchantments == null
                        ? null : enchantments.get(entry.enchantmentKey());
                if (entry == null || resolved == null || knownCurses.contains(resolved)
                        || !attachedKeys.add(entry.enchantmentKey())) {
                    warn("Unresolved or malformed augment entry; recovery was refused");
                    return false;
                }
            }
        }
        if (container.getKeys().contains(cursesKey)) {
            if (!container.has(cursesKey, PersistentDataType.LIST.strings())) {
                warn("Invalid augment curse list; recovery was refused");
                return false;
            }
            List<String> raw = container.get(cursesKey, PersistentDataType.LIST.strings());
            if (raw == null || raw.size() > MAX_CURSE_ENTRIES) {
                warn("Oversized augment curse list; recovery was refused");
                return false;
            }
            for (String encoded : raw) {
                AugmentGemItem.CurseRider curse = parseCurse(encoded);
                if (curse == null || !curseKeys.add(curse.enchantment().getKey())) {
                    warn("Unresolved or malformed augment curse; recovery was refused");
                    return false;
                }
            }
        }
        return true;
    }

    public void write(ItemStack item, int slots, List<AugmentEntry> entries, boolean migrated) {
        if (item == null || item.isEmpty() || entries == null || entries.size() > MAX_ATTACHED_ENTRIES) {
            if (entries != null && entries.size() > MAX_ATTACHED_ENTRIES) {
                warn("Oversized augment entry list was not written");
            }
            return;
        }
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

    public boolean appendCurses(ItemStack item, List<AugmentGemItem.CurseRider> curses) {
        if (item == null || item.isEmpty() || curses == null || curses.isEmpty()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> encoded = new ArrayList<>();
        try {
            List<String> existing = pdc.getOrDefault(cursesKey, PersistentDataType.LIST.strings(), List.of());
            if (existing == null) return false;
            encoded.addAll(existing);
        } catch (IllegalArgumentException e) {
            warn("Invalid augment curse list; attachment was refused");
            return false;
        }
        if (encoded.size() > MAX_CURSE_ENTRIES) {
            warn("Oversized augment curse list; existing curses were preserved");
            return false;
        }
        Set<org.bukkit.enchantments.Enchantment> knownCurses = AugmentService.resolveCurseEnchantments();
        List<String> additions = new ArrayList<>();
        for (AugmentGemItem.CurseRider curse : curses) {
            if (curse == null || curse.enchantment() == null || curse.level() <= 0
                    || !knownCurses.contains(curse.enchantment())) continue;
            String value = encodeCurse(curse);
            if (!encoded.contains(value) && !additions.contains(value)) additions.add(value);
        }
        if (encoded.size() + additions.size() > MAX_CURSE_ENTRIES) {
            warn("Augment curse list would exceed its supported bound; attachment was refused");
            return false;
        }
        encoded.addAll(additions);
        pdc.set(cursesKey, PersistentDataType.LIST.strings(), encoded);
        item.setItemMeta(meta);
        return true;
    }

    public void markMigrated(ItemStack item) {
        write(item, slots(item), entries(item), true);
    }

    public String encode(AugmentEntry entry) {
        return VERSION + "|" + entry.enchantmentKey() + "|" + entry.level() + "|" + entry.active();
    }

    private String encodeCurse(AugmentGemItem.CurseRider curse) {
        return VERSION + "|" + curse.enchantment().getKey() + "|" + curse.level();
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

    private AugmentGemItem.CurseRider parseCurse(String encoded) {
        if (encoded == null) {
            warn("Skipping unknown or malformed augment curse");
            return null;
        }
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 3 || !VERSION.equals(fields[0])) {
            warn("Skipping unknown or malformed augment curse");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(fields[1]);
        if (key == null) {
            warn("Skipping augment curse with invalid enchantment key: " + fields[1]);
            return null;
        }
        int level;
        try {
            level = Integer.parseInt(fields[2]);
        } catch (NumberFormatException e) {
            warn("Skipping augment curse with invalid level");
            return null;
        }
        if (level <= 0) {
            warn("Skipping augment curse with invalid level");
            return null;
        }
        var enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key);
        if (enchantment == null || !AugmentService.resolveCurseEnchantments().contains(enchantment)) {
            warn("Skipping augment curse with unknown enchantment: " + fields[1]);
            return null;
        }
        return new AugmentGemItem.CurseRider(enchantment, level);
    }

    private PersistentDataContainer pdc(ItemStack item) {
        return item == null || item.isEmpty() || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer();
    }

    private void warn(String message) {
        plugin.getLogger().log(Level.WARNING, message);
    }
}
