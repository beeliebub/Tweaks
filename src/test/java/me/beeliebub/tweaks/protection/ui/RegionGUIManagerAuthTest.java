package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the "Edit Managers" menu's owner-only gate
 * ({@link RegionGUI#openManagersMenu}/{@code handleRemoveManager}/
 * {@code handleRemoveManagerGroup}/{@code openAddManagerDialog}/{@code handleAddManagerSubmission}):
 * each of these must require {@link RegionAuth#isOwnerOrAdmin}, matching the tooltip's own
 * "(owner only)" label and the CLI equivalent ({@code MembershipSubcommands.manager()}). Without
 * that gate, a plain region manager could promote/demote other managers through the GUI —
 * something the CLI explicitly forbids.
 *
 * <p>These tests only exercise the DENIAL path for a non-owner, non-admin manager. The authorized
 * path is not covered here because it proceeds to build a Paper {@code Dialog}, which MockBukkit
 * cannot construct (no {@code DialogInstancesProvider} service — see {@code RegionGUITest}'s and
 * {@code ui/CLAUDE.md}'s note on the same caveat). If the owner-only guard were ever removed, a
 * denied-path test calling into the authorized branch would fail loudly with
 * {@code NoSuchElementException} rather than silently passing — that failure mode is itself part
 * of what makes this regression test meaningful.
 */
class RegionGUIManagerAuthTest {

    private static final UUID OWNER   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID MANAGER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
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
    private Region region;
    private Player manager;

    @BeforeEach
    void setUp() {
        protection = mock(ProtectionManager.class);
        region = new Region(REGION, OWNER, List.of(), EnumSet.noneOf(RegionFlag.class))
                .addManager(MANAGER);

        // A region MANAGER, not the owner and not an admin — exactly the role this bug let
        // reach manager-editing actions it should never reach. RegionAuth.isOwnerOrAdmin denies
        // managers just as it does strangers (it only special-cases owner/admin), so being a
        // real manager on `region` isn't load-bearing for these assertions, but constructing the
        // region this way keeps the fixture honest about the role under test.
        manager = mock(Player.class);
        when(manager.getUniqueId()).thenReturn(MANAGER);
        when(manager.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(false);
    }

    @Test
    void openManagersMenu_deniesNonOwnerManagerWithoutTouchingDialogOrProtectionManager() {
        RegionGUI.openManagersMenu(manager, region, protection, null, 0);

        verify(manager).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(protection, never()).removeManager(anyString(), any());
        verify(protection, never()).addManager(anyString(), any());
    }

    @Test
    void handleRemoveManager_deniesNonOwnerManager_doesNotMutate() {
        RegionGUI.handleRemoveManager(manager, region, protection, null, MANAGER, 0);

        verify(manager).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(protection, never()).removeManager(anyString(), any());
    }

    @Test
    void handleRemoveManagerGroup_deniesNonOwnerManager_doesNotMutate() {
        RegionGUI.handleRemoveManagerGroup(manager, region, protection, null, "builders", 0);

        verify(manager).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(protection, never()).removeManagerGroup(anyString(), anyString());
    }

    @Test
    void openAddManagerDialog_deniesNonOwnerManagerWithoutOpeningInputDialog() {
        RegionGUI.openAddManagerDialog(manager, region, protection, null);

        verify(manager).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void handleAddManagerSubmission_deniesNonOwnerManager_doesNotMutate() {
        RegionGUI.handleAddManagerSubmission(manager, region, protection, null, "SomePlayer");

        verify(manager).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(protection, never()).addManager(anyString(), any());
        verify(protection, never()).addManagerGroup(anyString(), anyString());
    }

    @Test
    void ownerIsNotDenied_regressionGuardOnly() {
        // Sanity check on the predicate itself (no Dialog construction reached): an owner must
        // NOT be denied. We can't call the GUI methods for the owner path (they'd proceed into
        // Dialog construction, which MockBukkit can't do), so this pins RegionAuth directly.
        assertFalse(!RegionAuth.isOwnerOrAdmin(ownerMock(), region, protection),
                "owner must pass the same isOwnerOrAdmin gate the manager-menu methods use");
    }

    private Player ownerMock() {
        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(OWNER);
        when(owner.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(false);
        return owner;
    }
}
