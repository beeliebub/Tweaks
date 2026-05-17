package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.*;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionCommandTest {

    private ServerMock server;
    private PermissionManager manager;
    private PermissionCommand command;
    private PlayerMock player;
    private org.bukkit.plugin.Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        manager = mock(PermissionManager.class);
        command = new PermissionCommand(manager);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onCommandNoPermission() {
        player.addAttachment(plugin, Permissions.ADMIN_PERMISSIONS, false);
        command.onCommand(player, mock(Command.class), "tprm", new String[0]);
        MessageAssert.assertMessageSent(player, "No permission.");
    }

    // The /tprm gui main menu is now a Paper Dialog. MockBukkit does not load a
    // DialogInstancesProvider service, so ActionButton.builder(...) throws
    // NoSuchElementException under MockBukkit. Verification of the dialog open
    // path lives outside this unit test (real-server smoke test).

    @Test
    void onCommandGroupCreate() {
        player.setOp(true);
        when(manager.getGroups()).thenReturn(new HashMap<>());
        
        command.onCommand(player, mock(Command.class), "tprm", new String[]{"group", "testgroup", "create"});
        
        verify(manager).saveGroups();
        MessageAssert.assertMessageSent(player, "Group 'testgroup' created.");
    }

    @Test
    void onCommandGroupDelete() {
        player.setOp(true);
        Map<String, PermissionGroup> groups = new HashMap<>();
        groups.put("testgroup", new PermissionGroup("testgroup"));
        when(manager.getGroups()).thenReturn(groups);
        
        command.onCommand(player, mock(Command.class), "tprm", new String[]{"group", "testgroup", "delete"});
        
        verify(manager).saveGroups();
        MessageAssert.assertMessageSent(player, "Group 'testgroup' deleted.");
        assertFalse(groups.containsKey("testgroup"));
    }

    @Test
    void onCommandUserAddPerm() {
        player.setOp(true);
        PlayerMock target = server.addPlayer("Target");
        UserPermissions userPerms = spy(new UserPermissions(target.getUniqueId()));
        when(manager.getUserPermissions(target.getUniqueId())).thenReturn(userPerms);
        
        command.onCommand(player, mock(Command.class), "tprm", new String[]{"user", "Target", "addperm", "some.perm"});
        
        verify(userPerms).addPermission("some.perm");
        verify(manager).saveUsers();
        MessageAssert.assertMessageSent(player, "Added permission to user Target.");
    }
}
