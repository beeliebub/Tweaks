package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

final class RegionWorldArgument {

    private RegionWorldArgument() {}

    static Result strip(RegionCommandContext context, CommandSender sender,
                        RegionSubcommand handler, String[] args) {
        if (args.length == 0 || !context.hasPerm(sender, Permissions.PROTECTION_ADMIN)) {
            return new Result(null, args);
        }
        String candidate = args[args.length - 1];
        // `owner` is a valid flag target and a common final token in the
        // region command grammar; never reinterpret it as a world argument.
        if ("owner".equalsIgnoreCase(candidate)) {
            return new Result(null, args);
        }
        World world = Bukkit.getWorld(candidate);
        if (world == null || args.length - 1 < handler.minArgs()) {
            return new Result(null, args);
        }
        String[] stripped = new String[args.length - 1];
        System.arraycopy(args, 0, stripped, 0, stripped.length);
        return new Result(world, stripped);
    }

    record Result(World world, String[] args) {}
}
