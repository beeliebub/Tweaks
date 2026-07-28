package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the fix to {@link RegionAuth#isOwnerManagerOrAdmin}: before this fix it
 * only checked UUID-listed managers, so a player promoted to manager purely via
 * {@code /region addmanager <region> group:staff} had full in-world manager privileges (per
 * {@link Region#isManager(UUID, Set)}, which {@link ProtectionManager} uses for in-world
 * enforcement) but was rejected by every command/GUI action gated on this method. See
 * {@code ui/CLAUDE.md}'s formerly-documented "Known gap" note, now resolved.
 *
 * <p>Covers the auth check directly plus its two CLI call sites that don't touch Paper's Dialog
 * API ({@link MembershipSubcommands#member} and {@link RegionFlagEditor#setFlag}) — a full
 * {@code GuiSubcommand} success-path test would hit the same MockBukkit
 * {@code DialogInstancesProvider} gap documented elsewhere in this package's tests, so the shared
 * {@link RegionAuth} check (which {@code GuiSubcommand.openGuiFor} calls with the identical
 * argument shape) is exercised directly instead.
 */
class RegionAuthGroupTest {

    private static final UUID OWNER         = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID GROUP_MANAGER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID OUTSIDER      = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
    private static final String REGION = "home";

    @BeforeAll
    static void setUpAll() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private ProtectionManager protection;
    private PermissionManager permissions;
    private Region region;

    @BeforeEach
    void setUp() {
        Tweaks plugin = mock(Tweaks.class);
        permissions = mock(PermissionManager.class);
        when(plugin.getPermissionManager()).thenReturn(permissions);

        Map<UUID, UserPermissions> users = new HashMap<>();
        UserPermissions groupManagerPerms = mock(UserPermissions.class);
        when(groupManagerPerms.getGroups()).thenReturn(Set.of("staff"));
        users.put(GROUP_MANAGER, groupManagerPerms);
        UserPermissions outsiderPerms = mock(UserPermissions.class);
        when(outsiderPerms.getGroups()).thenReturn(Set.of("peasants"));
        users.put(OUTSIDER, outsiderPerms);
        when(permissions.getUsers()).thenReturn(users);

        protection = new ProtectionManager(plugin);
        region = new Region(REGION, OWNER, List.of(), Map.of(), Map.of(),
                null, null, null, List.of(), Map.of(), 0, List.of(), List.of("staff"));
        protection.regions().put(REGION, region);
    }

    private static Player playerWithId(UUID uuid) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.hasPermission(anyString())).thenReturn(false);
        return p;
    }

    // ------------------------------------------------------------------
    // RegionAuth.isOwnerManagerOrAdmin — direct coverage
    // ------------------------------------------------------------------

    @Test
    void groupPromotedManagerPassesOwnerManagerOrAdmin() {
        assertTrue(RegionAuth.isOwnerManagerOrAdmin(playerWithId(GROUP_MANAGER), region, protection),
                "a player whose group is listed in managerGroups must pass isOwnerManagerOrAdmin, "
                        + "matching the in-world enforcement Region#isManager(UUID, Set) already grants");
    }

    @Test
    void outsiderWithUnrelatedGroupFailsOwnerManagerOrAdmin() {
        assertFalse(RegionAuth.isOwnerManagerOrAdmin(playerWithId(OUTSIDER), region, protection),
                "a player whose groups don't intersect managerGroups must still be rejected");
    }

    @Test
    void ownerStillPassesRegardlessOfGroups() {
        assertTrue(RegionAuth.isOwnerManagerOrAdmin(playerWithId(OWNER), region, protection));
    }

    // ------------------------------------------------------------------
    // End-to-end: CLI paths gated by isOwnerManagerOrAdmin
    // ------------------------------------------------------------------

    @Test
    void groupManagerCanAddMemberGroupViaCli() {
        RegionCommandContext ctx = new RegionCommandContext(
                pluginFor(), protection, mock(RegionSelectionManager.class));
        MembershipSubcommands.member(ctx, playerWithId(GROUP_MANAGER), new String[] {REGION, "group:vip"}, true);

        assertTrue(protection.regions().get(REGION).memberGroups().contains("vip"),
                "a group-promoted manager must be able to add a member group via /region addmember");
    }

    @Test
    void groupManagerCanSetFlagViaCli() {
        int rc = RegionFlagEditor.setFlag(
                playerWithId(GROUP_MANAGER), protection, permissions, REGION, "PVP", "true");

        assertEquals(1, rc, "a group-promoted manager must be able to set a flag via /region flag");
        assertEquals(Boolean.TRUE, protection.regions().get(REGION)
                .rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    private Tweaks pluginFor() {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getPermissionManager()).thenReturn(permissions);
        return plugin;
    }
}
