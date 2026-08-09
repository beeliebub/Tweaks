package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ordered registry of every {@link RegionSubcommand}, replacing the old
 * {@code SUBCOMMANDS}/{@code USAGE_ENTRIES} table pair. Construction order IS usage order —
 * {@code /region} prints usage lines in the order handlers are registered here, matching the
 * original 16-entry {@code USAGE_ENTRIES} declaration order exactly (confirmed against the git
 * history predating this registry class; pinned by {@code RegionSubcommandRegistryTest}):
 * {@code claim, clear, wand, select, unclaim, addmember, removemember, addmanager, removemanager,
 * flag, unflag, flags, info, setparent, unsetparent, gui}.
 */
final class RegionSubcommandRegistry {

    private final List<RegionSubcommand> ordered = new ArrayList<>();
    private final Map<String, RegionSubcommand> byName = new LinkedHashMap<>();

    RegionSubcommandRegistry() {
        register(new ClaimSubcommand());
        register(new SelectionSubcommands.Clear());
        register(new SelectionSubcommands.Wand());
        register(new SelectionSubcommands.Select());
        register(new UnclaimSubcommand());
        register(new MembershipSubcommands.Member(true));
        register(new MembershipSubcommands.Member(false));
        register(new MembershipSubcommands.Manager(true));
        register(new MembershipSubcommands.Manager(false));
        register(new FlagSubcommands.Flag());
        register(new FlagSubcommands.Unflag());
        register(new InfoSubcommands.Flags());
        register(new InfoSubcommands.Info());
        register(new ParentSubcommands.SetParent());
        register(new ParentSubcommands.UnsetParent());
        register(new GuiSubcommand());
    }

    private void register(RegionSubcommand handler) {
        ordered.add(handler);
        byName.put(handler.name().toLowerCase(Locale.ROOT), handler);
        for (String alias : handler.aliases()) {
            byName.put(alias.toLowerCase(Locale.ROOT), handler);
        }
    }

    RegionSubcommand find(String name) {
        if (name == null) return null;
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    void showRootUsage(RegionCommandContext ctx, CommandSender sender) {
        sender.sendMessage(Messages.PROTECTION.text(Text.REGION_COMMANDS_HEADER));
        int shown = 0;
        for (RegionSubcommand handler : ordered) {
            if (!handler.visibleInUsage()) continue;
            if (!handler.visibleTo(ctx, sender)) continue;
            for (RegionUsageEntry entry : handler.usage()) {
                if (entry.permission() != null && !ctx.hasPerm(sender, entry.permission())) continue;
                sender.sendMessage(Messages.PROTECTION.usageLine(entry.syntax(), entry.description()));
                shown++;
            }
        }
        if (shown == 0) {
            sender.sendMessage(Messages.PROTECTION.text(Text.NO_VISIBLE_REGION_COMMANDS));
        }
    }

    List<String> visibleNames(RegionCommandContext ctx, CommandSender sender) {
        List<String> out = new ArrayList<>();
        for (RegionSubcommand handler : ordered) {
            if (!handler.visibleInUsage()) continue;
            if (handler.visibleTo(ctx, sender)) out.add(handler.name());
        }
        return out;
    }
}
