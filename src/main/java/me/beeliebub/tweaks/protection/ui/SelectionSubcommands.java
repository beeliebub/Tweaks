package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.utils.GeometryUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/** The three wand-selection subcommands: {@code clear}, {@code wand}, {@code select}. */
final class SelectionSubcommands {

    private SelectionSubcommands() {}

    static final class Clear implements RegionSubcommand {
        @Override public String name() { return "clear"; }
        @Override public String permission() { return null; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_CLEAR_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_CLEAR_DESCRIPTION), null));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.CLEAR_ONLY_PLAYERS));
                return;
            }
            if (ctx.selections.get(player.getUniqueId()) == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.CLEAR_NONE));
                return;
            }
            ctx.selections.clear(player.getUniqueId());
            sender.sendMessage(Messages.PROTECTION.text(Text.CLEAR_SUCCESS));
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            return Collections.emptyList();
        }
    }

    static final class Wand implements RegionSubcommand {
        @Override public String name() { return "wand"; }
        @Override public String permission() { return null; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_WAND_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_WAND_DESCRIPTION), null));
        }

        // /region wand — hands the player the configured selection-tool material
        // (config: protection.selection-tool) with a labelled display name.
        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.WAND_ONLY_PLAYERS));
                return;
            }
            Material tool = ctx.plugin.getProtectionSelectionTool();
            ItemStack wand = new ItemStack(tool);
            wand.editMeta(meta -> meta.displayName(Messages.PROTECTION.text(Text.WAND_NAME)
                    .decoration(TextDecoration.ITALIC, false)));
            var overflow = player.getInventory().addItem(wand);
            if (!overflow.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), wand);
                sender.sendMessage(Messages.PROTECTION.text(Text.WAND_FULL));
            } else {
                sender.sendMessage(Messages.PROTECTION.text(Text.WAND_SUCCESS));
            }
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            return Collections.emptyList();
        }
    }

    static final class Select implements RegionSubcommand {
        @Override public String name() { return "select"; }
        @Override public String permission() { return Permissions.PROTECTION_INFO; }
        @Override public int minArgs() { return 1; }
        @Override public boolean supportsWorldArgument() { return true; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_SELECT_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_SELECT_DESCRIPTION), Permissions.PROTECTION_INFO));
        }

        // /region select <name> — restore a region's pos1/pos2 onto the calling
        // player's wand selection.
        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 1) {
                ctx.showUsage(sender, this);
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.SELECT_ONLY_PLAYERS));
                return;
            }
            String name = args[0];
            if (!ctx.requireNamedRegionWorld(sender, args)) return;
            Region region = ctx.resolveRegion(sender, name);
            if (region == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
                return;
            }
            boolean isOwner = region.isOwner(player.getUniqueId());
            if (!isOwner && !RegionAuth.isAdmin(ctx, player)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.SELECT_AUTH, name));
                return;
            }
            Region.RegionBounds bounds = region.bounds();
            if (bounds == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.SELECT_NO_BOUNDS, name));
                return;
            }

            var sel = ctx.selections.getOrCreate(player, ctx.scopeWorld(player));
            sel.setPos1(GeometryUtil.chunkKey(bounds.minChunkX(), bounds.minChunkZ()));
            sel.setPos2(GeometryUtil.chunkKey(bounds.maxChunkX(), bounds.maxChunkZ()));

            int chunks = (bounds.maxChunkX() - bounds.minChunkX() + 1)
                    * (bounds.maxChunkZ() - bounds.minChunkZ() + 1);
            sender.sendMessage(Messages.PROTECTION.text(Text.SELECT_SUCCESS,
                    name, chunks, chunks == 1 ? "" : "s"));
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            return Collections.emptyList();
        }
    }
}
