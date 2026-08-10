package me.beeliebub.tweaks.skyblock.command;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandHomes;
import me.beeliebub.tweaks.skyblock.island.IslandInviteManager;
import me.beeliebub.tweaks.skyblock.island.IslandBiomeService;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandRegionBridge;
import me.beeliebub.tweaks.skyblock.island.IslandVisits;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.economy.ShopService;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.ui.ChallengeGUI;
import me.beeliebub.tweaks.skyblock.ui.IslandGUI;
import me.beeliebub.tweaks.skyblock.ui.ShopGUI;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Island lifecycle command router. */
public final class IslandCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final SkyblockBootstrap.Runtime runtime;
    private final IslandManager islands;
    private final IslandRegionBridge regions;
    private final IslandVisits visits;
    private final IslandHomes homes;
    private final IslandBiomeService biomeService;
    private final me.beeliebub.tweaks.skyblock.island.IslandSinks sinks;
    private final Map<UUID, Long> deleteConfirmations = new ConcurrentHashMap<>();

    public IslandCommand(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.islands = runtime.islandManager();
        this.regions = runtime.regionBridge();
        this.visits = runtime.visits();
        this.homes = runtime.homes();
        this.biomeService = runtime.biomeService() != null ? runtime.biomeService()
                : runtime.economy() == null ? null : new IslandBiomeService(plugin, islands,
                runtime.economy(), runtime.config(), runtime.world(), 2);
        this.sinks = runtime.economy() == null ? null : new me.beeliebub.tweaks.skyblock.island.IslandSinks(
                homes, runtime.economy(), runtime.config());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.SKYBLOCK.invalidInput("player-only command"));
            return true;
        }
        if (!player.hasPermission(Permissions.SKYBLOCK_USE)) {
            player.sendMessage(Messages.SKYBLOCK.noPermission());
            return true;
        }
        if (player.getWorld() != runtime.world()) {
            player.sendMessage(Messages.SKYBLOCK.wrongWorld(runtime.config().worldKey()));
            return true;
        }
        String subcommand = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "create" -> create(player, args);
            case "home" -> home(player, args);
            case "sethome" -> setHome(player, args);
            case "info" -> info(player, args);
            case "delete" -> delete(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "kick" -> kick(player, args);
            case "leave" -> leave(player);
            case "settings" -> settings(player, args);
            case "visit" -> visit(player, args);
            case "challenges" -> challenges(player, args);
            case "shop" -> shop(player, args);
            case "biome" -> biome(player, args);
            case "homes" -> homes(player, args);
            default -> usage(player, label);
        };
    }

    private boolean create(Player player, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("gui")) {
            if (runtime.typeRegistry() == null) return invalid(player, "island creation is unavailable");
            new IslandGUI(runtime.typeRegistry(), runtime.creationService()).open(player);
            return true;
        }
        if (args.length != 1 && args.length != 3) return invalid(player, creationUsage());
        String typeId = runtime.config().defaultType().toLowerCase(Locale.ROOT);
        String difficultyId = runtime.config().defaultDifficulty().toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            typeId = args[1].toLowerCase(Locale.ROOT);
            difficultyId = args[2].toLowerCase(Locale.ROOT);
        }
        IslandType type = runtime.typeRegistry() == null ? null : runtime.typeRegistry().type(typeId).orElse(null);
        boolean difficultyExists = runtime.typeRegistry() != null
                && runtime.typeRegistry().difficulty(difficultyId).isPresent();
        if (type == null || !difficultyExists || !type.allowsDifficulty(difficultyId)) {
            if (args.length == 1) {
                player.sendMessage(Messages.SKYBLOCK.invalidConfiguredDefaults(typeId, difficultyId));
                return true;
            }
            return invalid(player, "unknown type or difficulty; " + creationUsage());
        }
        if (runtime.creationService() != null) {
            var result = runtime.creationService().begin(player, typeId, difficultyId, completed -> {
                if (!plugin.isEnabled() || !player.isOnline() || !player.hasPermission(Permissions.SKYBLOCK_USE)
                        || player.getWorld() != runtime.world()) return;
                if (completed.status() == me.beeliebub.tweaks.skyblock.island.IslandCreationService.Status.COMPLETED) {
                    player.sendMessage(Messages.SKYBLOCK.created(completed.island().displayName()));
                } else if (completed.status() == me.beeliebub.tweaks.skyblock.island.IslandCreationService.Status.FAILED) {
                    player.sendMessage(Messages.SKYBLOCK.invalidInput(completed.reason()));
                }
            });
            if (result.status() == me.beeliebub.tweaks.skyblock.island.IslandCreationService.Status.FAILED) {
                player.sendMessage(Messages.SKYBLOCK.invalidInput(result.reason()));
            }
            return true;
        }
        Optional<Island> created = islands.createIsland(player.getUniqueId());
        if (created.isEmpty()) {
            player.sendMessage(Messages.SKYBLOCK.createBlocked());
            return true;
        }
        Island island = created.get();
        island = islands.update(island.withTypeId(typeId).withDifficultyId(difficultyId));
        if (regions.create(island, runtime.world(), new java.util.concurrent.atomic.AtomicReference<>())
                != me.beeliebub.tweaks.protection.region.ProtectionManager.ClaimResult.OK) {
            islands.remove(island.id());
            player.sendMessage(Messages.SKYBLOCK.invalidInput("the island location is occupied"));
            return true;
        }
        player.teleport(islands.spawnLocation(island, runtime.world()));
        player.sendMessage(Messages.SKYBLOCK.created(island.displayName()));
        return true;
    }

    private boolean home(Player player, String[] args) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (args.length == 1) {
            player.teleport(islands.spawnLocation(island, runtime.world()));
            visits.clear(player.getUniqueId());
            return true;
        }
        if (args.length != 2) return invalid(player, "home [name]");
        homes.get(player.getUniqueId(), args[1], this::worldByKey)
                .ifPresentOrElse(player::teleport,
                        () -> player.sendMessage(Messages.SKYBLOCK.islandNotFound(args[1])));
        visits.clear(player.getUniqueId());
        return true;
    }

    private boolean setHome(Player player, String[] args) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (args.length != 2 || args[1].isBlank()) return invalid(player, "sethome <name>");
        if (islands.islandAt(player.getLocation()).map(Island::id).filter(island.id()::equals).isEmpty()) {
            player.sendMessage(Messages.SKYBLOCK.containment());
            return true;
        }
        if (!homes.set(player.getUniqueId(), args[1], player.getLocation())) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("home slots are full"));
        } else {
            player.sendMessage(Messages.SKYBLOCK.saved("home"));
        }
        return true;
    }

    private boolean info(Player player, String[] args) {
        if (args.length > 2) return invalid(player, "info [island]");
        Island island = args.length == 2 ? findIsland(args[1]) : ownIsland(player);
        if (island == null) {
            player.sendMessage(Messages.SKYBLOCK.islandNotFound(args.length == 2 ? args[1] : ""));
            return true;
        }
        String owner = Optional.ofNullable(Bukkit.getOfflinePlayer(island.owner()).getName())
                .orElse(island.owner().toString());
        player.sendMessage(Messages.SKYBLOCK.islandInfo(island.id(), owner, island.memberCount()));
        return true;
    }

    private boolean delete(Player player, String[] args) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (!island.owner().equals(player.getUniqueId())) {
            player.sendMessage(Messages.SKYBLOCK.noPermission());
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("confirm")) {
            deleteConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + 60_000L);
            player.sendMessage(Messages.SKYBLOCK.deleteConfirmation());
            return true;
        }
        Long expires = deleteConfirmations.remove(player.getUniqueId());
        if (expires == null || expires < System.currentTimeMillis()) {
            player.sendMessage(Messages.SKYBLOCK.deleteConfirmation());
            return true;
        }
        me.beeliebub.tweaks.skyblock.island.IslandDeletionService.BeginResult start = runtime.beginDeletion(island);
        if (start != me.beeliebub.tweaks.skyblock.island.IslandDeletionService.BeginResult.STARTED) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput(start.name().toLowerCase(Locale.ROOT)));
            return true;
        }
        player.sendMessage(Messages.SKYBLOCK.deleteStarted());
        return true;
    }

    private boolean invite(Player player, String[] args) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (!island.owner().equals(player.getUniqueId())) {
            player.sendMessage(Messages.SKYBLOCK.noPermission());
            return true;
        }
        if (args.length != 2) return invalid(player, "invite <player>");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Messages.SKYBLOCK.islandNotFound(args[1]));
            return true;
        }
        runtime.invites().invite(player.getUniqueId(), island.id(), target.getUniqueId());
        player.sendMessage(Messages.SKYBLOCK.inviteSent(target.getName()));
        target.sendMessage(Messages.SKYBLOCK.inviteReceived(player.getName()));
        return true;
    }

    private boolean accept(Player player) {
        Optional<IslandInviteManager.Invite> pending = runtime.invites().take(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(Messages.SKYBLOCK.inviteExpired());
            return true;
        }
        IslandInviteManager.Invite invite = pending.get();
        if (!islands.addMember(invite.islandId(), player.getUniqueId())) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("the island is full or you already belong to an island"));
            return true;
        }
        Island joined = islands.byId(invite.islandId()).orElse(null);
        if (joined == null || !runtime.regionBridge().addMember(joined, runtime.world(), player.getUniqueId())) {
            if (joined != null) {
                Island restored = joined.withMembers(joined.members().stream()
                        .filter(member -> !member.equals(player.getUniqueId())).collect(java.util.stream.Collectors.toSet()));
                islands.update(restored);
            }
            player.sendMessage(Messages.SKYBLOCK.invalidInput("the island protection could not be updated"));
            return true;
        }
        player.sendMessage(Messages.SKYBLOCK.memberChanged("You joined the Skyblock island."));
        return true;
    }

    private boolean kick(Player player, String[] args) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (!island.owner().equals(player.getUniqueId())) {
            player.sendMessage(Messages.SKYBLOCK.noPermission());
            return true;
        }
        if (args.length != 2) return invalid(player, "kick <player>");
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetId = target == null ? null : target.getUniqueId();
        if (targetId == null || !island.members().contains(targetId)
                || !removeMemberWithProtection(island, targetId)) {
            player.sendMessage(Messages.SKYBLOCK.islandNotFound(args[1]));
            return true;
        }
        visits.clear(targetId);
        wipe(targetId);
        if (target != null) target.teleport(runtime.spawn().location(runtime.world()));
        player.sendMessage(Messages.SKYBLOCK.memberChanged("Player removed from the island."));
        return true;
    }

    private boolean leave(Player player) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (island.owner().equals(player.getUniqueId())) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("the owner must delete the island"));
            return true;
        }
        if (!removeMemberWithProtection(island, player.getUniqueId())) return true;
        visits.clear(player.getUniqueId());
        wipe(player.getUniqueId());
        player.teleport(runtime.spawn().location(runtime.world()));
        player.sendMessage(Messages.SKYBLOCK.wipeComplete());
        return true;
    }

    private boolean settings(Player player, String[] args) {
        Island island = ownIsland(player);
        if (island == null) return true;
        if (!island.owner().equals(player.getUniqueId())) {
            player.sendMessage(Messages.SKYBLOCK.noPermission());
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("public")) return invalid(player, "settings public");
        boolean publicNow = !island.isPublic();
        islands.update(island.withPublic(publicNow));
        if (!publicNow) {
            runtime.visits().clearIsland(island.id()).forEach(visitor -> {
                Player online = Bukkit.getPlayer(visitor);
                if (online != null) online.teleport(runtime.spawn().location(runtime.world()));
            });
        }
        player.sendMessage(Messages.SKYBLOCK.saved("island visibility"));
        return true;
    }

    private boolean visit(Player player, String[] args) {
        if (args.length != 2) return invalid(player, "visit <island>");
        Island target = findIsland(args[1]);
        if (target == null) {
            player.sendMessage(Messages.SKYBLOCK.islandNotFound(args[1]));
            return true;
        }
        if (!target.isPublic()) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("that island is private"));
            return true;
        }
        if (islands.forPlayer(player.getUniqueId()).map(Island::id).filter(target.id()::equals).isPresent()) {
            return home(player, new String[]{"home"});
        }
        visits.visit(player.getUniqueId(), target.id());
        player.teleport(islands.spawnLocation(target, runtime.world()));
        return true;
    }

    private boolean challenges(Player player, String[] args) {
        ChallengeService service = runtime.challengeService();
        if (service == null || runtime.challengeRegistry() == null) return invalid(player, "challenges unavailable");
        if (args.length >= 2 && args[1].equalsIgnoreCase("claim")) {
            if (args.length != 3) return invalid(player, "challenges claim <id>");
            Island island = ownIsland(player);
            if (island == null) return true;
            ChallengeService.ClaimResult result = service.claim(island, args[2], player);
            player.sendMessage(result.claimed() ? Messages.SKYBLOCK.challengeClaimed(args[2])
                    : Messages.SKYBLOCK.challengeLocked(result.reason()));
            return true;
        }
        Island island = ownIsland(player);
        if (island == null) return true;
        if (args.length == 1 || args.length == 2 && args[1].equalsIgnoreCase("gui")) {
            new ChallengeGUI(runtime.challengeRegistry(), service).open(player, island);
            return true;
        }
        if (args.length > 2) return invalid(player, "challenges [gui|list|claim <id>]");
        for (Challenge challenge : runtime.challengeRegistry().challenges()) {
            ChallengeService.Readiness readiness = service.readiness(island, challenge.id(), player);
            player.sendMessage(readiness.ready() ? Messages.SKYBLOCK.challengeReady(challenge.id())
                    : Messages.SKYBLOCK.challengeLocked(challenge.id() + ": " + readiness.reason()));
        }
        return true;
    }

    private boolean shop(Player player, String[] args) {
        if (args.length == 1 && runtime.shopService() != null) {
            new ShopGUI(runtime.shopCatalog(), runtime.shopService()).open(player);
            return true;
        }
        if (runtime.shopService() == null || (args.length != 3 && args.length != 4)
                || !args[1].equalsIgnoreCase("buy")) {
            return invalid(player, "shop buy <material> [amount]");
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null) return invalid(player, args[2]);
        int amount;
        try { amount = Integer.parseInt(args.length > 3 ? args[3] : "1"); }
        catch (NumberFormatException error) { return invalid(player, "amount"); }
        ShopService.BuyResult result = runtime.shopService().buy(player, material, amount);
        player.sendMessage(result.success() ? Messages.SKYBLOCK.purchased(material.name(), amount, result.price())
                : Messages.SKYBLOCK.invalidInput(result.message()));
        return true;
    }

    private boolean biome(Player player, String[] args) {
        if (args.length != 2 || biomeService == null) return invalid(player, "biome <name>");
        Biome biome;
        try {
            biome = Biome.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return invalid(player, args[1]);
        }
        var result = biomeService.begin(player, biome, completed -> {
            if (!plugin.isEnabled() || !player.isOnline() || !player.hasPermission(Permissions.SKYBLOCK_USE)
                    || player.getWorld() != runtime.world()) return;
            player.sendMessage(completed.status() == IslandBiomeService.Status.COMPLETED
                    ? Messages.SKYBLOCK.saved("island biome")
                    : Messages.SKYBLOCK.invalidInput(completed.reason()));
        });
        if (result.status() == IslandBiomeService.Status.FAILED) player.sendMessage(Messages.SKYBLOCK.invalidInput(result.reason()));
        return true;
    }

    private boolean homes(Player player, String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("buy") || sinks == null) {
            return invalid(player, "homes buy");
        }
        Island island = ownIsland(player);
        if (island == null) return true;
        var result = sinks.purchaseHomeSlot(island);
        player.sendMessage(result.applied() ? Messages.SKYBLOCK.saved("home slot")
                : Messages.SKYBLOCK.invalidInput(result.reason()));
        return true;
    }

    private Island ownIsland(Player player) {
        Island island = islands.forPlayer(player.getUniqueId()).orElse(null);
        if (island == null) player.sendMessage(Messages.SKYBLOCK.islandRequired());
        return island;
    }

    private Island findIsland(String value) {
        Island island = islands.byId(value).orElse(null);
        if (island == null) {
            Player player = Bukkit.getPlayerExact(value);
            if (player != null) island = islands.forPlayer(player.getUniqueId()).orElse(null);
        }
        if (island == null && value != null && !value.isBlank()) {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(value);
            island = islands.forPlayer(offline.getUniqueId()).orElse(null);
        }
        if (island == null) {
            // Owner UUID input is useful for console-assisted testing and is unambiguous.
            try {
                UUID owner = UUID.fromString(value);
                island = islands.forPlayer(owner).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // Handled by the player-facing not-found message below.
            }
        }
        if (island == null) {
            // Callers send the final not-found message with the original token.
            return null;
        }
        return island;
    }

    private void wipe(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            online.getInventory().clear();
            online.getEnderChest().clear();
            online.getOpenInventory().setCursor(new org.bukkit.inventory.ItemStack(Material.AIR));
        }
        runtime.pendingWipeStore().markPending(player);
        runtime.wipeNow(player);
    }

    private boolean removeMemberWithProtection(Island island, UUID member) {
        Island before = islands.byId(island.id()).orElse(null);
        if (before == null) return false;
        homes.clear(member);
        if (!islands.removeMember(island.id(), member)) return false;
        Island after = islands.byId(island.id()).orElse(null);
        if (after != null && runtime.regionBridge().removeMember(after, runtime.world(), member)) return true;
        islands.update(before);
        return false;
    }

    private World worldByKey(String key) {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getKey().asString().equalsIgnoreCase(key)) return world;
        }
        return null;
    }

    private boolean invalid(Player player, String usage) {
        player.sendMessage(Messages.SKYBLOCK.invalidInput(usage));
        return true;
    }

    private String creationUsage() {
        List<String> types = runtime.typeRegistry() == null ? List.of()
                : runtime.typeRegistry().types().stream().map(IslandType::id).toList();
        List<String> difficulties = runtime.typeRegistry() == null ? List.of()
                : runtime.typeRegistry().difficulties().stream().map(value -> value.id()).toList();
        return "create <type> <difficulty> or create gui; types: "
                + (types.isEmpty() ? "(none)" : String.join(", ", types))
                + "; difficulties: "
                + (difficulties.isEmpty() ? "(none)" : String.join(", ", difficulties));
    }

    private boolean usage(Player player, String label) {
        player.sendMessage(Messages.SKYBLOCK.invalidInput("/" + label
                + " create [type difficulty|gui]|home [name]|sethome <name>|homes buy|biome <name>|info [island]"
                + "|delete [confirm]|invite <player>|accept|kick <player>|leave|settings public|visit <island>"
                + "|challenges [gui|list|claim <id>]|shop [buy <material> [amount]]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> values = List.of("create", "home", "homes", "biome", "sethome", "info", "delete", "invite",
                "accept", "kick", "leave", "settings", "visit", "challenges", "shop");
        if (args.length == 1) {
            return prefix(values, args[0]);
        }
        if (!(sender instanceof Player player) || !player.hasPermission(Permissions.SKYBLOCK_USE)
                || player.getWorld() != runtime.world()) return List.of();
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (subcommand) {
                case "create" -> prefix(runtime.typeRegistry() == null ? List.of("gui")
                        : java.util.stream.Stream.concat(Stream.of("gui"),
                        runtime.typeRegistry().types().stream().map(IslandType::id)).toList(), args[1]);
                case "home" -> prefix(homeNames(player), args[1]);
                case "invite" -> prefix(plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName).toList(), args[1]);
                case "kick" -> prefix(memberNames(player), args[1]);
                case "visit" -> prefix(publicIslandNames(), args[1]);
                case "info" -> prefix(islandNames(), args[1]);
                case "challenges" -> prefix(List.of("gui", "list", "claim"), args[1]);
                case "shop" -> prefix(List.of("buy"), args[1]);
                case "biome" -> prefix(Arrays.stream(Biome.values()).map(Biome::name).toList(), args[1]);
                case "settings" -> prefix(List.of("public"), args[1]);
                case "delete" -> prefix(List.of("confirm"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (subcommand) {
                case "create" -> {
                    IslandType type = runtime.typeRegistry() == null ? null
                            : runtime.typeRegistry().type(args[1]).orElse(null);
                    yield type == null ? List.of() : prefix(type.difficultyIds(), args[2]);
                }
                case "challenges" -> args[1].equalsIgnoreCase("claim")
                        ? prefix(runtime.challengeRegistry().challenges().stream().map(Challenge::id).toList(), args[2])
                        : List.of();
                case "shop" -> args[1].equalsIgnoreCase("buy")
                        ? prefix(runtime.shopCatalog().entries().stream().filter(entry -> entry.buyable())
                        .map(entry -> entry.material().name()).toList(), args[2])
                        : List.of();
                default -> List.of();
            };
        }
        if (args.length == 4 && subcommand.equals("shop") && args[1].equalsIgnoreCase("buy")) {
            return List.of("1", "16", "64").stream().filter(value -> value.startsWith(args[3])).toList();
        }
        return List.of();
    }

    private List<String> homeNames(Player player) {
        return islands.forPlayer(player.getUniqueId())
                .<List<String>>map(island -> new ArrayList<>(island.homes()
                        .getOrDefault(player.getUniqueId(), Map.of()).keySet()))
                .orElse(List.of());
    }

    private List<String> memberNames(Player player) {
        return islands.forPlayer(player.getUniqueId()).stream().flatMap(island -> island.memberSet().stream())
                .map(Bukkit::getPlayer).filter(value -> value != null).map(Player::getName).toList();
    }

    private List<String> publicIslandNames() {
        List<String> result = new ArrayList<>();
        for (Island island : islands.all()) {
            if (!island.isPublic()) continue;
            result.add(island.id());
            String owner = Bukkit.getOfflinePlayer(island.owner()).getName();
            if (owner != null) result.add(owner);
        }
        return result;
    }

    private List<String> islandNames() {
        List<String> result = new ArrayList<>();
        for (Island island : islands.all()) {
            result.add(island.id());
            String owner = Bukkit.getOfflinePlayer(island.owner()).getName();
            if (owner != null) result.add(owner);
        }
        return result;
    }

    private static List<String> prefix(Iterable<String> values, String rawPrefix) {
        String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value);
        return List.copyOf(result);
    }
}
