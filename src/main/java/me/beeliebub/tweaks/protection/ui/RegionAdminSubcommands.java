package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import me.beeliebub.tweaks.utils.SafeGroundLocator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Administrative region handlers that do not belong to single-region editing. */
final class RegionAdminSubcommands {

    private static final int PAGE_SIZE = 8;

    private RegionAdminSubcommands() {}

    static final class ToggleBypass implements RegionSubcommand {
        @Override public String name() { return "togglebypass"; }
        @Override public String permission() { return Permissions.PROTECTION_BYPASS; }

        @Override
        public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(
                    Messages.PROTECTION.value(Text.USAGE_TOGGLE_BYPASS_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_TOGGLE_BYPASS_DESCRIPTION),
                    Permissions.PROTECTION_BYPASS));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.BYPASS_ONLY_PLAYERS));
                return;
            }
            boolean enabled = ctx.protection.toggleProtectionBypass(player.getUniqueId());
            sender.sendMessage(Messages.PROTECTION.text(
                    enabled ? Text.BYPASS_ENABLED : Text.BYPASS_DISABLED));
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            return Collections.emptyList();
        }
    }

    static final class ListRegions implements RegionSubcommand {
        @Override public String name() { return "list"; }
        @Override public String permission() { return Permissions.PROTECTION_ADMIN; }

        @Override
        public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(
                    Messages.PROTECTION.value(Text.USAGE_LIST_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_LIST_DESCRIPTION),
                    Permissions.PROTECTION_ADMIN));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length > 2) {
                ctx.showUsage(sender, this);
                return;
            }
            String filter = null;
            int page = 1;
            boolean numericSingle = args.length == 1 && isInteger(args[0]);
            if (args.length == 1) {
                if (numericSingle) page = parsePage(args[0]);
                else filter = args[0];
            } else if (args.length == 2) {
                filter = args[0];
                if (!isInteger(args[1])) {
                    sender.sendMessage(Messages.invalidNumber());
                    return;
                }
                page = parsePage(args[1]);
            }
            final int selectedPage = page;

            if (filter == null) {
                render(ctx, sender, null, null, selectedPage, numericSingle);
                return;
            }

            String filterName = filter;
            CompletableFuture<OfflinePlayer> future = OfflinePlayerResolver.resolve(
                    (Tweaks) ctx.plugin, sender, filterName);
            boolean asynchronous = !future.isDone();
            future.whenComplete((target, error) -> {
                Runnable continuation = () -> {
                    if (asynchronous && !OfflinePlayerResolver.isSenderOnline(sender)) return;
                    if (error != null) {
                        if (OfflinePlayerResolver.isLookupInProgress(error)) {
                            sender.sendMessage(Messages.COMMANDS.offlinePlayerLookupBusy());
                        } else {
                            ctx.plugin.getLogger().log(Level.WARNING,
                                    "Region list player lookup failed for " + filterName, error);
                            sender.sendMessage(Messages.playerNotOnline(filterName));
                        }
                        return;
                    }
                    if (target == null) {
                        sender.sendMessage(Messages.playerNotOnline(filterName));
                        return;
                    }
                    render(ctx, sender, target.getUniqueId(), filterName, selectedPage, false);
                };
                scheduleContinuation(ctx.plugin, future, asynchronous, continuation);
            });
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) {
                return RegionCommandContext.filterByPrefix(
                        RegionCommandContext.onlinePlayerNames(""), rawArgs[1]);
            }
            return Collections.emptyList();
        }
    }

    static final class Tp implements RegionSubcommand {
        @Override public String name() { return "tp"; }
        @Override public String permission() { return Permissions.PROTECTION_ADMIN; }
        @Override public int minArgs() { return 1; }
        @Override public boolean supportsWorldArgument() { return true; }
        @Override public int worldArgumentMinArgs() { return 1; }

        @Override
        public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(
                    Messages.PROTECTION.value(Text.USAGE_TP_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_TP_DESCRIPTION),
                    Permissions.PROTECTION_ADMIN));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.TP_ONLY_PLAYERS));
                return;
            }
            if (args.length != 1) {
                ctx.showUsage(sender, this);
                return;
            }
            String name = args[0];
            Region region = ctx.resolveRegion(sender, name);
            if (region == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
                return;
            }
            if (region.bounds() == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.TP_NO_BOUNDS, name));
                return;
            }
            if (region.worldName() == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.TP_NO_WORLD, name));
                return;
            }
            World world = Bukkit.getWorld(region.worldName());
            if (world == null) {
                sender.sendMessage(Messages.PROTECTION.text(
                        Text.TP_WORLD_NOT_LOADED, region.worldName(), name));
                return;
            }

            double centerX = centerBlockX(region.bounds());
            double centerZ = centerBlockZ(region.bounds());
            sender.sendMessage(Messages.PROTECTION.text(Text.TP_TELEPORTING, name));
            SafeGroundLocator.findSafeCenter(world, centerX, centerZ,
                            player.getYaw(), player.getPitch())
                    .whenComplete((result, error) -> {
                        scheduleOnMain(ctx.plugin, () -> {
                            if (!player.isOnline()) return;
                            if (error != null || result == null) {
                                ctx.plugin.getLogger().log(Level.WARNING,
                                        "Failed to locate region teleport destination for " + name, error);
                                sender.sendMessage(Messages.PROTECTION.text(Text.TP_FAILED, name));
                                return;
                            }
                            if (!result.groundFound()) {
                                sender.sendMessage(Messages.PROTECTION.text(Text.TP_NO_GROUND, name));
                            }
                            try {
                                player.teleportAsync(result.location()).whenComplete((success, teleportError) ->
                                        scheduleOnMain(ctx.plugin, () -> {
                                            if (!player.isOnline()) return;
                                            if (teleportError != null || !Boolean.TRUE.equals(success)) {
                                                if (teleportError != null) {
                                                    ctx.plugin.getLogger().log(Level.WARNING,
                                                            "Region teleport failed for " + name, teleportError);
                                                }
                                                sender.sendMessage(Messages.PROTECTION.text(Text.TP_FAILED, name));
                                                return;
                                            }
                                            sender.sendMessage(Messages.PROTECTION.text(Text.TP_SUCCESS, name));
                                        }));
                            } catch (RuntimeException teleportError) {
                                ctx.plugin.getLogger().log(Level.WARNING,
                                        "Region teleport failed for " + name, teleportError);
                                sender.sendMessage(Messages.PROTECTION.text(Text.TP_FAILED, name));
                            }
                        });
                    });
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            return Collections.emptyList();
        }
    }

    static double centerBlockX(Region.RegionBounds bounds) {
        return 8.0d * ((long) bounds.minChunkX() + bounds.maxChunkX() + 1L);
    }

    static double centerBlockZ(Region.RegionBounds bounds) {
        return 8.0d * ((long) bounds.minChunkZ() + bounds.maxChunkZ() + 1L);
    }

    private static void render(RegionCommandContext ctx, CommandSender sender, java.util.UUID owner,
                               String filter, int requestedPage, boolean numericSingle) {
        List<Region> regions = new ArrayList<>();
        for (Region region : ctx.protection.regions().values()) {
            if (ProtectionManager.isGlobal(region)) continue;
            if (owner != null && !owner.equals(region.owner())) continue;
            regions.add(region);
        }
        regions.sort(Comparator.comparing(Region::id, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (regions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        sender.sendMessage(Messages.PROTECTION.regionListHeader(page, pages));
        if (regions.isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_LIST_EMPTY));
            if (numericSingle) sender.sendMessage(Messages.PROTECTION.text(Text.REGION_LIST_NUMERIC_HINT));
            return;
        }

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, regions.size());
        for (Region region : regions.subList(from, to)) {
            sender.sendMessage(row(region));
        }
        Component previous = page > 1
                ? Messages.PROTECTION.regionListPageButton(Text.REGION_LIST_PREVIOUS, page - 1,
                listCommand(filter, page - 1)) : null;
        Component next = page < pages
                ? Messages.PROTECTION.regionListPageButton(Text.REGION_LIST_NEXT, page + 1,
                listCommand(filter, page + 1)) : null;
        if (previous != null || next != null) {
            sender.sendMessage(Messages.PROTECTION.regionListNavigation(previous, next));
        }
        if (numericSingle && requestedPage != page) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_LIST_NUMERIC_HINT));
        }
    }

    private static Component row(Region region) {
        String worldName = region.worldName();
        String world = worldName == null
                ? Messages.PROTECTION.value(Text.REGION_LIST_NO_WORLD)
                : Bukkit.getWorld(worldName) == null
                ? Messages.PROTECTION.value(Text.REGION_LIST_NOT_LOADED, worldName) : worldName;
        String coordinates;
        String exactCoordinates;
        if (region.bounds() == null) {
            coordinates = Messages.PROTECTION.value(Text.REGION_LIST_NO_BOUNDS);
            exactCoordinates = coordinates;
        } else {
            long x = Math.round(centerBlockX(region.bounds()));
            long z = Math.round(centerBlockZ(region.bounds()));
            coordinates = x + ", " + z;
            exactCoordinates = Messages.PROTECTION.value(Text.REGION_LIST_CENTRE, x, z);
        }
        return Messages.PROTECTION.regionListRow(region.id(), world, coordinates,
                exactCoordinates, RegionInfoRenderer.resolveName(region.owner()));
    }

    private static String listCommand(String filter, int page) {
        return filter == null ? "/region list " + page : "/region list " + filter + " " + page;
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static void scheduleContinuation(Tweaks plugin, CompletableFuture<?> future,
                                             boolean asynchronous, Runnable continuation) {
        if (!asynchronous || future == null || !plugin.isEnabled()) {
            continuation.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, continuation);
    }

    private static void scheduleOnMain(Tweaks plugin, Runnable continuation) {
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    continuation.run();
                } catch (RuntimeException error) {
                    plugin.getLogger().log(Level.WARNING,
                            "Region teleport completion failed unexpectedly", error);
                }
            });
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not schedule region teleport completion", error);
        }
    }
}
