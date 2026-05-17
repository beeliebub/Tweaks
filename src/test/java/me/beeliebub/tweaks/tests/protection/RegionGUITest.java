package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.ProtectionCommand;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import me.beeliebub.tweaks.protection.RegionGUI;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

// Permission-gate coverage for the /region gui subcommand. The dialog itself
// cannot be opened under MockBukkit (Paper's Dialog API requires a service
// MockBukkit does not provide — see PermissionGUITest for the same caveat),
// so these tests stub `RegionGUI.openRegionHub` via Mockito's static mocking
// and verify the COMMAND-layer guard: owners, managers, and admins reach the
// open call; everyone else is short-circuited with a denial message.
//
// The handler is reached via reflection — `handleGui` is a private dispatch
// method on ProtectionCommand. Driving it directly bypasses CommandExecutor
// plumbing (the public `onCommand` would require us to also mock the Bukkit
// Command object) while still exercising the real authorization logic.
class RegionGUITest {

    private static final UUID OWNER     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID MANAGER   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID STRANGER  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
    private static final UUID ADMIN     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4");

    private static final String REGION = "home";

    private static Method handleGui;

    @BeforeAll
    static void setUpAll() throws NoSuchMethodException {
        MockBukkit.mock();
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
        handleGui = ProtectionCommand.class.getDeclaredMethod(
                "handleGui", org.bukkit.command.CommandSender.class, String[].class);
        handleGui.setAccessible(true);
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private ProtectionManager protection;
    private ProtectionCommand command;
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
        // by the ProtectionCommand constructor.
        command = new ProtectionCommand(
                mock(Tweaks.class), protection, mock(RegionSelectionManager.class));

        regionGuiStatic = mockStatic(RegionGUI.class);
    }

    @AfterEach
    void tearDown() {
        regionGuiStatic.close();
    }

    private void invokeGui(org.bukkit.command.CommandSender sender, String... args) throws Exception {
        handleGui.invoke(command, sender, args);
    }

    private Player playerWith(UUID uuid, boolean admin) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(admin);
        return p;
    }

    @Test
    void ownerCanOpenRegionGui() throws Exception {
        Player owner = playerWith(OWNER, false);

        invokeGui(owner, REGION);

        Region resolved = protection.regions().get(REGION);
        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(eq(owner), eq(resolved), eq(protection)),
                times(1));
    }

    @Test
    void managerCanOpenRegionGui() throws Exception {
        Player manager = playerWith(MANAGER, false);

        invokeGui(manager, REGION);

        Region resolved = protection.regions().get(REGION);
        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(eq(manager), eq(resolved), eq(protection)),
                times(1));
    }

    @Test
    void adminCanOpenAnyRegionGui() throws Exception {
        // No ownership, no manager role — admin perm alone must suffice.
        Player admin = playerWith(ADMIN, true);

        invokeGui(admin, REGION);

        Region resolved = protection.regions().get(REGION);
        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(eq(admin), eq(resolved), eq(protection)),
                times(1));
    }

    @Test
    void strangerCannotOpenRegionGui() throws Exception {
        Player stranger = playerWith(STRANGER, false);

        invokeGui(stranger, REGION);

        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(any(), any(), any()),
                never());
    }

    @Test
    void unknownRegionDoesNotOpenGui() throws Exception {
        Player owner = playerWith(OWNER, false);

        invokeGui(owner, "ghost_region");

        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(any(), any(), any()),
                never());
    }

    @Test
    void consoleWithoutRegionNameDoesNotOpenGui() throws Exception {
        // Console can pass isOwnerManagerOrAdmin (non-Player → true) but the
        // no-args branch refuses with a "must supply a region name" message
        // because regionsAt() needs a player Location.
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        invokeGui(console /* no args */);

        regionGuiStatic.verify(
                () -> RegionGUI.openRegionHub(any(), any(), any()),
                never());
    }
}
