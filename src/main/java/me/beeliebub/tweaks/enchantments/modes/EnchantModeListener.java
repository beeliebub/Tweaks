package me.beeliebub.tweaks.enchantments.modes;

import me.beeliebub.tweaks.enchantments.Efficacy;
import me.beeliebub.tweaks.enchantments.Tunneller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

// Sneak + right-click cycles the held tool's area mode down by one. When the smallest area (3x3)
// is reached, the next cycle wraps back to the tool's max radius. Cancels the event so a sneaking
// player doesn't simultaneously trigger Efficacy's path/till/strip on the same click, and so axes
// don't strip a log on the same input. Tools whose max radius is 1 (base efficacy without quality)
// are skipped — there's nothing meaningful to cycle.
//
// Mode storage and lore are handled by EnchantMode; this listener just dispatches.
public class EnchantModeListener implements Listener {

    private final EnchantMode enchantMode;
    private final Tunneller tunneller;
    private final Efficacy efficacy;

    public EnchantModeListener(EnchantMode enchantMode, Tunneller tunneller, Efficacy efficacy) {
        this.enchantMode = enchantMode;
        this.tunneller = tunneller;
        this.efficacy = efficacy;
    }

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

        int current = enchantMode.getMode(tool, maxRadius);
        int next = current - 1;
        if (next < 1) next = maxRadius;
        enchantMode.setMode(tool, next);

        int size = next * 2 + 1;
        player.sendActionBar(Component.text("Mode: ", NamedTextColor.GRAY)
                .append(Component.text(size + "x" + size, NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    // Resolves the tool's max area radius from its enchantment. Tunneller and Efficacy both expose
    // getMaxRadius; whichever one returns >0 is the active enchant (a single tool only has one).
    private int resolveMaxRadius(ItemStack tool) {
        int tunnellerMax = tunneller != null ? tunneller.getMaxRadius(tool) : 0;
        if (tunnellerMax > 0) return tunnellerMax;
        int efficacyMax = efficacy != null ? efficacy.getMaxRadius(tool) : 0;
        return efficacyMax;
    }
}
