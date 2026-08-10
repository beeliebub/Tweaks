package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.protection.ui.RegionSelection;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.command.IslandAdminCommand;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.skyblock.template.BukkitTemplateSupport;
import me.beeliebub.tweaks.skyblock.template.TemplateAdminService;
import me.beeliebub.tweaks.utils.GeometryUtil;
import me.beeliebub.tweaks.core.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Template and spawn operations for {@code /isadmin}. */
public final class WorldCommands {
    private final AdminCommandContext context;
    private final TemplateAdminService templates;

    public WorldCommands(AdminCommandContext context) {
        this.context = context;
        this.templates = context.templates;
    }

    public boolean spawn(Player player) {
        if (!context.actorUsable(player)) return false;
        RegionSelection selection = context.runtime.regionSelection(player.getUniqueId());
        if (selection == null || !selection.isComplete() || selection.world() != context.runtime.world()) {
            return context.invalid(player, "select spawn bounds with the region tool first");
        }
        int cx1 = GeometryUtil.chunkX(selection.pos1());
        int cz1 = GeometryUtil.chunkZ(selection.pos1());
        int cx2 = GeometryUtil.chunkX(selection.pos2());
        int cz2 = GeometryUtil.chunkZ(selection.pos2());
        IslandGrid.ChunkBounds bounds = new IslandGrid.ChunkBounds(Math.min(cx1, cx2), Math.min(cz1, cz2),
                Math.max(cx1, cx2), Math.max(cz1, cz2));
        var location = player.getLocation();
        SkyblockSpawn.SpawnData data = new SkyblockSpawn.SpawnData(
                context.runtime.world().getKey().asString(), bounds,
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        var result = context.admin.recordSpawn(data);
        player.sendMessage(result.success() ? Messages.SKYBLOCK.saved("spawn")
                : Messages.SKYBLOCK.invalidInput(result.message()));
        return true;
    }

    public boolean templates(CommandSender sender, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(Messages.SKYBLOCK.adminList("templates",
                    String.join(", ", context.runtime.templateStore().ids())));
            return true;
        }
        if (args[1].equalsIgnoreCase("preview") && args.length == 3) {
            try {
                context.runtime.templateStore().loadAsync(args[2]).whenComplete((template, error) ->
                        context.plugin.getServer().getScheduler().runTask(context.plugin, () -> {
                            if (sender instanceof Player player && !context.actorUsable(player)) return;
                            if (!context.plugin.isEnabled()) return;
                            if (error != null || template == null) {
                                sender.sendMessage(Messages.SKYBLOCK.invalidInput("template not found"));
                            } else {
                                sender.sendMessage(Messages.SKYBLOCK.templatePreview(args[2], template.width(),
                                        template.height(), template.depth(), template.blockEntities().size()));
                            }
                        }));
            } catch (RuntimeException error) {
                sender.sendMessage(Messages.SKYBLOCK.invalidInput("template id"));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("save") && args.length == 3) {
            if (!(sender instanceof Player player)) {
                return context.invalid(sender, "templates save requires an in-game administrator");
            }
            RegionSelection selection = context.runtime.regionSelection(player.getUniqueId());
            if (selection == null || !selection.isComplete() || selection.world() != context.runtime.world()) {
                return context.invalid(sender, "select a template with the region tool first");
            }
            try {
                int minChunkX = Math.min(GeometryUtil.chunkX(selection.pos1()), GeometryUtil.chunkX(selection.pos2()));
                int maxChunkX = Math.max(GeometryUtil.chunkX(selection.pos1()), GeometryUtil.chunkX(selection.pos2()));
                int minChunkZ = Math.min(GeometryUtil.chunkZ(selection.pos1()), GeometryUtil.chunkZ(selection.pos2()));
                int maxChunkZ = Math.max(GeometryUtil.chunkZ(selection.pos1()), GeometryUtil.chunkZ(selection.pos2()));
                IslandGrid.ChunkBounds bounds = new IslandGrid.ChunkBounds(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
                var template = templates.capture(BukkitTemplateSupport.source(context.runtime.world()), bounds,
                        context.runtime.world().getMinHeight(), context.runtime.world().getMaxHeight(), args[2],
                        new Island.SpawnOffset(8, 0, 8));
                templates.save(args[2], template).whenComplete((ignored, error) ->
                        context.plugin.getServer().getScheduler().runTask(context.plugin, () -> {
                            if (!context.actorUsable(player)) return;
                            player.sendMessage(error == null ? Messages.SKYBLOCK.saved("template " + args[2])
                                    : Messages.SKYBLOCK.invalidInput("template save failed"));
                        }));
            } catch (RuntimeException error) {
                context.invalid(sender, error.getMessage() == null ? "template selection" : error.getMessage());
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("delete") && args.length >= 3 && args.length <= 4) {
            long references = context.runtime.typeRegistry().types().stream()
                    .filter(type -> args[2].equalsIgnoreCase(type.templateId())).count();
            if (!context.requireConfirmation(sender, "template-delete", "template " + args[2], references,
                    "Referenced templates cannot be deleted; unreferenced template data will be removed.",
                    AdminArgumentParser.hasTrailingConfirm(args))) return true;
            try {
                templates.deleteCheckedAsync(args[2]).whenComplete((ignored, error) ->
                        context.plugin.getServer().getScheduler().runTask(context.plugin, () -> {
                            if (sender instanceof Player player && !context.actorUsable(player)) return;
                            if (!context.plugin.isEnabled()) return;
                            sender.sendMessage(error == null ? Messages.SKYBLOCK.saved("template deletion")
                                    : Messages.SKYBLOCK.invalidInput(error.getMessage() == null
                                    ? "template deletion failed" : error.getMessage()));
                        }));
            } catch (RuntimeException error) {
                context.invalid(sender, "template id");
            }
            return true;
        }
        return context.invalidUsage(sender, "templates");
    }
}
