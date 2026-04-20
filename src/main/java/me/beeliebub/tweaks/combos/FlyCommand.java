package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Toggles creative flight for players who have earned it via an advancement or are in a fly-enabled world.
// Flight state persists across logins and is revoked when entering a non-fly world without the advancement.
public class FlyCommand implements CommandExecutor, Listener {

    private final NamespacedKey advancementKey;
    private final NamespacedKey flyKey;
    private final Set<String> defaultFlyWorlds;

    public FlyCommand(JavaPlugin plugin) {
        this.flyKey = new NamespacedKey(plugin, "fly_enabled");
        this.defaultFlyWorlds = new HashSet<>();

        String advancementName = plugin.getConfig().getString("fly-advancement", "jass:test");
        this.advancementKey = NamespacedKey.fromString(advancementName);

        List<String> configured = plugin.getConfig().getStringList("fly-worlds");
        for (String world : configured) {
            defaultFlyWorlds.add(world.toLowerCase());
        }
    }

    private boolean isDefaultFlyWorld(String worldKey) {
        return defaultFlyWorlds.contains(worldKey.toLowerCase());
    }

    private boolean hasAdvancement(Player player) {
        Advancement advancement = Bukkit.getAdvancement(advancementKey);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    private boolean canFly(Player player) {
        String worldKey = player.getWorld().getKey().asString();
        if (isDefaultFlyWorld(worldKey)) return true;
        return hasAdvancement(player);
    }

    private void enableFlight(Player player) {
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getPersistentDataContainer().set(flyKey, PersistentDataType.BOOLEAN, true);
    }

    private void disableFlight(Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getPersistentDataContainer().set(flyKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can fly.").color(NamedTextColor.RED));
            return true;
        }

        if (player.getAllowFlight()) {
            disableFlight(player);
            player.sendMessage(Component.text("Flight disabled.").color(NamedTextColor.RED));
            return true;
        }

        if (!canFly(player)) {
            player.sendMessage(Component.text("You don't have access to flight in this world.")
                    .color(NamedTextColor.RED));
            return true;
        }

        enableFlight(player);
        player.sendMessage(Component.text("Flight enabled!").color(NamedTextColor.GREEN));
        return true;
    }

    // Disable flight when moving to a world where the player doesn't have fly access
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();
        if (!player.getAllowFlight()) return;
        String toWorldKey = player.getWorld().getKey().asString();
        if (isDefaultFlyWorld(toWorldKey)) return;
        if (hasAdvancement(player)) return;

        disableFlight(player);
        player.sendMessage(Component.text("Flight disabled — you don't have access in this world.")
                .color(NamedTextColor.RED));
    }

    // Restore flight on login if the player had it enabled and still qualifies
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        Boolean stored = pdc.get(flyKey, PersistentDataType.BOOLEAN);
        if (stored == null || !stored) return;

        if (canFly(player)) {
            enableFlight(player);
        } else {
            disableFlight(player);
        }
    }
}