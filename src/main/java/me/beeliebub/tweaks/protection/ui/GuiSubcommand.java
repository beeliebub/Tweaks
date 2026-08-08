package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** /region gui [name] — opens the Paper-Dialog management dashboard for a region. */
final class GuiSubcommand implements RegionSubcommand {

    @Override public String name() { return "gui"; }
    @Override public String permission() { return Permissions.PROTECTION_INFO; }
    // The optional world is only valid after the optional region name.
    @Override public int worldArgumentMinArgs() { return 1; }
    @Override public boolean supportsWorldArgument() { return true; }
    @Override public List<RegionUsageEntry> usage() {
        return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_GUI_SYNTAX),
                Messages.PROTECTION.value(Text.USAGE_GUI_DESCRIPTION), Permissions.PROTECTION_INFO));
    }

    @Override
    public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
        gui(ctx, sender, args);
    }

    @Override
    public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
        if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
        return Collections.emptyList();
    }

    // Package-visible (not private) so RegionGUITest can call it directly instead of via
    // reflection on the old private ProtectionCommand.handleGui.
    static void gui(RegionCommandContext ctx, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length == 0) {
                sender.sendMessage(Messages.PROTECTION.text(Text.GUI_CONSOLE_NAME_REQUIRED));
            } else {
                sender.sendMessage(Messages.PROTECTION.text(Text.GUI_ONLY_PLAYERS));
            }
            return;
        }
        if (args.length == 0) {
            List<Region> here = ctx.protection.regionsAt(player.getLocation());
            if (here.isEmpty()) {
                player.sendMessage(Messages.PROTECTION.text(Text.GUI_WILDERNESS));
                return;
            }
            openGuiFor(ctx, player, pickLeaf(here));
            return;
        }
        String name = args[0];
        if (!ctx.requireNamedRegionWorld(sender, args)) return;
        Region region = ctx.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return;
        }
        openGuiFor(ctx, player, region);
    }

    private static void openGuiFor(RegionCommandContext ctx, Player player, Region region) {
        if (!RegionAuth.isOwnerManagerOrAdmin(ctx, player, region)) {
            player.sendMessage(Messages.PROTECTION.text(Text.GUI_OPEN_AUTH, region.id()));
            return;
        }
        RegionGUI.openRegionHub(player, region, ctx.protection, ctx.permissions());
    }

    // From a candidate list of overlapping regions at one chunk, return the leaf — a region
    // whose id is not the parentId of any other region in the list.
    private static Region pickLeaf(List<Region> candidates) {
        if (candidates.size() == 1) return candidates.getFirst();
        Set<String> parentIds = new HashSet<>();
        for (Region r : candidates) {
            if (r.hasParent()) parentIds.add(r.parentId());
        }
        for (Region r : candidates) {
            if (!parentIds.contains(r.id())) return r;
        }
        return candidates.getFirst();
    }
}
