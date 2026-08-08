package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RegionCommandContext {

    private static final int MAX_REGION_SUGGESTIONS = 100;
    // Bukkit's enum-backed registries are not guaranteed to be initialized
    // when this command class is loaded by an isolated unit test. Keep these
    // derived lists lazy so class initialization itself remains side-effect free.
    private static final class Names {
        private static final List<String> BLOCK_MATERIAL_NAMES = materialNames();
        private static final List<String> ENTITY_TYPE_NAMES = entityNames();
    }

    final Tweaks plugin;
    final ProtectionManager protection;
    final RegionSelectionManager selections;
    private final World scopeWorld;
    private final boolean legacyBareLookup;

    RegionCommandContext(Tweaks plugin, ProtectionManager protection, RegionSelectionManager selections) {
        this(plugin, protection, selections, null, false);
    }

    private RegionCommandContext(Tweaks plugin, ProtectionManager protection,
                                 RegionSelectionManager selections, World scopeWorld,
                                 boolean legacyBareLookup) {
        this.plugin = plugin;
        this.protection = protection;
        this.selections = selections;
        this.scopeWorld = scopeWorld;
        this.legacyBareLookup = legacyBareLookup;
    }

    RegionCommandContext withScope(World world) {
        return new RegionCommandContext(plugin, protection, selections, world, false);
    }

    RegionCommandContext legacyBareLookup() {
        return new RegionCommandContext(plugin, protection, selections, null, true);
    }

    World scopeWorld(CommandSender sender) {
        return scopeWorld != null ? scopeWorld
                : sender instanceof Player player ? player.getWorld() : null;
    }

    World requireWorld(CommandSender sender) {
        World world = scopeWorld(sender);
        if (world == null) sender.sendMessage(Messages.PROTECTION.text(Text.WORLD_REQUIRED));
        return world;
    }

    boolean requireNamedRegionWorld(CommandSender sender, String[] args) {
        return args.length == 0 || legacyBareLookup || scopeWorld(sender) != null || sender instanceof Player
                || requireWorld(sender) != null;
    }

    PermissionManager permissions() {
        return plugin == null ? null : plugin.getPermissionManager();
    }

    boolean hasPerm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        if (!(sender instanceof Player player)) return false;
        PermissionManager pm = permissions();
        return pm != null && pm.calculateEffectivePermissions(player.getUniqueId()).contains(permission);
    }

    Region resolveRegion(CommandSender sender, String name) {
        World world = scopeWorld(sender);
        // A real Bukkit Player always has a world. The null-world branch is
        // only a compatibility seam for isolated command tests and legacy
        // world-less regions; it probes the bare legacy key only and never
        // searches another world's composite entries.
        if (world == null) {
            return (sender instanceof Player || legacyBareLookup) ? protection.byName(null, name) : null;
        }
        if (ProtectionManager.GLOBAL_REGION_ID.equals(name)) return protection.globalRegion(world);
        return protection.byName(world, name);
    }

    static World senderWorld(CommandSender sender) {
        return sender instanceof Player player ? player.getWorld() : null;
    }

    void showUsage(CommandSender sender, RegionSubcommand handler) {
        List<RegionUsageEntry> entries = handler.usage();
        RegionUsageEntry toShow = null;
        for (RegionUsageEntry entry : entries) {
            if (entry.permission() != null && !hasPerm(sender, entry.permission())) continue;
            toShow = entry;
            break;
        }
        if (toShow == null && !entries.isEmpty()) toShow = entries.getFirst();
        if (toShow == null) return;
        sender.sendMessage(Messages.PROTECTION.text(Text.USAGE_HEADER));
        sender.sendMessage(Messages.PROTECTION.usageLine(toShow.syntax(), toShow.description()));
    }

    static String joinFrom(String[] args, int fromIndex) {
        if (fromIndex >= args.length) return "";
        StringBuilder joined = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) joined.append(' ');
            joined.append(args[i]);
        }
        return joined.toString();
    }

    static List<String> filterByPrefix(List<String> options, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(option);
        }
        return out;
    }

    List<String> regionSuggestions(CommandSender sender, String partial) {
        World world = scopeWorld(sender);
        if (world == null) return List.of();
        boolean admin = hasPerm(sender, Permissions.PROTECTION_ADMIN);
        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        Set<String> groups = actor == null ? Set.of() : protection.groupsOf(actor);
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Region region : protection.regions().values()) {
            if (region.worldName() != null && !region.worldName().equals(world.getName())) continue;
            if (!admin && (actor == null || (!region.isOwner(actor)
                    && !region.isManager(actor, groups) && !region.isMember(actor, groups)))) continue;
            if (seen.add(region.id()) && region.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(region.id());
            }
        }
        Region global = protection.globalRegion(world);
        if (admin && global != null && seen.add(global.id())
                && global.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
            matches.add(global.id());
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches.size() > MAX_REGION_SUGGESTIONS
                ? List.copyOf(matches.subList(0, MAX_REGION_SUGGESTIONS)) : matches;
    }

    List<String> regionGroupSuggestions(World world, String regionName, String prefix, boolean managers) {
        Region region = scopedRegion(world, regionName);
        if (region == null) return List.of();
        List<String> groups = managers ? region.managerGroups() : region.memberGroups();
        List<String> out = new ArrayList<>();
        for (String group : groups) {
            String suggestion = "group:" + group;
            if (suggestion.startsWith(prefix)) out.add(suggestion);
        }
        return out;
    }

    List<String> regionGroupSuggestions(String regionName, String prefix, boolean managers) {
        return regionGroupSuggestions(null, regionName, prefix, managers);
    }

    List<String> allGroupSuggestions(String prefix) {
        PermissionManager pm = permissions();
        if (pm == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String groupName : pm.getGroups().keySet()) {
            String suggestion = "group:" + groupName.toLowerCase(Locale.ROOT);
            if (suggestion.startsWith(prefix)) out.add(suggestion);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    List<String> regionMemberNames(World world, String regionName, String prefix) {
        Region region = scopedRegion(world, regionName);
        if (region == null) return List.of();
        List<String> out = new ArrayList<>();
        for (UUID member : region.members()) {
            String name = Bukkit.getOfflinePlayer(member).getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    List<String> regionMemberNames(String regionName, String prefix) {
        return regionMemberNames(null, regionName, prefix);
    }

    @SuppressWarnings("deprecation")
    List<String> regionManagerNames(World world, String regionName, String prefix) {
        Region region = scopedRegion(world, regionName);
        if (region == null) return List.of();
        List<String> out = new ArrayList<>();
        for (UUID manager : region.managers()) {
            String name = Bukkit.getOfflinePlayer(manager).getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    List<String> regionManagerNames(String regionName, String prefix) {
        return regionManagerNames(null, regionName, prefix);
    }

    static List<String> onlinePlayerNames(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
        }
        return out;
    }

    List<String> targetNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String fixed : List.of("owner", "manager", "member", "default")) {
            if (fixed.startsWith(prefix)) out.add(fixed);
        }
        PermissionManager pm = permissions();
        if (pm != null) {
            for (String groupName : pm.getGroups().keySet()) {
                String group = groupName.toLowerCase(Locale.ROOT);
                if (group.startsWith(prefix)) out.add(group);
            }
        }
        return out;
    }

    static List<String> blockMaterialNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : Names.BLOCK_MATERIAL_NAMES) if (name.startsWith(prefix)) out.add(name);
        return out;
    }

    static List<String> entityTypeNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : Names.ENTITY_TYPE_NAMES) if (name.startsWith(prefix)) out.add(name);
        return out;
    }

    static List<String> flagNameSuggestions(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (RegionFlag flag : RegionFlag.values()) {
            String name = flag.name();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
        }
        return out;
    }

    private Region scopedRegion(World world, String id) {
        if (world == null) return protection.byName(null, id);
        return ProtectionManager.GLOBAL_REGION_ID.equals(id)
                ? protection.globalRegion(world) : protection.byName(world, id);
    }

    private static List<String> materialNames() {
        List<String> names = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isBlock()) names.add(material.name().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableList(names);
    }

    private static List<String> entityNames() {
        List<String> names = new ArrayList<>();
        for (EntityType type : EntityType.values()) names.add(type.name().toLowerCase(Locale.ROOT));
        return Collections.unmodifiableList(names);
    }
}
