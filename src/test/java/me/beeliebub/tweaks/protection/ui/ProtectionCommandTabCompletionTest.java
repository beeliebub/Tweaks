package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtectionCommandTabCompletionTest {

    private static final UUID OWNER = UUID.randomUUID();

    private World world;
    private Player player;
    private ProtectionCommand command;

    @BeforeAll
    static void boot() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        world = MockBukkit.getMock().addSimpleWorld("autocomplete_world");
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(OWNER);
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission(anyString())).thenReturn(true);

        Tweaks plugin = mock(Tweaks.class);
        ProtectionManager protection = new ProtectionManager(plugin);
        protection.regions().put("home", new Region(
                "home", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
        command = new ProtectionCommand(plugin, protection, mock(RegionSelectionManager.class));
    }

    @Test
    void infoDoesNotSuggestWorldUntilAfterTheRegionName() {
        List<String> beforeRegionName = complete("i", "");
        assertFalse(beforeRegionName.contains(world.getName()),
                "info must not suggest worlds in the optional region-name position");

        List<String> afterRegionName = complete("i", "home", "");
        assertTrue(afterRegionName.contains(world.getName()),
                "info must suggest worlds after a region name has been supplied");

        assertFalse(complete("clear", "").contains(world.getName()),
                "commands without a world argument must not suggest loaded worlds");
    }

    private List<String> complete(String... args) {
        return command.onTabComplete(player, mock(Command.class), "rg", args);
    }
}
