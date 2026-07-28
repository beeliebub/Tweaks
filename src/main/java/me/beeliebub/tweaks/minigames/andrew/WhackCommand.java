package me.beeliebub.tweaks.minigames.andrew;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.minigames.RewardManager;
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

// Admin command for setting up and controlling Whack-an-Andrew games.
// Subcommands: arena, corner1, corner2, setblocks, start, pause, stop, setreward, reload
public class WhackCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final WhackConfig config;
    private final RewardManager rewardManager;

    private WhackArena arena;
    private WhackGame game;
    private WhackGame registeredGame;
    private List<Material> spawnBlockMaterials = new ArrayList<>();

    // Tracks which player is mid-arena-setup (waiting for corner2 after setting corner1)
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
            sender.sendMessage(Messages.MINIGAMES.whackRequiresPlayer());
            return true;
        }

        if (!player.hasPermission(Permissions.ADMIN_WHACK)) {
            player.sendMessage(Messages.noPermission());
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
        player.sendMessage(Messages.MINIGAMES.whackArenaSetupStarted());
        player.sendMessage(Messages.MINIGAMES.whackCornerInstruction(true, label));
        player.sendMessage(Messages.MINIGAMES.whackCornerInstruction(false, label));
        pendingCorner1.remove(player.getUniqueId());
    }

    private void handleCorner1(Player player) {
        Location target = player.getTargetBlockExact(100).getLocation();
        if (target == null) {
            player.sendMessage(Messages.MINIGAMES.whackCornerTargetRequired(1));
            return;
        }
        pendingCorner1.put(player.getUniqueId(), target);
        player.sendMessage(Messages.MINIGAMES.whackCornerOneSet(formatLoc(target)));
        player.sendMessage(Messages.MINIGAMES.whackCornerTwoInstruction());
    }

    private void handleCorner2(Player player) {
        Location corner1 = pendingCorner1.remove(player.getUniqueId());
        if (corner1 == null) {
            player.sendMessage(Messages.MINIGAMES.whackCornerOneRequired());
            return;
        }

        Location target = player.getTargetBlockExact(100).getLocation();
        if (target == null) {
            player.sendMessage(Messages.MINIGAMES.whackCornerTargetRequired(2));
            pendingCorner1.put(player.getUniqueId(), corner1);
            return;
        }

        if (!corner1.getWorld().equals(target.getWorld())) {
            player.sendMessage(Messages.MINIGAMES.whackCornersSameWorldRequired());
            return;
        }

        // Clean up old game if present
        if (game != null && game.getState() != WhackGame.State.IDLE) {
            game.stop();
        }

        arena = new WhackArena(corner1, target);
        spawnBlockMaterials.clear();
        game = null;
        registeredGame = null;

        config.saveArena(arena, spawnBlockMaterials);

        player.sendMessage(Messages.MINIGAMES.whackArenaCreated(formatLoc(corner1), formatLoc(target)));
        player.sendMessage(Messages.MINIGAMES.whackSetBlocksInstruction());
    }

    private void handleSetBlocks(Player player, String[] args) {
        if (arena == null) {
            player.sendMessage(Messages.MINIGAMES.whackArenaRequired());
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Messages.MINIGAMES.whackSetBlocksUsage());
            player.sendMessage(Messages.MINIGAMES.whackSetBlocksExample());
            return;
        }

        List<Material> materials = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Material mat = Material.matchMaterial(args[i]);
            if (mat == null || !mat.isBlock()) {
                player.sendMessage(Messages.MINIGAMES.whackUnknownBlock(args[i]));
                return;
            }
            materials.add(mat);
        }

        int count = arena.scanForBlocks(materials.toArray(Material[]::new));
        spawnBlockMaterials = materials;
        config.saveArena(arena, spawnBlockMaterials);

        player.sendMessage(Messages.MINIGAMES.whackSpawnLocationsFound(count));

        if (count == 0) {
            player.sendMessage(Messages.MINIGAMES.whackNoSpawnBlocksFound());
        }
    }

    private void handleStart(Player player) {
        if (arena == null) {
            player.sendMessage(Messages.MINIGAMES.whackArenaRequired());
            return;
        }

        if (arena.getSpawnBlocks().isEmpty()) {
            player.sendMessage(Messages.MINIGAMES.whackSpawnBlocksRequired());
            return;
        }

        if (game == null || game.getState() == WhackGame.State.IDLE) {
            game = new WhackGame(plugin, config, arena, rewardManager);
            // Unregister the previous game's listener if any, then register the new game.
            if (registeredGame != null) {
                EntityDamageByEntityEvent.getHandlerList().unregister(registeredGame);
            }
            registeredGame = game;
            plugin.getServer().getPluginManager().registerEvents(registeredGame, plugin);
        }

        game.start();
    }

    private void handlePause(Player player) {
        if (game == null || game.getState() != WhackGame.State.RUNNING) {
            player.sendMessage(Messages.MINIGAMES.whackNoGameRunning());
            return;
        }
        game.pause();
    }

    private void handleStop(Player player) {
        if (game == null || game.getState() == WhackGame.State.IDLE) {
            player.sendMessage(Messages.MINIGAMES.whackNoGameActive());
            return;
        }
        game.stop();

        if (registeredGame != null) {
            EntityDamageByEntityEvent.getHandlerList().unregister(registeredGame);
            registeredGame = null;
        }
    }

    private void handleSetReward(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Messages.MINIGAMES.whackSetRewardUsage());
            return;
        }

        int place;
        try {
            place = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.MINIGAMES.whackPlaceInvalid());
            return;
        }

        if (place < 1 || place > 3) {
            player.sendMessage(Messages.MINIGAMES.whackPlaceInvalid());
            return;
        }

        String rewardName = args[2].toLowerCase();
        if (!rewardManager.rewardExists(rewardName)) {
            player.sendMessage(Messages.MINIGAMES.rewardDoesNotExist(rewardName));
            return;
        }

        config.setPlaceReward(place, rewardName);
        String ordinal = switch (place) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> place + "th";
        };
        player.sendMessage(Messages.MINIGAMES.whackPlaceRewardSet(ordinal, rewardName));
    }

    private void handleReload(Player player) {
        config.load();
        player.sendMessage(Messages.MINIGAMES.whackConfigReloaded());
    }

    private void sendUsage(Player player, String label) {
        Messages.MINIGAMES.whackUsage(label).forEach(player::sendMessage);
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_WHACK)) return Collections.emptyList();

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
