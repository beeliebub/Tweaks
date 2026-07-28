package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Renders {@code /region info} and {@code /region flags} output. Stateless, static-only. */
final class RegionInfoRenderer {

    private RegionInfoRenderer() {}

    static void printRegionFlags(CommandSender sender, Region region) {
        String name = region.id();
        boolean any = false;
        if (!region.flagRules().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_RULES_HEADER, name));
            for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
                for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                    sender.sendMessage(Messages.PROTECTION.booleanRule(
                            e.getKey().name(), rule.getKey().toKey(), rule.getValue()));
                }
            }
            any = true;
        }
        if (!region.materialFlags().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_LISTS_HEADER, name));
            for (Map.Entry<RegionFlag, Set<Material>> e : region.materialFlags().entrySet()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.INDENT)
                        .append(materialListLine(e.getKey(), e.getValue())));
            }
            any = true;
        }
        if (!region.entityFlags().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.ENTITY_LISTS_HEADER, name));
            for (Map.Entry<RegionFlag, Set<EntityType>> e : region.entityFlags().entrySet()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.INDENT)
                        .append(entityListLine(e.getKey(), e.getValue())));
            }
            any = true;
        }
        if (!any) {
            sender.sendMessage(Messages.PROTECTION.text(Text.NO_FLAG_RULES, name));
        }
    }

    static void printRegion(CommandSender sender, Region region) {
        sender.sendMessage(Messages.PROTECTION.text(Text.REGION_BULLET, region.id()));
        if (region.hasParent()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_PARENT, region.parentId()));
        }
        sender.sendMessage(Messages.PROTECTION.text(Text.REGION_OWNER, resolveName(region.owner())));
        if (!region.managers().isEmpty() || !region.managerGroups().isEmpty()) {
            List<String> managers = new ArrayList<>();
            for (UUID m : region.managers()) {
                managers.add(resolveName(m));
            }
            for (String g : region.managerGroups()) {
                managers.add(Messages.PROTECTION.value(Text.GROUP_LABEL, g));
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_MANAGERS, String.join(", ", managers)));
        }
        if (region.members().isEmpty() && region.memberGroups().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_MEMBERS_NONE));
        } else {
            List<String> members = new ArrayList<>();
            for (UUID m : region.members()) {
                members.add(resolveName(m));
            }
            for (String g : region.memberGroups()) {
                members.add(Messages.PROTECTION.value(Text.GROUP_LABEL, g));
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_MEMBERS, String.join(", ", members)));
        }
        if (region.flagRules().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_FLAGS_NONE));
        } else {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_FLAGS));
            for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
                for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                    sender.sendMessage(Messages.PROTECTION.indentedBooleanRule(
                            e.getKey().name(), rule.getKey().toKey(), rule.getValue()));
                }
            }
        }
        if (!region.materialFlags().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_MATERIAL_FLAGS));
            for (Map.Entry<RegionFlag, Set<Material>> e : region.materialFlags().entrySet()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.DEEP_INDENT)
                        .append(materialListLine(e.getKey(), e.getValue())));
            }
        }
        if (!region.entityFlags().isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_ENTITY_FLAGS));
            for (Map.Entry<RegionFlag, Set<EntityType>> e : region.entityFlags().entrySet()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.DEEP_INDENT)
                        .append(entityListLine(e.getKey(), e.getValue())));
            }
        }
    }

    // Hoverable label for one material-list flag entry: "FLAG_NAME (N entries)" — hover
    // text reveals the full Material list so /rg i stays readable when lists are long.
    private static Component materialListLine(RegionFlag flag, Set<Material> materials) {
        return Messages.PROTECTION.materialListSummary(
                flag.name(), materials.size(), joinNames(materials, Material::name));
    }

    private static Component entityListLine(RegionFlag flag, Set<EntityType> entities) {
        return Messages.PROTECTION.entityListSummary(
                flag.name(), entities.size(), joinNames(entities, EntityType::name));
    }

    private static <T> String joinNames(Set<T> values, Function<T, String> nameOf) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (T v : values) {
            if (!first) sb.append(", ");
            sb.append(nameOf.apply(v));
            first = false;
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static String resolveName(UUID uuid) {
        if (uuid == null) return Messages.PROTECTION.value(Text.UNKNOWN_NAME);
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String name = p.getName();
        return (name == null || name.isEmpty()) ? uuid.toString() : name;
    }
}
