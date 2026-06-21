package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.ProtectionCommand;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedConstruction;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests for the hasPerm() fallback logic in ProtectionCommand.
//
// hasPerm() first checks Bukkit's player.hasPermission() (the attachment path),
// then falls back to PermissionManager.calculateEffectivePermissions(). This
// ensures that permissions granted via PermissionManager AFTER a player logs in
// (before the Bukkit attachment is refreshed) are still honoured by the command.
//
// NOTE: MockBukkit is required so that Material.matchMaterial() and
// NamespacedKey construction work inside ProtectionKeys.init().
class HaPermFallbackTest {

    private static Method hasPerm;

    @BeforeAll
    static void setUpAll() throws Exception {
        MockBukkit.mock();
        try (MockedConstruction<org.bukkit.NamespacedKey> ignored =
                     mockConstruction(org.bukkit.NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
        hasPerm = ProtectionCommand.class.getDeclaredMethod(
                "hasPerm", CommandSender.class, String.class);
        hasPerm.setAccessible(true);
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private ProtectionCommand command;
    private Player player;
    private PermissionManager permissionManager;

    @BeforeEach
    void setUp() {
        Tweaks plugin = mock(Tweaks.class);
        permissionManager = mock(PermissionManager.class);
        when(plugin.getPermissionManager()).thenReturn(permissionManager);

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        RegionSelectionManager selections = mock(RegionSelectionManager.class);
        command = new ProtectionCommand(plugin, protection, selections);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    // ─── Bukkit attachment path ───────────────────────────────────────────────

    @Test
    void hasPerm_trueWhenBukkitAttachmentGranted() throws Exception {
        when(player.hasPermission(Permissions.PROTECTION_FLAG)).thenReturn(true);

        boolean result = (boolean) hasPerm.invoke(command, player, Permissions.PROTECTION_FLAG);

        assertTrue(result, "hasPerm must return true when Bukkit attachment grants the perm");
    }

    @Test
    void hasPerm_falseWhenBothPathsDeny() throws Exception {
        UUID uuid = player.getUniqueId();
        when(player.hasPermission(Permissions.PROTECTION_FLAG)).thenReturn(false);
        when(permissionManager.calculateEffectivePermissions(uuid)).thenReturn(Set.of());

        boolean result = (boolean) hasPerm.invoke(command, player, Permissions.PROTECTION_FLAG);

        assertFalse(result, "hasPerm must return false when neither path grants the perm");
    }

    // ─── PermissionManager fallback path ─────────────────────────────────────

    @Test
    void hasPerm_trueWhenPermManagerFallbackGranted() throws Exception {
        UUID uuid = player.getUniqueId();
        when(player.hasPermission(Permissions.PROTECTION_PURCHASEABLE)).thenReturn(false);
        when(permissionManager.calculateEffectivePermissions(uuid))
                .thenReturn(Set.of(Permissions.PROTECTION_PURCHASEABLE));

        boolean result = (boolean) hasPerm.invoke(command, player, Permissions.PROTECTION_PURCHASEABLE);

        assertTrue(result, "hasPerm must return true when PermissionManager fallback grants the perm");
    }

    @Test
    void hasPerm_falseWhenPermManagerDoesNotGrantPerm() throws Exception {
        UUID uuid = player.getUniqueId();
        when(player.hasPermission(Permissions.PROTECTION_INFO)).thenReturn(false);
        when(permissionManager.calculateEffectivePermissions(uuid))
                .thenReturn(Set.of(Permissions.PROTECTION_PURCHASEABLE)); // different perm

        boolean result = (boolean) hasPerm.invoke(command, player, Permissions.PROTECTION_INFO);

        assertFalse(result, "hasPerm must return false when PermissionManager grants a different perm");
    }

    // ─── Console sender (non-player) ─────────────────────────────────────────

    @Test
    void hasPerm_consoleWithPermission_returnsTrue() throws Exception {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(true);

        boolean result = (boolean) hasPerm.invoke(command, console, Permissions.PROTECTION_ADMIN);

        assertTrue(result, "Console with Bukkit permission should return true");
    }

    @Test
    void hasPerm_consoleWithoutPermission_returnsFalse() throws Exception {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(false);

        boolean result = (boolean) hasPerm.invoke(command, console, Permissions.PROTECTION_ADMIN);

        assertFalse(result, "Non-player (console) has no PermissionManager fallback — must return false");
    }

    // ─── Null PermissionManager (edge case) ──────────────────────────────────

    @Test
    void hasPerm_nullPermissionManager_fallsBackGracefully() throws Exception {
        // Simulate a plugin where getPermissionManager() returns null.
        Tweaks pluginWithNullPm = mock(Tweaks.class);
        when(pluginWithNullPm.getPermissionManager()).thenReturn(null);
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        ProtectionCommand cmdWithNullPm = new ProtectionCommand(
                pluginWithNullPm, protection, mock(RegionSelectionManager.class));

        when(player.hasPermission(Permissions.PROTECTION_INFO)).thenReturn(false);

        // Should NOT throw; the null check in hasPerm() guards against NPE.
        boolean result = (boolean) hasPerm.invoke(cmdWithNullPm, player, Permissions.PROTECTION_INFO);
        assertFalse(result);
    }
}
