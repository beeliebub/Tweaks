package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.DisplayChestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DisplayChestCommand implements CommandExecutor, TabCompleter {

    private final DisplayChestManager displayChestManager;

    public DisplayChestCommand(DisplayChestManager displayChestManager) {
        this.displayChestManager = displayChestManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        // 'off' is exclusive — never combines with hand/side.
        for (String arg : args) {
            if (arg.equalsIgnoreCase("off")) {
                boolean enabled = displayChestManager.toggleRemovalMode(player.getUniqueId());
                if (enabled) {
                    player.sendMessage(Component.text("Display Chest removal mode ENABLED. Click a chest to remove its item display.").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Display Chest removal mode DISABLED.").color(NamedTextColor.RED));
                }
                return true;
            }
        }

        // hand and side may appear in any order, both, or neither.
        boolean handFlag = false;
        boolean sideFlag = false;
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.equals("hand")) handFlag = true;
            else if (lower.equals("side")) sideFlag = true;
        }

        boolean enabled = displayChestManager.toggleSetupMode(player.getUniqueId());
        if (enabled) {
            displayChestManager.setUseCurrentHand(player.getUniqueId(), handFlag);
            displayChestManager.setEmbedSide(player.getUniqueId(), sideFlag);
            player.sendMessage(Component.text(buildEnableMessage(handFlag, sideFlag)).color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Display Chest setup mode DISABLED.").color(NamedTextColor.RED));
        }
        return true;
    }

    private static String buildEnableMessage(boolean hand, boolean side) {
        String source = hand
                ? "Currently-held item is used on each click"
                : "Click a chest to display its top-left item";
        String placement = side
                ? "Item embeds on the clicked face."
                : "Item floats above the chest.";
        StringBuilder sb = new StringBuilder("Display Chest setup mode ENABLED");
        if (hand || side) {
            sb.append(" (");
            if (hand) sb.append("hand");
            if (hand && side) sb.append(" + ");
            if (side) sb.append("side");
            sb.append(")");
        }
        sb.append(". ").append(source).append(". ").append(placement);
        return sb.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return List.of();
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);

        // First positional: any of the three keywords. 'off' alone is exclusive,
        // so don't keep suggesting it after the user already typed another arg.
        if (args.length == 1) {
            return filterPrefix(List.of("hand", "side", "off"), partial);
        }

        // Second positional: only the complementary keyword (hand <-> side).
        // Suppress repeats and never suggest 'off' once hand/side is in play.
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if (first.equals("hand")) options.add("side");
            else if (first.equals("side")) options.add("hand");
            return filterPrefix(options, partial);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        List<String> out = new ArrayList<>(options.size());
        for (String opt : options) {
            if (opt.startsWith(prefix)) out.add(opt);
        }
        return out;
    }
}
