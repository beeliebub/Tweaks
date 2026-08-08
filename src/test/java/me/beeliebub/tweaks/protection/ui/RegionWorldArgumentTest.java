package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RegionWorldArgumentTest {

    @BeforeAll static void boot() { MockBukkit.mock(); }
    @AfterAll static void stop() { MockBukkit.unmock(); }

    @Test
    void stripsOnlyAnAdminLoadedTrailingWorldWhenMinimumArgsRemain() {
        World world = MockBukkit.getMock().addSimpleWorld("nether");
        Player admin = player(true);
        RegionCommandContext context = new RegionCommandContext(mock(Tweaks.class),
                new ProtectionManager(mock(Tweaks.class)), mock(RegionSelectionManager.class));
        RegionSubcommand handler = handler(1);

        RegionWorldArgument.Result result = RegionWorldArgument.strip(
                context, admin, handler, new String[] {"base", "nether"});

        assertSame(world, result.world());
        assertArrayEquals(new String[] {"base"}, result.args());
    }

    @Test
    void nonAdminUnloadedAndOwnerTokensFallThroughUntouched() {
        RegionCommandContext context = new RegionCommandContext(mock(Tweaks.class),
                new ProtectionManager(mock(Tweaks.class)), mock(RegionSelectionManager.class));
        RegionSubcommand handler = handler(1);
        assertArrayEquals(new String[] {"base", "nether"}, RegionWorldArgument.strip(
                context, player(false), handler, new String[] {"base", "nether"}).args());
        assertArrayEquals(new String[] {"base", "missing"}, RegionWorldArgument.strip(
                context, player(true), handler, new String[] {"base", "missing"}).args());
        assertArrayEquals(new String[] {"base", "owner"}, RegionWorldArgument.strip(
                context, player(true), handler, new String[] {"base", "owner"}).args());
    }

    @Test
    void consoleWithoutWorldIsRejectedByTheContext() {
        Tweaks plugin = mock(Tweaks.class);
        RegionCommandContext context = new RegionCommandContext(plugin,
                new ProtectionManager(plugin), mock(RegionSelectionManager.class));
        CommandSender console = mock(CommandSender.class);
        assertFalse(context.requireNamedRegionWorld(console, new String[] {"base"}));
        verify(console).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    private static Player player(boolean admin) {
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(admin);
        return player;
    }

    private static RegionSubcommand handler(int minArgs) {
        return new RegionSubcommand() {
            public String name() { return "test"; }
            public String permission() { return null; }
            public int minArgs() { return minArgs; }
            public List<RegionUsageEntry> usage() { return List.of(); }
            public void execute(RegionCommandContext c, CommandSender s, String[] a) {}
            public List<String> tabComplete(RegionCommandContext c, CommandSender s, String[] a) { return List.of(); }
        };
    }
}
