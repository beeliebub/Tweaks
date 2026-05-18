package me.beeliebub.tweaks.playeradmin;

import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

// Single executor for the player-administration commands:
// /survival, /creative (GameMode), /nv (NightVision), /fly, /nick, /afk.
// Shared state lives in PlayerAdminManager.
public class PlayerAdminCommand implements CommandExecutor {

    private final PlayerAdminManager manager;

    public PlayerAdminCommand(PlayerAdminManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        return switch (label.toLowerCase()) {
            case "survival", "creative" -> handleGameMode(sender, command);
            case "nv"                   -> handleNightVision(sender);
            case "fly"                  -> handleFly(sender);
            case "afk"                  -> handleAfk(sender);
            case "nick"                 -> handleNick(sender, args);
            default -> false;
        };
    }

    // ============================================================
    // /survival, /creative
    // ============================================================

    private boolean handleGameMode(CommandSender sender, Command command) {
        if (!sender.hasPermission(Permissions.ADMIN_GAMEMODE)) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can change their gamemode.", NamedTextColor.RED));
            return true;
        }
        GameMode target = switch (command.getName().toLowerCase()) {
            case "survival" -> GameMode.SURVIVAL;
            case "creative" -> GameMode.CREATIVE;
            default -> null;
        };
        if (target == null) return false;

        if (player.getGameMode() == target) {
            player.sendMessage(Component.text("Already in " + target.name().toLowerCase() + " mode.", NamedTextColor.YELLOW));
            return true;
        }
        player.setGameMode(target);
        player.sendMessage(Component.text("Gamemode set to " + target.name().toLowerCase() + ".", NamedTextColor.GREEN));
        return true;
    }

    // ============================================================
    // /nv
    // ============================================================

    private boolean handleNightVision(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players need night vision!", NamedTextColor.RED));
            return true;
        }
        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendMessage(Component.text("Night vision disabled!", NamedTextColor.GREEN));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            player.sendMessage(Component.text("Night vision enabled!", NamedTextColor.GREEN));
        }
        return true;
    }

    // ============================================================
    // /fly
    // ============================================================

    private boolean handleFly(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can fly.").color(NamedTextColor.RED));
            return true;
        }
        if (player.getAllowFlight()) {
            manager.disableFlight(player);
            player.sendMessage(Component.text("Flight disabled.").color(NamedTextColor.RED));
            return true;
        }
        if (!manager.canFly(player)) {
            player.sendMessage(Component.text("You don't have access to flight in this world.")
                    .color(NamedTextColor.RED));
            return true;
        }
        manager.enableFlight(player);
        player.sendMessage(Component.text("Flight enabled!").color(NamedTextColor.GREEN));
        return true;
    }

    // ============================================================
    // /afk
    // ============================================================

    private boolean handleAfk(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can go AFK.", NamedTextColor.RED));
            return true;
        }
        if (manager.isAfk(player)) {
            manager.exitAfk(player, true);
        } else {
            manager.enterAfk(player);
        }
        return true;
    }

    // ============================================================
    // /nick
    // ============================================================

    private boolean handleNick(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /nick <nickname> | /nick off [player]")
                    .color(NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("off")) {
            return handleNickOff(sender, args);
        }
        return handleNickSet(sender, args);
    }

    private boolean handleNickOff(CommandSender sender, String[] args) {
        if (args.length == 2) {
            if (!sender.hasPermission(Permissions.ADMIN_NICK)) {
                sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
                return true;
            }
            String targetName = args[1];

            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            if (onlineTarget != null) {
                manager.clearNickname(onlineTarget);
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
            manager.queueOfflineNickRemoval(offlineTarget.getUniqueId());
            String resolvedName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
            sender.sendMessage(Component.text("Nickname removal queued for " + resolvedName
                            + ". It will be cleared on their next login.")
                    .color(NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Console usage: /nick off <player>").color(NamedTextColor.RED));
            return true;
        }
        if (!player.getPersistentDataContainer().has(manager.nickKey())) {
            player.sendMessage(Component.text("You don't have a nickname set.").color(NamedTextColor.YELLOW));
            return true;
        }
        manager.clearNickname(player);
        player.sendMessage(Component.text("Nickname removed!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleNickSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set nicknames.").color(NamedTextColor.RED));
            return true;
        }
        String rawInput = String.join(" ", args);
        if (rawInput.contains(" ")) {
            player.sendMessage(Component.text("Nicknames cannot contain spaces.").color(NamedTextColor.RED));
            return true;
        }
        if (rawInput.matches("(?i).*&k.*")) {
            player.sendMessage(Component.text("Nicknames cannot use the magic effect (&k).").color(NamedTextColor.RED));
            return true;
        }
        String visibleText = PlayerAdminManager.stripNickColorCodes(rawInput);
        if (visibleText.length() > manager.maxNickLength()) {
            player.sendMessage(Component.text("Nickname too long! Max " + manager.maxNickLength()
                            + " visible characters (yours: " + visibleText.length() + ").")
                    .color(NamedTextColor.RED));
            return true;
        }
        Component nickname = manager.nickColorSerializer().deserialize(rawInput);
        player.getPersistentDataContainer().set(manager.nickKey(), PersistentDataType.STRING, rawInput);
        player.displayName(nickname);
        player.sendMessage(Component.text("Nickname set to ").color(NamedTextColor.GREEN)
                .append(nickname)
                .append(Component.text("!").color(NamedTextColor.GREEN)));
        return true;
    }
}
