package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.combos.InvSeeCommand;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvSeeCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private InvSeeCommand invSeeCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        invSeeCommand = new InvSeeCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void requiresPermission() {
        PlayerMock viewer = server.addPlayer();
        viewer.nextComponentMessage(); // Clear join message
        viewer.setOp(false);

        invSeeCommand.onCommand(viewer, bukkitCmd, "invsee", new String[]{"Target"});
        MessageAssert.assertMessageSent(viewer, "You don't have permission");
    }

    @Test
    void failsIfTargetOffline() {
        PlayerMock viewer = server.addPlayer();
        viewer.nextComponentMessage(); // Clear join message
        viewer.addAttachment(plugin, Permissions.ADMIN_INVSEE, true);

        invSeeCommand.onCommand(viewer, bukkitCmd, "invsee", new String[]{"OfflinePlayer"});
        MessageAssert.assertMessageSent(viewer, "is not online");
    }

    @Test
    void opensInventorySuccessfully() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock target = server.addPlayer("Target");
        viewer.nextComponentMessage(); // Clear join message
        target.nextComponentMessage(); // Clear join message
        viewer.addAttachment(plugin, Permissions.ADMIN_INVSEE, true);

        invSeeCommand.onCommand(viewer, bukkitCmd, "invsee", new String[]{"Target"});

        assertNotNull(viewer.getOpenInventory());
    }

    @Test
    void preventsSelfInvSee() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.nextComponentMessage(); // Clear join message
        viewer.addAttachment(plugin, Permissions.ADMIN_INVSEE, true);

        invSeeCommand.onCommand(viewer, bukkitCmd, "invsee", new String[]{"Viewer"});

        MessageAssert.assertMessageSent(viewer, "You can't /invsee yourself");
    }
}
