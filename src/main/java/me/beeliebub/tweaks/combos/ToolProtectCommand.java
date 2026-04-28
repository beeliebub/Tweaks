package me.beeliebub.tweaks.combos;

import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Per-player safety net that blocks "use" of high-tier diamond/netherite tools when their
// remaining durability has dropped below a player-set threshold. Default ON for every player
// with a 100-durability threshold. Only acts on tools that carry an EPIC or LEGENDARY quality
// enchantment so common gear isn't affected.
public class ToolProtectCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int DEFAULT_THRESHOLD = 100;
    private static final long WARN_COOLDOWN_MS = 2000L;

    private static final Set<Material> PROTECTED_TOOLS = EnumSet.of(
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );

    private final NamespacedKey enabledKey;
    private final NamespacedKey thresholdKey;
    private final QualityRegistry qualityRegistry;
    private final Map<UUID, Long> lastWarnAt = new ConcurrentHashMap<>();

    public ToolProtectCommand(JavaPlugin plugin, QualityRegistry qualityRegistry) {
        this.enabledKey = new NamespacedKey(plugin, "toolprotect_enabled");
        this.thresholdKey = new NamespacedKey(plugin, "toolprotect_threshold");
        this.qualityRegistry = qualityRegistry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /toolprotect.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showStatus(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "on"         -> handleSetEnabled(player, true);
            case "off"        -> handleSetEnabled(player, false);
            case "durability" -> handleDurability(player, args);
            default -> {
                player.sendMessage(Component.text("Usage: /toolprotect <on|off> | /toolprotect durability <n>",
                        NamedTextColor.YELLOW));
                yield true;
            }
        };
    }

    private boolean handleSetEnabled(Player player, boolean enable) {
        boolean current = isEnabled(player);
        if (current == enable) {
            player.sendMessage(Component.text("ToolProtect is already " + (enable ? "ON." : "OFF."),
                    NamedTextColor.YELLOW));
            return true;
        }
        setEnabled(player, enable);
        player.sendMessage(Component.text("ToolProtect ", NamedTextColor.GRAY)
                .append(Component.text(enable ? "ON" : "OFF",
                        enable ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(".", NamedTextColor.GRAY)));
        return true;
    }

    private boolean handleDurability(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Current threshold: ", NamedTextColor.GRAY)
                    .append(Component.text(getThreshold(player), NamedTextColor.AQUA))
                    .append(Component.text(" remaining durability.", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("Usage: /toolprotect durability <n>", NamedTextColor.YELLOW));
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("Threshold must be a whole number.", NamedTextColor.RED));
            return true;
        }
        if (value < 1) {
            player.sendMessage(Component.text("Threshold must be at least 1. Use /toolprotect off to disable.",
                    NamedTextColor.RED));
            return true;
        }
        setThreshold(player, value);
        player.sendMessage(Component.text("ToolProtect threshold set to ", NamedTextColor.GREEN)
                .append(Component.text(value, NamedTextColor.AQUA))
                .append(Component.text(" remaining durability.", NamedTextColor.GREEN)));
        return true;
    }

    private void showStatus(Player player) {
        boolean enabled = isEnabled(player);
        int threshold = getThreshold(player);
        player.sendMessage(Component.text("ToolProtect: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ON" : "OFF",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" • Threshold: ", NamedTextColor.GRAY))
                .append(Component.text(threshold, NamedTextColor.AQUA))
                .append(Component.text(" remaining", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Use /toolprotect <on|off> or /toolprotect durability <n>.",
                NamedTextColor.DARK_GRAY));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (shouldProtect(player, tool)) {
            event.setCancelled(true);
            warn(player, tool);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (shouldProtect(player, tool)) {
            event.setCancelled(true);
            warn(player, tool);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (shouldProtect(player, tool)) {
            event.setCancelled(true);
            warn(player, tool);
        }
    }

    private boolean shouldProtect(Player player, ItemStack tool) {
        if (tool == null || tool.isEmpty()) return false;
        if (!PROTECTED_TOOLS.contains(tool.getType())) return false;
        if (!isEnabled(player)) return false;
        if (!hasEpicOrLegendaryQuality(tool)) return false;

        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        int max = tool.getType().getMaxDurability();
        if (max <= 0) return false;
        int remaining = max - damageable.getDamage();
        return remaining < getThreshold(player);
    }

    private boolean hasEpicOrLegendaryQuality(ItemStack tool) {
        for (Enchantment ench : tool.getEnchantments().keySet()) {
            QualityTier tier = qualityRegistry.getTier(ench);
            if (tier == QualityTier.EPIC || tier == QualityTier.LEGENDARY) return true;
        }
        return false;
    }

    private void warn(Player player, ItemStack tool) {
        long now = System.currentTimeMillis();
        Long previous = lastWarnAt.get(player.getUniqueId());
        if (previous != null && now - previous < WARN_COOLDOWN_MS) return;
        lastWarnAt.put(player.getUniqueId(), now);

        ItemMeta meta = tool.getItemMeta();
        int remaining = (meta instanceof Damageable d) ? tool.getType().getMaxDurability() - d.getDamage() : 0;
        player.sendActionBar(Component.text("ToolProtect: " + remaining + " durability remaining (threshold "
                + getThreshold(player) + "). Repair or /toolprotect off.", NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return prefixFilter(List.of("on", "off", "durability"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("durability")) {
            return prefixFilter(List.of(Integer.toString(DEFAULT_THRESHOLD)), args[1]);
        }
        return List.of();
    }

    private static List<String> prefixFilter(List<String> options, String partial) {
        String p = partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    // PDC accessors. Default ON: absence of the key means enabled.

    private boolean isEnabled(Player player) {
        Byte b = player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return b == null || b == (byte) 1;
    }

    private void setEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE,
                (byte) (enabled ? 1 : 0));
    }

    private int getThreshold(Player player) {
        Integer t = player.getPersistentDataContainer().get(thresholdKey, PersistentDataType.INTEGER);
        return t != null ? t : DEFAULT_THRESHOLD;
    }

    private void setThreshold(Player player, int value) {
        player.getPersistentDataContainer().set(thresholdKey, PersistentDataType.INTEGER, value);
    }
}