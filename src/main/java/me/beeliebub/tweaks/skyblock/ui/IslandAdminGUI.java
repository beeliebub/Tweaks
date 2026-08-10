package me.beeliebub.tweaks.skyblock.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminServices;
import me.beeliebub.tweaks.skyblock.ui.admin.SkyblockAdminScreens;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the full Skyblock administration hub. */
public final class IslandAdminGUI {
    private final SkyblockAdminService admin;
    private final SkyblockAdminScreens screens;

    public IslandAdminGUI(SkyblockAdminService admin) {
        this(null, null, admin);
    }

    public IslandAdminGUI(SkyblockBootstrap.Runtime runtime, SkyblockAdminService admin) {
        this(null, runtime, admin);
    }

    public IslandAdminGUI(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime, SkyblockAdminService admin) {
        this.admin = admin;
        this.screens = plugin == null || runtime == null ? null : new SkyblockAdminScreens(plugin, runtime, admin);
    }

    public IslandAdminGUI(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime, SkyblockAdminServices services) {
        this.admin = services.skyblockAdmin();
        this.screens = plugin == null || runtime == null ? null : new SkyblockAdminScreens(plugin, runtime, services);
    }

    public void open(Player player) {
        if (player == null || !player.hasPermission(Permissions.ADMIN_SKYBLOCK)) {
            if (player != null) player.sendMessage(Messages.SKYBLOCK.adminNoPermission());
            return;
        }
        if (screens != null) {
            screens.open(player);
            return;
        }
        player.sendMessage(Messages.SKYBLOCK.adminText("Open /isadmin gui from the configured Skyblock runtime.",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
    }

    public void shutdown() {
        if (screens != null) screens.shutdown();
    }
}
