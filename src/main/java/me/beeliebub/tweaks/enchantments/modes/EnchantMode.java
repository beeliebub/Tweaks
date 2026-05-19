package me.beeliebub.tweaks.enchantments.modes;

import me.beeliebub.tweaks.enchantments.Efficacy;
import me.beeliebub.tweaks.enchantments.Tunneller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

// PDC-backed area-mode storage shared by Tunneller and Efficacy, plus the
// sneak+right-click listener that cycles modes. Previously this lived across
// two files (EnchantMode + EnchantModeListener).
//
// Each quality-enchanted tool has a maximum area radius set by its tier (uncommon = 2 / 5x5,
// rare = 3 / 7x7, epic = 4 / 9x9, legendary = 5 / 11x11). Mode lets the player downshift to a
// smaller area without losing the enchantment — e.g. a legendary tunneller can cycle through
// 3x3, 5x5, 7x7, 9x9, 11x11.
//
// Cycling is sneak + right-click on the held tool; the mode read by Tunneller/Efficacy is the PDC
// value clamped to [1, maxRadius]. Absence of the PDC value means "use the tool's maximum"
// (i.e. the default behavior before this feature shipped).
public final class EnchantMode implements Listener {

    public static final String MODE_LORE_PREFIX = "Mode: ";

    private final NamespacedKey modeKey;
    private Tunneller tunneller;
    private Efficacy efficacy;

    public EnchantMode(JavaPlugin plugin) {
        this.modeKey = new NamespacedKey(plugin, "enchant_mode");
    }

    // Wire up the enchant dependencies after construction. Both Tunneller and
    // Efficacy take EnchantMode in their own constructors, so the listener
    // dependencies are injected here to avoid the circular reference.
    public void wireEnchantments(Tunneller tunneller, Efficacy efficacy) {
        this.tunneller = tunneller;
        this.efficacy = efficacy;
    }

    public NamespacedKey modeKey() {
        return modeKey;
    }

    // Returns the effective mode (clamped to [1, maxRadius]). If the PDC value is unset, returns
    // maxRadius so legacy tools (created before this feature) behave exactly as they did before.
    public int getMode(ItemStack tool, int maxRadius) {
        if (maxRadius <= 0) return 0;
        if (tool == null || !tool.hasItemMeta()) return maxRadius;
        Integer raw = tool.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.INTEGER);
        if (raw == null) return maxRadius;
        if (raw < 1) return 1;
        if (raw > maxRadius) return maxRadius;
        return raw;
    }

    public void setMode(ItemStack tool, int mode) {
        if (tool == null || tool.isEmpty()) return;
        tool.editMeta(meta -> {
            meta.getPersistentDataContainer().set(modeKey, PersistentDataType.INTEGER, mode);
            applyModeLore(meta, mode);
        });
    }

    public void clearMode(ItemStack tool) {
        if (tool == null || tool.isEmpty()) return;
        tool.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(modeKey);
            removeOurLore(meta);
        });
    }

    public void applyModeLore(ItemMeta meta, int radius) {
        int size = radius * 2 + 1;
        Component line = Component.text(MODE_LORE_PREFIX + size + "x" + size, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(EnchantMode::isOurModeLore);
        lore.add(line);
        meta.lore(lore);
    }

    private void removeOurLore(ItemMeta meta) {
        if (meta.lore() == null) return;
        List<Component> lore = new ArrayList<>(meta.lore());
        if (lore.removeIf(EnchantMode::isOurModeLore)) {
            meta.lore(lore.isEmpty() ? null : lore);
        }
    }

    static boolean isOurModeLore(Component c) {
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        return plain.startsWith(MODE_LORE_PREFIX) && plain.matches("Mode: \\d+x\\d+");
    }

    // ─── Listener (sneak + right-click cycles mode down) ──────────────────────

    // LOWEST so the cancellation reaches Efficacy's default-priority listener via
    // ignoreCancelled = true — otherwise the area path/till/strip would happen on the same click
    // as the mode cycle, defeating the sneak-click input convention.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.isEmpty()) return;

        int maxRadius = resolveMaxRadius(tool);
        if (maxRadius < 2) return; // 3x3-only tools have nothing to cycle through.

        event.setCancelled(true);

        int current = getMode(tool, maxRadius);
        int next = current - 1;
        if (next < 1) next = maxRadius;
        setMode(tool, next);

        int size = next * 2 + 1;
        player.sendActionBar(Component.text("Mode: ", NamedTextColor.GRAY)
                .append(Component.text(size + "x" + size, NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    private int resolveMaxRadius(ItemStack tool) {
        int tunnellerMax = tunneller != null ? tunneller.getMaxRadius(tool) : 0;
        if (tunnellerMax > 0) return tunnellerMax;
        return efficacy != null ? efficacy.getMaxRadius(tool) : 0;
    }
}
