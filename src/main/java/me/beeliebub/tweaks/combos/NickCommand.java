package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NickCommand implements CommandExecutor, Listener {

    private static final LegacyComponentSerializer COLOR_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .build();

    private final JavaPlugin plugin;
    private final NamespacedKey nickKey;

    private final Set<UUID> pendingRemovals = ConcurrentHashMap.newKeySet();
    private final File pendingFile;

    public NickCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nickKey = new NamespacedKey(plugin, "nickname");
        this.pendingFile = new File(plugin.getDataFolder(), "nick-removals.yml");
        loadPendingRemovals();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /nick <nickname> | /nick off [player]")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            return handleOff(sender, args);
        }

        return handleSet(sender, args);
    }

    private boolean handleOff(CommandSender sender, String[] args) {

        if (args.length == 2) {
            if (!sender.hasPermission("tweaks.admin.nick")) {
                sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
                return true;
            }

            String targetName = args[1];

            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            if (onlineTarget != null) {
                clearNickname(onlineTarget);
                sender.sendMessage(Component.text("Removed nickname for " + onlineTarget.getName() + ".")
                        .color(NamedTextColor.GREEN));
                onlineTarget.sendMessage(Component.text("Your nickname has been removed by an admin.")
                        .color(NamedTextColor.YELLOW));
                return true;
            }

            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

            if (!offlineTarget.hasPlayedBefore()) {
                sender.sendMessage(Component.text("Player '" + targetName + "' has never joined the server.")
                        .color(NamedTextColor.RED));
                return true;
            }

            pendingRemovals.add(offlineTarget.getUniqueId());
            savePendingRemovalsAsync();

            String resolvedName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
            sender.sendMessage(Component.text("Nickname removal queued for " + resolvedName
                            + ". It will be cleared on their next login.")
                    .color(NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Console usage: /nick off <player>")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("tweaks.nick")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.getPersistentDataContainer().has(nickKey)) {
            player.sendMessage(Component.text("You don't have a nickname set.")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        clearNickname(player);
        player.sendMessage(Component.text("Nickname removed!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set nicknames.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("tweaks.nick")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }

        String rawInput = String.join(" ", args);
        Component nickname = COLOR_SERIALIZER.deserialize(rawInput);

        player.getPersistentDataContainer().set(nickKey, PersistentDataType.STRING, rawInput);

        player.displayName(nickname);
        player.playerListName(nickname);

        player.sendMessage(Component.text("Nickname set to ").color(NamedTextColor.GREEN)
                .append(nickname)
                .append(Component.text("!").color(NamedTextColor.GREEN)));
        return true;
    }

    private void clearNickname(Player player) {
        player.getPersistentDataContainer().remove(nickKey);
        player.displayName(null);
        player.playerListName(null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingRemovals.remove(uuid)) {
            clearNickname(player);
            savePendingRemovalsAsync();
            return;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String rawNick = pdc.get(nickKey, PersistentDataType.STRING);

        if (rawNick != null && !rawNick.isEmpty()) {
            Component nickname = COLOR_SERIALIZER.deserialize(rawNick);
            player.displayName(nickname);
            player.playerListName(nickname);
        }
    }


    private void loadPendingRemovals() {
        if (!pendingFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingFile);
        List<String> UUIDs = config.getStringList("pending");

        for (String raw : UUIDs) {
            try {
                pendingRemovals.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in nick-removals.yml: " + raw);
            }
        }
    }

    private void savePendingRemovalsAsync() {
        List<String> snapshot = pendingRemovals.stream()
                .map(UUID::toString)
                .toList();

        CompletableFuture.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            config.set("pending", snapshot);
            try {
                config.save(pendingFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save nick-removals.yml: " + e.getMessage());
            }
        });
    }
}