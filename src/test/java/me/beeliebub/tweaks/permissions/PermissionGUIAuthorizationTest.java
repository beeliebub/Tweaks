package me.beeliebub.tweaks.permissions;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionGUIAuthorizationTest {

    @Test
    void deniesAPlayerWhoNoLongerHasPermissionManagementAccess() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_PERMISSIONS)).thenReturn(false);

        assertFalse(PermissionGUI.canManage(player));
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void allowsAPlayerWithPermissionManagementAccess() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_PERMISSIONS)).thenReturn(true);

        assertTrue(PermissionGUI.canManage(player));
    }
}
