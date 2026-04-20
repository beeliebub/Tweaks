package me.beeliebub.tweaks.minigames.andrew;

import me.beeliebub.tweaks.minigames.RewardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WhackCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final WhackConfig config;
    private final RewardManager rewardManager;

    private WhackArena arena;
    private WhackGame game;
    private WhackListener listener;
    private List<Material> spawnBlockMaterials = new ArrayList<>();

    // Arena setup state per player
    private final Map<UUID, Location> pendingCorner1 = new HashMap<>();

    public WhackCommand(JavaPlugin plugin, WhackConfig config, RewardManager rewardManager) {
        this.plugin = plugin;
        this.config = config;
        this.rewardManager = rewardManager;

        // Restore arena from config
        arena = config.loadArena();
        if (arena != null) {
            plugin.getLogger().info("Whack arena loaded with " + arena.getSpawnBlocks().size() + " spawn blocks.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("tweaks.admin.whack")) {
            player.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "arena" -> handleArena(player, label);
            case "corner1" -> handleCorner1(player);
            case "corner2" -> handleCorner2(player);
            case "setblocks" -> handleSetBlocks(player, args);
            case "start" -> handleStart(player);
            case "pause" -> handlePause(player);
            case "stop" -> handleStop(player);
            case "setreward" -> handleSetReward(player, args);
            case "reload" -> handleReload(player);
            default -> sendUsage(player, label);
        }

        return true;
    }

    private void handleArena(Player player, String label) {
        player.sendMessage(Component.text("Arena setup started.").color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Look at the bottom corner and run: ").color(NamedTextColor.YELLOW)
                .append(Component.text("/" + label + " corner1").color(NamedTextColor.GOLD)));
        player.sendMessage(Component.text("Then look at the opposite top corner and run: ").color(NamedTextColor.YELLOW)
                .append(Component.text("/" + label + " corner2").color(NamedTextColor.GOLD)));
        pendingCorner1.remove(player.getUniqueId());
    }

    private void handleCorner1(Player player) {
        Location target = player.getTargetBlockExact(100).getLocation();
        if (target == null) {
            player.sendMessage(Component.text("Look at a block to set as corner 1.").color(NamedTextColor.RED));
            return;
        }
        pendingCorner1.put(player.getUniqueId(), target);
        player.sendMessage(Component.text("Corner 1 set at " + formatLoc(target) + ".").color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Now look at the opposite corner and run /whack corner2.").color(NamedTextColor.YELLOW));
    }

    private void handleCorner2(Player player) {
        Location corner1 = pendingCorner1.remove(player.getUniqueId());
        if (corner1 == null) {
            player.sendMessage(Component.text("You must set corner 1 first! Run /whack arena.").color(NamedTextColor.RED));
            return;
        }

        Location target = player.getTargetBlockExact(100).getLocation();
        if (target == null) {
            player.sendMessage(Component.text("Look at a block to set as corner 2.").color(NamedTextColor.RED));
            pendingCorner1.put(player.getUniqueId(), corner1);
            return;
        }

        if (!corner1.getWorld().equals(target.getWorld())) {
            player.sendMessage(Component.text("Both corners must be in the same world.").color(NamedTextColor.RED));
            return;
        }

        // Clean up old game if present
        if (game != null && game.getState() != WhackGame.State.IDLE) {
            game.stop();
        }

        arena = new WhackArena(corner1, target);
        spawnBlockMaterials.clear();
        game = null;
        listener = null;

        config.saveArena(arena, spawnBlockMaterials);

        player.sendMessage(Component.text("Arena created from " + formatLoc(corner1) + " to " + formatLoc(target) + "!")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Now set spawn blocks with /whack setblocks <material>").color(NamedTextColor.YELLOW));
    }

    private void handleSetBlocks(Player player, String[] args) {
        if (arena == null) {
            player.sendMessage(Component.text("Create an arena first with /whack arena.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /whack setblocks <material> [material...]").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Example: /whack setblocks hay_block gold_block").color(NamedTextColor.GRAY));
            return;
        }

        List<Material> materials = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Material mat = Material.matchMaterial(args[i]);
            if (mat == null || !mat.isBlock()) {
                player.sendMessage(Component.text("Unknown block material: " + args[i]).color(NamedTextColor.RED));
                return;
            }
            materials.add(mat);
        }

        int count = arena.scanForBlocks(materials.toArray(Material[]::new));
        spawnBlockMaterials = materials;
        config.saveArena(arena, spawnBlockMaterials);

        player.sendMessage(Component.text("Found " + count + " spawn locations in the arena.").color(NamedTextColor.GREEN));

        if (count == 0) {
            player.sendMessage(Component.text("No matching blocks were found in the arena bounds.").color(NamedTextColor.YELLOW));
        }
    }

    private void handleStart(Player player) {
        if (arena == null) {
            player.sendMessage(Component.text("Create an arena first with /whack arena.").color(NamedTextColor.RED));
            return;
        }

        if (arena.getSpawnBlocks().isEmpty()) {
            player.sendMessage(Component.text("Set spawn blocks first with /whack setblocks <material>.").color(NamedTextColor.RED));
            return;
        }

        if (game == null || game.getState() == WhackGame.State.IDLE) {
            game = new WhackGame(plugin, config, arena, rewardManager);
            // Register listener fresh each game
            if (listener != null) {
                EntityDamageByEntityEvent.getHandlerList().unregister(listener);
            }
            listener = new WhackListener(game);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        game.start();
    }

    private void handlePause(Player player) {
        if (game == null || game.getState() != WhackGame.State.RUNNING) {
            player.sendMessage(Component.text("No game is currently running.").color(NamedTextColor.RED));
            return;
        }
        game.pause();
    }

    private void handleStop(Player player) {
        if (game == null || game.getState() == WhackGame.State.IDLE) {
            player.sendMessage(Component.text("No game is currently active.").color(NamedTextColor.RED));
            return;
        }
        game.stop();

        if (listener != null) {
            EntityDamageByEntityEvent.getHandlerList().unregister(listener);
            listener = null;
        }
    }

    private void handleSetReward(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /whack setreward <1|2|3> <reward_name>").color(NamedTextColor.RED));
            return;
        }

        int place;
        try {
            place = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Place must be 1, 2, or 3.").color(NamedTextColor.RED));
            return;
        }

        if (place < 1 || place > 3) {
            player.sendMessage(Component.text("Place must be 1, 2, or 3.").color(NamedTextColor.RED));
            return;
        }

        String rewardName = args[2].toLowerCase();
        if (!rewardManager.rewardExists(rewardName)) {
            player.sendMessage(Component.text("No reward named '" + rewardName + "' exists.").color(NamedTextColor.RED));
            return;
        }

        config.setPlaceReward(place, rewardName);
        String ordinal = switch (place) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> place + "th";
        };
        player.sendMessage(Component.text(ordinal + " place reward set to '" + rewardName + "'.").color(NamedTextColor.GREEN));
    }

    private void handleReload(Player player) {
        config.load();
        player.sendMessage(Component.text("whack.yml reloaded.").color(NamedTextColor.GREEN));
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(Component.text("=== Whack an Andrew ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/" + label + " arena").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Begin arena setup").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " corner1").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Set first corner (look at block)").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " corner2").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Set second corner (look at block)").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " setblocks <material...>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Scan arena for spawn blocks").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " start").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Start the game").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " pause").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Pause the game").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " stop").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Stop the game").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " setreward <1|2|3> <name>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Set place reward").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/" + label + " reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Reload whack.yml config").color(NamedTextColor.GRAY)));
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tweaks.admin.whack")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = List.of("arena", "corner1", "corner2", "setblocks", "start", "pause", "stop", "setreward", "reload");
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setreward")) {
            return List.of("1", "2", "3").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setreward")) {
            String partial = args[2].toLowerCase();
            return rewardManager.getRewardNames().stream()
                    .filter(n -> n.startsWith(partial))
                    .toList();
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("setblocks")) {
            String partial = args[args.length - 1].toLowerCase();
            return Arrays.stream(Material.values())
                    .filter(Material::isBlock)
                    .map(m -> m.name().toLowerCase())
                    .filter(n -> n.startsWith(partial))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}