package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Permission-gate coverage for the /region gui subcommand — moved into the production package
 * (was {@code tests.protection.RegionGUITest}, reflecting into a private {@code handleGui} on
 * {@code ProtectionCommand}) since that dispatch now lives on {@link GuiSubcommand#gui}, package-
 * private in this same package.
 *
 * <p>The dialog itself cannot be opened under MockBukkit (Paper's Dialog API requires a service
 * MockBukkit does not provide — see PermissionGUITest for the same caveat), so these tests stub
 * {@code RegionGUI.openRegionHub} via Mockito's static mocking and verify the COMMAND-layer guard:
 * owners, managers, and admins reach the open call; everyone else is short-circuited with a
 * denial message.
 */
class RegionGUITest {

    private static final UUID OWNER     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID MANAGER   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID STRANGER  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
    private static final UUID ADMIN     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4");

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
    private RegionCommandContext ctx;
    private MockedStatic<RegionGUI> regionGuiStatic;

    @BeforeEach
    void setUp() {
        protection = new ProtectionManager(mock(Tweaks.class));
        // Legacy null-world region so resolveRegion's byNameAnyWorld branch
        // finds it without needing to wire up a mock World. The /region gui
        // command path doesn't care about world bounds — it just resolves the
        // region by name and checks ownership.
        protection.regions().put(REGION, new Region(
                REGION, OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
        protection.addManager(REGION, MANAGER);

        // RegionSelectionManager is unused on the /region gui path but required
        // by RegionCommandContext's constructor.
        ctx = new RegionCommandContext(mock(Tweaks.class), protection, mock(RegionSelectionManager.class));

        regionGuiStatic = mockStatic(RegionGUI.class);
    }

    @AfterEach
    void tearDown() {
        regionGuiStatic.close();
    }

    private void invokeGui(org.bukkit.command.CommandSender sender, String... args) {
        GuiSubcommand.gui(ctx, sender, args);
    }

    private Player playerWith(UUID uuid, boolean admin) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(admin);
        return p;
    }

    @Test
    void ownerCanOpenRegionGui() {
        Player owner = playerWith(OWNER, false);

        invokeGui(owner, REGION);

        Region resolved = protection.regions().get(REGION);
        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(eq(owner), eq(resolved), eq(protection), isNull()),
                times(1));
    }

    @Test
    void managerCanOpenRegionGui() {
        Player manager = playerWith(MANAGER, false);

        invokeGui(manager, REGION);

        Region resolved = protection.regions().get(REGION);
        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(eq(manager), eq(resolved), eq(protection), isNull()),
                times(1));
    }

    @Test
    void adminCanOpenAnyRegionGui() {
        // No ownership, no manager role — admin perm alone must suffice.
        Player admin = playerWith(ADMIN, true);

        invokeGui(admin, REGION);

        Region resolved = protection.regions().get(REGION);
        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(eq(admin), eq(resolved), eq(protection), isNull()),
                times(1));
    }

    @Test
    void strangerCannotOpenRegionGui() {
        Player stranger = playerWith(STRANGER, false);

        invokeGui(stranger, REGION);

        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(any(), any(), any(), any()),
                never());
    }

    @Test
    void unknownRegionDoesNotOpenGui() {
        Player owner = playerWith(OWNER, false);

        invokeGui(owner, "ghost_region");

        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(any(), any(), any(), any()),
                never());
    }

    @Test
    void consoleWithoutRegionNameDoesNotOpenGui() {
        // Console can pass isOwnerManagerOrAdmin (non-Player → true) but the
        // no-args branch refuses with a "must supply a region name" message
        // because regionsAt() needs a player Location.
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        invokeGui(console /* no args */);

        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(any(), any(), any(), any()),
                never());
    }
}
