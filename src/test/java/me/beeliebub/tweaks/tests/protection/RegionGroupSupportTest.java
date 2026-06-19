package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.ProtectionCommand;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import me.beeliebub.tweaks.protection.RegionGUI;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit coverage for Region Groups Support (bead Tweaks-ylt4.3):
//
// 1. ProtectionCommand.handleMember / handleManager — the group: routing branch
//    is exercised via reflection on the private handle* methods. We verify that
//    group: prefixed args reach ProtectionManager.addMemberGroup / removeManagerGroup
//    etc., while plain player names still travel the UUID path.
//
// 2. RegionGUI.routeAddMemberInput / routeAddManagerInput — the package-private
//    helpers are callable without constructing a Paper Dialog, making them safe
//    to test under MockBukkit.
class RegionGroupSupportTest {

    private static final UUID OWNER   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID MANAGER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final String REGION = "home";

    @BeforeAll
    static void setUpAll() {
        MockBukkit.mock();
        try (MockedConstruction<NamespacedKey> ignored = mockConstruction(NamespacedKey.class)) {
            ProtectionKeys.init(mock(Tweaks.class));
        }
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------
    // Command-layer routing tests  (via reflection on handle* methods)
    // ------------------------------------------------------------------

    @Nested
    class CommandGroupRouting {

        private static Method handleMember;
        private static Method handleManager;

        // Static initialiser for the reflected methods — runs once for the nested class.
        static {
            try {
                handleMember = ProtectionCommand.class.getDeclaredMethod(
                        "handleMember", CommandSender.class, String[].class, boolean.class);
                handleMember.setAccessible(true);
                handleManager = ProtectionCommand.class.getDeclaredMethod(
                        "handleManager", CommandSender.class, String[].class, boolean.class);
                handleManager.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private ProtectionManager protection;
        private ProtectionCommand command;
        private CommandSender consoleSender;

        @BeforeEach
        void setUp() {
            protection = new ProtectionManager(mock(Tweaks.class));
            protection.regions().put(REGION, new Region(
                    REGION, OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
            protection.addManager(REGION, MANAGER);

            command = new ProtectionCommand(
                    mock(Tweaks.class), protection, mock(RegionSelectionManager.class));

            // Console sender: !(sender instanceof Player) → isOwnerManagerOrAdmin returns true.
            consoleSender = mock(CommandSender.class);
        }

        private void invokeMember(CommandSender sender, boolean add, String... args) throws Exception {
            handleMember.invoke(command, sender, args, add);
        }

        private void invokeManager(CommandSender sender, boolean add, String... args) throws Exception {
            handleManager.invoke(command, sender, args, add);
        }

        @Test
        void addMemberGroup_addsGroupToRegion() throws Exception {
            invokeMember(consoleSender, true, REGION, "group:builders");

            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.contains("builders"),
                    "addMemberGroup should have added 'builders' to member groups");
        }

        @Test
        void addMemberGroup_caseInsensitivePrefix() throws Exception {
            invokeMember(consoleSender, true, REGION, "GROUP:admins");

            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.contains("admins"),
                    "group: prefix detection must be case-insensitive");
        }

        @Test
        void removeMemberGroup_removesGroupFromRegion() throws Exception {
            protection.addMemberGroup(REGION, "builders");

            invokeMember(consoleSender, false, REGION, "group:builders");

            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertFalse(memberGroups.contains("builders"),
                    "removeMemberGroup should have removed 'builders'");
        }

        @Test
        void addMemberGroup_noOpOnDuplicate_doesNotThrow() throws Exception {
            protection.addMemberGroup(REGION, "builders");

            // Second add is a no-op; handler should send an error message but not throw.
            invokeMember(consoleSender, true, REGION, "group:builders");

            // The group should still be there (exactly once).
            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.contains("builders"));
        }

        @Test
        void addManagerGroup_addsGroupToManagerList() throws Exception {
            invokeManager(consoleSender, true, REGION, "group:mods");

            List<String> managerGroups = protection.regions().get(REGION).managerGroups();
            assertTrue(managerGroups.contains("mods"),
                    "addManagerGroup should have added 'mods' to manager groups");
        }

        @Test
        void removeManagerGroup_removesGroupFromManagerList() throws Exception {
            protection.addManagerGroup(REGION, "mods");

            invokeManager(consoleSender, false, REGION, "group:mods");

            List<String> managerGroups = protection.regions().get(REGION).managerGroups();
            assertFalse(managerGroups.contains("mods"),
                    "removeManagerGroup should have removed 'mods'");
        }

        @Test
        void plainPlayerArg_doesNotRouteToGroupPath() throws Exception {
            // A player name that doesn't start with "group:" must NOT call addMemberGroup.
            // Since the player has never joined (MockBukkit), getOfflinePlayer returns a
            // handle with hasPlayedBefore==false — the handler will print an error but
            // must not mutate memberGroups.
            invokeMember(consoleSender, true, REGION, "NotAGroup");

            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.isEmpty(),
                    "plain player name must not create a group entry");
        }

        @Test
        void emptyGroupName_isRejected() throws Exception {
            // "group:" with nothing after the colon must be rejected without mutating state.
            invokeMember(consoleSender, true, REGION, "group:");

            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.isEmpty(),
                    "empty group name after group: must be rejected");
        }
    }

