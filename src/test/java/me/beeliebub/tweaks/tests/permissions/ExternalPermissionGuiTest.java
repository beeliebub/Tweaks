package me.beeliebub.tweaks.tests.permissions;

import io.papermc.paper.dialog.Dialog;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionCommand;
import me.beeliebub.tweaks.permissions.PermissionGUI;
import me.beeliebub.tweaks.permissions.PermissionGroup;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExternalPermissionGuiTest {

    private ServerMock server;
    private Tweaks plugin;
    private PermissionManager manager;
    private Path externalDirectory;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = plugin.getPermissionManager();
        externalDirectory = plugin.getDataFolder().toPath().resolve("external-permissions");
        Files.createDirectories(externalDirectory);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void externalCategoryRendersForGroupsAndUsers() throws Exception {
        Files.writeString(externalDirectory.resolve("tools.txt"),
                "# name: Other Plugin\nOTHER.ADMIN | External administrator permission\n");
        PermissionGroup group = new PermissionGroup("staff");
        manager.getGroups().put("staff", group);
        UUID target = UUID.randomUUID();
        Player viewer = mock(Player.class);

        assertDoesNotThrow(() -> PermissionGUI.openGroupPermCategories(viewer, manager, "staff"));
        assertDoesNotThrow(() -> PermissionGUI.openGroupPerms(viewer, manager, "staff", "external:tools", 0));
        assertDoesNotThrow(() -> PermissionGUI.openUserPermCategories(viewer, manager, target));
        assertDoesNotThrow(() -> PermissionGUI.openUserPerms(viewer, manager, target, "external:tools", 0));

        verify(viewer, org.mockito.Mockito.times(4)).showDialog(any(Dialog.class));
    }

    @Test
    void tabCompletionIncludesExternalNodesAlongsideBuiltIns() throws Exception {
        Files.writeString(externalDirectory.resolve("other.txt"), "Plugin.Mixed.Node\n");
        PermissionCommand command = new PermissionCommand(plugin, manager);
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, Permissions.ADMIN_PERMISSIONS, true);

        List<String> suggestions = command.onTabComplete(admin, mock(Command.class), "tprm",
                new String[] {"group", "default", "addperm", "plugin."});

        assertTrue(suggestions.contains("plugin.mixed.node"));
    }
}
