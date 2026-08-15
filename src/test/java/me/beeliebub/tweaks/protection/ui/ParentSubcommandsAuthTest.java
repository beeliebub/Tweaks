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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a critical authorization boundary: {@code /region setparent}/
 * {@code unsetparent} previously had NO ownership check at all — gated only by the flat
 * {@code PROTECTION_PURCHASEABLE} permission (the same one granted to every claiming player), with
 * {@code ProtectionManager.setParent} itself never checking who owns either region. Any player
 * holding that permission could reparent or unparent ANY region on the server, not just their own;
 * for {@code unsetparent} there was zero geometric constraint either. {@link ParentSubcommands} now
 * resolves the child region and requires {@link RegionAuth#isOwnerOrAdmin} before mutating, mirroring
 * {@code RegionGUI.openSubRegionsMenu}'s pre-existing gate on the same action.
 */
class ParentSubcommandsAuthTest {

    private static final UUID OWNER    = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID STRANGER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");

    @BeforeAll
    static void setUpAll() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private ProtectionManager protection;
    private RegionCommandContext ctx;

    @BeforeEach
    void setUp() {
        protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("child", new Region(
                "child", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
        protection.regions().put("parent", new Region(
                "parent", OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
        ctx = new RegionCommandContext(mock(Tweaks.class), protection, mock(RegionSelectionManager.class));
    }

    private static Player playerWithId(UUID uuid) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.hasPermission(anyString())).thenReturn(false);
        return p;
    }

    @Test
    void setParentDeniedForNonOwnerNonAdmin() {
        new ParentSubcommands.SetParent().execute(ctx, playerWithId(STRANGER), new String[] {"child", "parent"});

        assertNull(protection.regions().get("child").parentId(),
                "a non-owner, non-admin must not be able to reparent someone else's region");
    }

    @Test
    void setParentAllowedForOwner() {
        new ParentSubcommands.SetParent().execute(ctx, playerWithId(OWNER), new String[] {"child", "parent"});

        assertEquals("parent", protection.regions().get("child").parentId());
    }

    @Test
    void setParentAllowedForAdmin() {
        Player admin = mock(Player.class);
        when(admin.getUniqueId()).thenReturn(STRANGER);
        when(admin.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(true);

        new ParentSubcommands.SetParent().execute(ctx, admin, new String[] {"child", "parent"});

        assertEquals("parent", protection.regions().get("child").parentId(),
                "an admin must still be able to reparent any region");
    }

    @Test
    void unsetParentDeniedForNonOwnerNonAdmin() {
        protection.setParent("child", "parent");

        new ParentSubcommands.UnsetParent().execute(ctx, playerWithId(STRANGER), new String[] {"child"});

        assertEquals("parent", protection.regions().get("child").parentId(),
                "a non-owner, non-admin must not be able to strip another player's region's parent");
    }

    @Test
    void unsetParentAllowedForOwner() {
        protection.setParent("child", "parent");

        new ParentSubcommands.UnsetParent().execute(ctx, playerWithId(OWNER), new String[] {"child"});

        assertNull(protection.regions().get("child").parentId());
    }

    @Test
    void setParentDeniedForUnknownChildDoesNotThrow() {
        new ParentSubcommands.SetParent().execute(ctx, playerWithId(STRANGER), new String[] {"ghost", "parent"});
        // Must resolve region-not-found before the auth check without throwing.
    }
}