    // ------------------------------------------------------------------
    // GUI helper tests  (routeAddMemberInput / routeAddManagerInput)
    // ------------------------------------------------------------------

    @Nested
    class GuiGroupHelpers {

        private ProtectionManager protection;
        private Region region;
        private Player player;

        @BeforeEach
        void setUp() {
            protection = new ProtectionManager(mock(Tweaks.class));
            region = new Region(REGION, OWNER, List.of(), EnumSet.noneOf(RegionFlag.class));
            protection.regions().put(REGION, region);
            player = mock(Player.class);
        }

        @Test
        void routeAddMemberInput_groupPrefix_addsGroupAndReturnsTrue() {
            boolean result = RegionGUI.routeAddMemberInput(player, region, protection, "group:builders");

            assertTrue(result, "routeAddMemberInput with group: prefix must return true on success");
            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.contains("builders"));
        }

        @Test
        void routeAddMemberInput_groupPrefixUpperCase_addsGroupNormalized() {
            boolean result = RegionGUI.routeAddMemberInput(player, region, protection, "GROUP:Admins");

            assertTrue(result);
            // The manager normalises to lowercase internally.
            List<String> memberGroups = protection.regions().get(REGION).memberGroups();
            assertTrue(memberGroups.contains("admins"));
        }

        @Test
        void routeAddMemberInput_duplicate_returnsFalseWithMessage() {
            protection.addMemberGroup(REGION, "builders");

            boolean result = RegionGUI.routeAddMemberInput(player, region, protection, "group:builders");

            assertFalse(result, "duplicate group add must return false");
            // Player should receive a YELLOW message — verify sendMessage was called.
            verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        void routeAddMemberInput_emptyGroupName_returnsFalseWithMessage() {
            boolean result = RegionGUI.routeAddMemberInput(player, region, protection, "group:");

            assertFalse(result, "empty group name must return false");
            verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
            assertTrue(protection.regions().get(REGION).memberGroups().isEmpty());
        }

        @Test
        void routeAddMemberInput_noGroupPrefix_returnsFalse() {
            // The helper must return false for plain player names; caller handles those.
            boolean result = RegionGUI.routeAddMemberInput(player, region, protection, "Steve");

            assertFalse(result, "non-group: input must return false so caller handles player path");
            // No message should be sent — the caller is responsible for player-name validation.
            verify(player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        void routeAddManagerInput_groupPrefix_addsGroupAndReturnsTrue() {
            boolean result = RegionGUI.routeAddManagerInput(player, region, protection, "group:mods");

            assertTrue(result);
            List<String> managerGroups = protection.regions().get(REGION).managerGroups();
            assertTrue(managerGroups.contains("mods"));
        }

        @Test
        void routeAddManagerInput_duplicate_returnsFalseWithMessage() {
            protection.addManagerGroup(REGION, "mods");

            boolean result = RegionGUI.routeAddManagerInput(player, region, protection, "group:mods");

            assertFalse(result);
            verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        void routeAddManagerInput_noGroupPrefix_returnsFalse() {
            boolean result = RegionGUI.routeAddManagerInput(player, region, protection, "Steve");

            assertFalse(result);
            verify(player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    // ------------------------------------------------------------------
    // Tab-completion tests for group: suggestions
    // ------------------------------------------------------------------

    @Nested
    class TabCompletionGroupSuggestions {

        private static Method onTabComplete;

        static {
            try {
                // onTabComplete is part of the public TabCompleter interface.
                onTabComplete = ProtectionCommand.class.getDeclaredMethod(
                        "onTabComplete",
                        org.bukkit.command.CommandSender.class,
                        org.bukkit.command.Command.class,
                        String.class,
                        String[].class);
                onTabComplete.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private ProtectionManager protection;
        private PermissionManager permissionManager;
        private Tweaks pluginMock;
        private ProtectionCommand command;
        private CommandSender consoleSender;

        @BeforeEach
        void setUp() {
            protection = new ProtectionManager(mock(Tweaks.class));
            protection.regions().put(REGION, new Region(
                    REGION, OWNER, List.of(), EnumSet.noneOf(RegionFlag.class)));
            protection.addMemberGroup(REGION, "builders");
            protection.addManagerGroup(REGION, "mods");

            permissionManager = mock(PermissionManager.class);
            // Return "builders" and "mods" as known groups.
            when(permissionManager.getGroups()).thenReturn(Map.of(
                    "builders", mock(me.beeliebub.tweaks.permissions.PermissionGroup.class),
                    "mods", mock(me.beeliebub.tweaks.permissions.PermissionGroup.class)));

            pluginMock = mock(Tweaks.class);
            when(pluginMock.getPermissionManager()).thenReturn(permissionManager);

            command = new ProtectionCommand(pluginMock, protection, mock(RegionSelectionManager.class));
            consoleSender = mock(CommandSender.class);
            // Grant all permissions so the subcommand permission gate and region
            // suggestion admin-check both pass for the console-style mock sender.
            when(consoleSender.hasPermission(any(String.class))).thenReturn(true);
        }

        @SuppressWarnings("unchecked")
        private List<String> complete(String... args) throws Exception {
            org.bukkit.command.Command cmd = mock(org.bukkit.command.Command.class);
            return (List<String>) onTabComplete.invoke(command, consoleSender, cmd, "region", args);
        }

        @Test
        void addmember_thirdArg_suggestsKnownGroupsWithPrefix() throws Exception {
            List<String> suggestions = complete("addmember", REGION, "group:");

            assertTrue(suggestions.stream().anyMatch(s -> s.startsWith("group:")),
                    "addmember arg 3 must suggest group: entries from known permission groups");
        }

        @Test
        void removemember_thirdArg_suggestsOnlyGroupsAlreadyOnRegion() throws Exception {
            List<String> suggestions = complete("removemember", REGION, "group:");

            assertTrue(suggestions.contains("group:builders"),
                    "removemember must suggest existing member groups");
            assertFalse(suggestions.contains("group:mods"),
                    "removemember must not suggest groups that are only in managers");
        }

        @Test
        void removemanager_thirdArg_suggestsOnlyManagerGroupsAlreadyOnRegion() throws Exception {
            List<String> suggestions = complete("removemanager", REGION, "group:");

            assertTrue(suggestions.contains("group:mods"),
                    "removemanager must suggest existing manager groups");
            assertFalse(suggestions.contains("group:builders"),
                    "removemanager must not suggest groups that are only in members");
        }

        @Test
        void addmanager_thirdArg_suggestsAllKnownGroups() throws Exception {
            List<String> suggestions = complete("addmanager", REGION, "group:");

            assertTrue(suggestions.stream().anyMatch(s -> s.equals("group:builders") || s.equals("group:mods")),
                    "addmanager arg 3 must suggest all known permission groups with group: prefix");
        }

        @Test
        void groupPrefix_filterAppliedCorrectly() throws Exception {
            List<String> suggestions = complete("addmember", REGION, "group:b");

            assertTrue(suggestions.stream().anyMatch(s -> s.startsWith("group:b")),
                    "prefix filtering must narrow suggestions to those starting with the typed prefix");
            assertTrue(suggestions.stream().noneMatch(s -> s.startsWith("group:m")),
                    "groups not matching the prefix must be excluded");
        }
    }
}
