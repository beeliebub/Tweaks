package me.beeliebub.tweaks.tools;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Free, plain legacy command for setting or clearing the main-hand display name. */
public final class RenameCommand implements CommandExecutor, TabCompleter {

    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public RenameCommand(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.TOOLS.renameRequiresPlayer());
            return true;
        }
        if (!plugin.getConfig().getBoolean("tools.rename.enabled", true)) {
            player.sendMessage(Messages.TOOLS.featureDisabled("Rename"));
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            player.sendMessage(Messages.TOOLS.renameRequiresItem());
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (args.length == 0) {
            meta.displayName(null);
            item.setItemMeta(meta);
            player.sendMessage(Messages.TOOLS.renameReset());
            return true;
        }
        Component parsed = ColorUtil.parse(String.join(" ", args));
        String plain = PlainTextComponentSerializer.plainText().serialize(parsed);
        int max = Math.max(1, Math.min(256, plugin.getConfig().getInt("tools.rename.max-length", 50)));
        if (plain.codePointCount(0, plain.length()) > max) {
            player.sendMessage(Messages.TOOLS.renameTooLong(max));
            return true;
        }
        meta.displayName(parsed);
        item.setItemMeta(meta);
        player.sendMessage(Messages.TOOLS.renamed());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
