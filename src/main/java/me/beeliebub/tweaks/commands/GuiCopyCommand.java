package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

// /guicopy [name] — admin command. Copies the targeted chest's full inventory (single or double)
// to plugins/Tweaks/guicopies/<name>.yml. If no name is given, one is auto-generated from the
// world key and block coordinates. Files include world+coords for later restoration; items are
// stored in both a human-readable YAML format and as a Base64 string for zero-data-loss restoration.
// Additionally, a copy-pasteable Java code snippet is generated for use in Paper plugins.
public class GuiCopyCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_DISTANCE = 8;
    // Restrictive on purpose so we never write outside guicopies/ via the user-supplied name.
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    private final JavaPlugin plugin;
    private final File copiesFolder;

    public GuiCopyCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.copiesFolder = new File(plugin.getDataFolder(), "guicopies");
        if (!copiesFolder.exists() && !copiesFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create guicopies directory: " + copiesFolder.getAbsolutePath());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_GUI_COPY)) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        Block target = player.getTargetBlockExact(MAX_DISTANCE);
        if (target == null || !(target.getState() instanceof Chest chest)) {
            player.sendMessage(Component.text("Look at a chest within " + MAX_DISTANCE + " blocks.", NamedTextColor.RED));
            return true;
        }

        Location loc = target.getLocation();
        String worldKey = target.getWorld().getKey().asString();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        String name;
        if (args.length >= 1) {
            name = args[0];
            if (!SAFE_NAME.matcher(name).matches()) {
                player.sendMessage(Component.text(
                        "Name may only contain letters, numbers, dots, dashes, and underscores.",
                        NamedTextColor.RED));
                return true;
            }
        } else {
            // Use the simple world key (no namespace) for filenames so the colon doesn't show up.
            name = target.getWorld().getKey().getKey() + "_" + x + "_" + y + "_" + z;
        }

        Inventory inventory = chest.getInventory();
        int size = inventory.getSize();

        File outputFile = new File(copiesFolder, name + ".yml");
        boolean overwriting = outputFile.exists();

        CompletableFuture.runAsync(() -> {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("world", worldKey);
            yaml.set("x", x);
            yaml.set("y", y);
            yaml.set("z", z);
            yaml.set("size", size);

            for (int i = 0; i < size; i++) {
                ItemStack item = inventory.getItem(i);
                if (item == null || item.getType().isAir()) continue;
                yaml.set("items." + i + ".readable", item);
                yaml.set("items." + i + ".base64",
                        java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }

            yaml.set("java-code", GuiCopyJavaGenerator.generate(inventory));

            try {
                yaml.save(outputFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save guicopy '" + name + "': " + e.getMessage());
            }
        });

        Component message = Component.text(overwriting ? "Overwrote " : "Saved ", NamedTextColor.GREEN)
                .append(Component.text("guicopies/" + name + ".yml", NamedTextColor.YELLOW))
                .append(Component.text(" (" + size + " slots from " + worldKey + " "
                        + x + "," + y + "," + z + ").", NamedTextColor.GREEN));
        player.sendMessage(message);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_GUI_COPY)) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        String[] children = copiesFolder.list();
        if (children == null) return Collections.emptyList();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String child : children) {
            if (child.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                String stem = child.substring(0, child.length() - 4);
                if (stem.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(stem);
            }
        }
        Collections.sort(out);
        return out;
    }
}