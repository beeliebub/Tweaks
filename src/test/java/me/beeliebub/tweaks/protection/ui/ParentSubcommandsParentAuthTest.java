package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentSubcommandsParentAuthTest {

    private static final UUID CHILD_OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PARENT_MANAGER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ProtectionManager protection;
    private RegionCommandContext context;

    @BeforeEach
    void setUp() {
        protection = spy(new ProtectionManager(mock(Tweaks.class)));
        protection.regions().put("child", new Region("child", CHILD_OWNER, List.of(), Map.of()));
        protection.regions().put("parent", new Region("parent", PARENT_OWNER, List.of(), Map.of()));
        protection.addManager("parent", PARENT_MANAGER);
        context = new RegionCommandContext(mock(Tweaks.class), protection,
                mock(RegionSelectionManager.class));
    }

    private static Player player(UUID id, boolean admin) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(admin);
        when(player.hasPermission(anyString())).thenReturn(false);
        if (admin) when(player.hasPermission(Permissions.PROTECTION_ADMIN)).thenReturn(true);
        return player;
    }

    @Test
    void childOwnerWhoDoesNotOwnParentIsRejected() {
        Player sender = player(CHILD_OWNER, false);

        new ParentSubcommands.SetParent().execute(context, sender, new String[] {"child", "parent"});

        verify(sender).sendMessage(Messages.PROTECTION.text(Text.PARENT_TARGET_AUTH));
        verify(protection, never()).setParent(anyString(), anyString(), anyBoolean());
    }

    @Test
    void managerOfParentIsStillRejected() {
        protection.addManager("parent", CHILD_OWNER);
        Player sender = player(CHILD_OWNER, false);

        new ParentSubcommands.SetParent().execute(context, sender, new String[] {"child", "parent"});

        verify(sender).sendMessage(Messages.PROTECTION.text(Text.PARENT_TARGET_AUTH));
        verify(protection, never()).setParent(anyString(), anyString(), anyBoolean());
    }

    @Test
    void adminMayNestAcrossOwnersWithOverride() {
        Player sender = player(PARENT_MANAGER, true);

        new ParentSubcommands.SetParent().execute(context, sender, new String[] {"child", "parent"});

        verify(protection).setParent("child", "parent", true);
    }

    @Test
    void nonAdminOwnerOfBothRegionsUsesStrictParentApi() {
        Region sharedParent = new Region("parent", CHILD_OWNER, List.of(), Map.of());
        protection.regions().put("parent", sharedParent);
        Player sender = player(CHILD_OWNER, false);

        new ParentSubcommands.SetParent().execute(context, sender, new String[] {"child", "parent"});

        verify(protection).setParent("child", "parent", false);
        assertEquals("parent", protection.regions().get("child").parentId());
    }
}
