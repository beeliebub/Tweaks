package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RegionCommandContext#hasPerm}'s fallback logic — moved into the production
 * package (was {@code tests.protection.HaPermFallbackTest}, reflecting into a private method on
 * {@code ProtectionCommand}) since the method it exercises now lives on the package-private
 * {@link RegionCommandContext}, mirroring the Blackjack split's precedent for testing
 * package-private seams directly rather than via reflection.
 *
 * <p>hasPerm() first checks Bukkit's player.hasPermission() (the attachment path), then falls
 * back to PermissionManager.calculateEffectivePermissions(). This ensures that permissions
 * granted via PermissionManager AFTER a player logs in (before the Bukkit attachment is
 * refreshed) are still honoured by the command.
 */
class HaPermFallbackTest {

    @BeforeAll
    static void setUpAll() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private RegionCommandContext ctx;
    private Player player;
    private PermissionManager permissionManager;

    @BeforeEach
    void setUp() {
        Tweaks plugin = mock(Tweaks.class);
        permissionManager = mock(PermissionManager.class);
        when(plugin.getPermissionManager()).thenReturn(permissionManager);

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        RegionSelectionManager selections = mock(RegionSelectionManager.class);
        ctx = new RegionCommandContext(plugin, protection, selections);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    // ─── Bukkit attachment path ───────────────────────────────────────────────

    @Test
    void hasPerm_trueWhenBukkitAttachmentGranted() {
        when(player.hasPermission(Permissions.PROTECTION_FLAG)).thenReturn(true);

        assertTrue(ctx.hasPerm(player, Permissions.PROTECTION_FLAG),
                "hasPerm must return true when Bukkit attachment grants the perm");
    }

    @Test
    void hasPerm_falseWhenBothPathsDeny() {
        UUID uuid = player.getUniqueId();
        when(player.hasPermission(Permissions.PROTECTION_FLAG)).thenReturn(false);
        when(permissionManager.calculateEffectivePermissions(uuid)).thenReturn(Set.of());

        assertFalse(ctx.hasPerm(player, Permissions.PROTECTION_FLAG),
                "hasPerm must return false when neither path grants the perm");
    }

    // ─── PermissionManager fallback path ─────────────────────────────────────

    @Test
    void hasPerm_trueWhenPermManagerFallbackGranted() {
        UUID uuid = player.getUniqueId();
        when(player.hasPermission(Permissions.PROTECTION_PURCHASEABLE)).thenReturn(false);
        when(permissionManager.calculateEffectivePermissions(uuid))
                .thenReturn(Set.of(Permissions.PROTECTION_PURCHASEABLE));

        assertTrue(ctx.hasPerm(player, Permissions.PROTECTION_PURCHASEABLE),
                "hasPerm must return true when PermissionManager fallback grants the perm");
    }

    @Test
    void hasPerm_falseWhenPermManagerDoesNotGrantPerm() {
        UUID uuid = player.getUniqueId();
        when(player.hasPermission(Permissions.PROTECTION_INFO)).thenReturn(false);
        when(permissionManager.calculateEffectivePermissions(uuid))
                .thenReturn(Set.of(Permissions.PROTECTION_PURCHASEABLE)); // different perm

        assertFalse(ctx.hasPerm(player, Permissions.PROTECTION_INFO),
                "hasPerm must return false when PermissionManager grants a different perm");
    }

    // ─── Console sender (non-player) ─────────────────────────────────────────

    @Test
    void hasPerm_consoleWithPermission_returnsTrue() {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(true);

        assertTrue(ctx.hasPerm(console, Permissions.PROTECTION_ADMIN),
                "Console with Bukkit permission should return true");
    }

    @Test
    void hasPerm_consoleWithoutPermission_returnsFalse() {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(false);

        assertFalse(ctx.hasPerm(console, Permissions.PROTECTION_ADMIN),
                "Non-player (console) has no PermissionManager fallback — must return false");
    }

    // ─── Null PermissionManager (edge case) ──────────────────────────────────

    @Test
    void hasPerm_nullPermissionManager_fallsBackGracefully() {
        // Simulate a plugin where getPermissionManager() returns null.
        Tweaks pluginWithNullPm = mock(Tweaks.class);
        when(pluginWithNullPm.getPermissionManager()).thenReturn(null);
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        RegionCommandContext ctxWithNullPm = new RegionCommandContext(
                pluginWithNullPm, protection, mock(RegionSelectionManager.class));

        when(player.hasPermission(Permissions.PROTECTION_INFO)).thenReturn(false);

        // Should NOT throw; the null check in hasPerm() guards against NPE.
        assertFalse(ctxWithNullPm.hasPerm(player, Permissions.PROTECTION_INFO));
    }
}
