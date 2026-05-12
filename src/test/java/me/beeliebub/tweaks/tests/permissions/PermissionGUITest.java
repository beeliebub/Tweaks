package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionGroup;
import me.beeliebub.tweaks.permissions.PermissionGUI;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
import me.beeliebub.tweaks.permissions.PermissionHolder;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
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

class PermissionGUITest {

    private ServerMock server;
    private PermissionManager manager;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        manager = mock(PermissionManager.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openMainMenu() {
        PermissionGUI.openMainMenu(player, manager);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertNotNull(top);
        assertTrue(top.getHolder() instanceof PermissionHolder holder && holder.kind() == PermissionHolder.MenuKind.MAIN);
    }

    @Test
    void openGroupsMenu() {
        when(manager.getGroups()).thenReturn(new HashMap<>());
        PermissionGUI.openGroupsMenu(player, manager, 0);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertNotNull(top);
        assertTrue(top.getHolder() instanceof PermissionHolder holder && holder.kind() == PermissionHolder.MenuKind.GROUPS_LIST);
    }

    @Test
    void openGroupHub() {
        Map<String, PermissionGroup> groups = new HashMap<>();
        groups.put("admin", new PermissionGroup("admin"));
        when(manager.getGroups()).thenReturn(groups);
        
        PermissionGUI.openGroupHub(player, manager, "admin");
        Inventory top = player.getOpenInventory().getTopInventory();
        assertNotNull(top);
        assertTrue(top.getHolder() instanceof PermissionHolder holder && holder.kind() == PermissionHolder.MenuKind.GROUP_HUB);
    }

    @Test
    void openUsersMenu() {
        when(manager.getUsers()).thenReturn(new HashMap<>());
        PermissionGUI.openUsersMenu(player, manager, 0);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertNotNull(top);
        assertTrue(top.getHolder() instanceof PermissionHolder holder && holder.kind() == PermissionHolder.MenuKind.USERS_LIST);
    }

    @Test
    void openUserHub() {
        UUID uuid = UUID.randomUUID();
        when(manager.getUserPermissions(uuid)).thenReturn(new UserPermissions(uuid));
        
        PermissionGUI.openUserHub(player, manager, uuid);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertNotNull(top);
        assertTrue(top.getHolder() instanceof PermissionHolder holder && holder.kind() == PermissionHolder.MenuKind.USER_HUB);
    }
}
