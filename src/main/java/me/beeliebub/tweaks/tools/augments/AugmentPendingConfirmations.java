package me.beeliebub.tweaks.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread owner for short-lived, quoted legacy-migration confirmations. */
public final class AugmentPendingConfirmations {

    public static final long EXPIRY_TICKS = 600L;

    private final Tweaks plugin;
    private final Map<UUID, PendingRequest> pending = new HashMap<>();

    public AugmentPendingConfirmations(Tweaks plugin) {
        this.plugin = plugin;
    }

    public PendingRequest create(Player player, ItemStack item, int quotedCost) {
        UUID uuid = player.getUniqueId();
        cancel(uuid);
        PendingRequest request = new PendingRequest(uuid, ItemSignature.capture(item), quotedCost);
        pending.put(uuid, request);
        request.expiryTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> expire(request), EXPIRY_TICKS);
        return request;
    }

    public PendingRequest validFor(Player player, ItemStack currentHeld) {
        if (player == null) return null;
        PendingRequest request = pending.get(player.getUniqueId());
        if (request == null || !request.signature.matches(currentHeld)) return null;
        return request;
    }

    public boolean cancel(UUID uuid) {
        PendingRequest request = pending.remove(uuid);
        if (request == null) return false;
        request.cancelTask();
        return true;
    }

    public void cancelAll() {
        for (UUID uuid : List.copyOf(pending.keySet())) cancel(uuid);
    }

    public boolean contains(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public int size() {
        return pending.size();
    }

    private void expire(PendingRequest request) {
        if (!pending.remove(request.playerId, request)) return;
        request.expiryTask = null;
        Player player = Bukkit.getPlayer(request.playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(Messages.TOOLS.augmentConfirmationExpired());
        }
    }

    public static final class PendingRequest {
        private final UUID playerId;
        private final ItemSignature signature;
        private final int quotedCost;
        private BukkitTask expiryTask;

        private PendingRequest(UUID playerId, ItemSignature signature, int quotedCost) {
            this.playerId = playerId;
            this.signature = signature;
            this.quotedCost = quotedCost;
        }

        public int quotedCost() {
            return quotedCost;
        }

        private void cancelTask() {
            if (expiryTask != null) expiryTask.cancel();
            expiryTask = null;
        }
    }

    private static final class ItemSignature {
        private final Material material;
        private final int amount;
        private final Map<Enchantment, Integer> enchantments;
        private final Component displayName;
        private final List<Component> lore;
        private final String pdcFingerprint;
        private final ItemStack snapshot;

        private ItemSignature(Material material, int amount, Map<Enchantment, Integer> enchantments,
                              Component displayName, List<Component> lore, String pdcFingerprint,
                              ItemStack snapshot) {
            this.material = material;
            this.amount = amount;
            this.enchantments = enchantments;
            this.displayName = displayName;
            this.lore = lore;
            this.pdcFingerprint = pdcFingerprint;
            this.snapshot = snapshot;
        }

        private static ItemSignature capture(ItemStack item) {
            if (item == null || item.isEmpty()) {
                return new ItemSignature(Material.AIR, 0, Map.of(), null, List.of(), "", null);
            }
            ItemMeta meta = item.getItemMeta();
            return new ItemSignature(item.getType(), item.getAmount(),
                    Map.copyOf(new HashMap<>(item.getEnchantments())),
                    meta.hasDisplayName() ? meta.displayName() : null,
                    meta.hasLore() && meta.lore() != null ? List.copyOf(meta.lore()) : List.of(),
                    pdcFingerprint(meta.getPersistentDataContainer()), item.clone());
        }

        private boolean matches(ItemStack current) {
            if (current == null || current.isEmpty() || snapshot == null
                    || current.getType() != material || current.getAmount() != amount
                    || !enchantments.equals(current.getEnchantments())) return false;
            ItemMeta meta = current.getItemMeta();
            Component currentName = meta.hasDisplayName() ? meta.displayName() : null;
            List<Component> currentLore = meta.hasLore() && meta.lore() != null
                    ? List.copyOf(meta.lore()) : List.of();
            if (!java.util.Objects.equals(displayName, currentName) || !lore.equals(currentLore)) return false;
            if (!pdcFingerprint.equals(pdcFingerprint(meta.getPersistentDataContainer()))) return false;
            // The explicit fields above are the confirmation identity. Damage is deliberately
            // omitted so ordinary tool use during the 30-second window remains valid.
            return true;
        }

        private static String pdcFingerprint(PersistentDataContainer pdc) {
            List<String> values = new ArrayList<>();
            pdc.getKeys().stream().sorted(Comparator.comparing(NamespacedKey::toString)).forEach(key -> {
                String encoded = encodeKnownValue(pdc, key);
                values.add(key + "=" + encoded);
            });
            return String.join(";", values);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static String encodeKnownValue(PersistentDataContainer pdc, NamespacedKey key) {
            List<PersistentDataType<?, ?>> types = List.of(
                    PersistentDataType.BYTE, PersistentDataType.SHORT, PersistentDataType.INTEGER,
                    PersistentDataType.LONG, PersistentDataType.FLOAT, PersistentDataType.DOUBLE,
                    PersistentDataType.BOOLEAN, PersistentDataType.STRING, PersistentDataType.BYTE_ARRAY,
                    PersistentDataType.INTEGER_ARRAY, PersistentDataType.LONG_ARRAY,
                    PersistentDataType.LIST.strings());
            for (PersistentDataType type : types) {
                try {
                    if (pdc.has(key, type)) return type.getPrimitiveType().getName() + ":"
                            + valueString(pdc.get(key, type));
                } catch (RuntimeException ignored) {
                    // A foreign custom type is still represented by its key below.
                }
            }
            return "unknown";
        }

        private static String valueString(Object value) {
            if (value == null) return "null";
            if (value instanceof byte[] bytes) return Arrays.toString(bytes);
            if (value instanceof int[] ints) return Arrays.toString(ints);
            if (value instanceof long[] longs) return Arrays.toString(longs);
            return String.valueOf(value);
        }
    }
}
