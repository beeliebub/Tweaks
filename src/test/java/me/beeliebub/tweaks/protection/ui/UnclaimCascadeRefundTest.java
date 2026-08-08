package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

class UnclaimCascadeRefundTest {

    private static final UUID OFFLINE_OWNER = UUID.fromString("deadbeef-dead-dead-dead-deaddeadbeef");
    private static ServerMock server;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private static Region region(String id, UUID owner, String parent, int cost) {
        return new Region(id, owner, List.of(), Map.of(), Map.of(), parent,
                null, null, List.of(), Map.of(), cost);
    }

    @Test
    void cascadeRefundsEveryOnlineOwner() {
        PlayerMock owner = server.addPlayer("CascadeOwner");
        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        Region parent = region("parent", owner.getUniqueId(), null, 40);
        Region child = region("child", owner.getUniqueId(), "parent", 25);
        protection.regions().put("parent", parent);
        protection.regions().put("child", child);

        ProtectionManager.UnclaimOutcome outcome =
                UnclaimSubcommand.unclaimWithRefund(owner, protection, parent);

        assertEquals(ProtectionManager.UnclaimResult.OK, outcome.result());
        assertEquals(65, InventoryUtil.getResourceRupeeBalance(owner));
        assertFalse(protection.regions().containsKey("parent"));
        assertFalse(protection.regions().containsKey("child"));
    }

    @Test
    void offlineDescendantRefundIsSkippedAndWarned() {
        PlayerMock owner = server.addPlayer("OnlineParentOwner");
        Logger logger = mock(Logger.class);
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(logger);
        ProtectionManager protection = new ProtectionManager(plugin);
        Region parent = region("parent-offline", owner.getUniqueId(), null, 40);
        Region child = region("child-offline", OFFLINE_OWNER, "parent-offline", 25);
        protection.regions().put("parent-offline", parent);
        protection.regions().put("child-offline", child);

        UnclaimSubcommand.unclaimWithRefund(owner, protection, parent);

        assertEquals(40, InventoryUtil.getResourceRupeeBalance(owner));
        verify(logger, atLeastOnce()).log(eq(Level.WARNING), contains("child-offline"));
    }

    @Test
    void persistenceFailureDoesNotRefundOrEvictRegion() throws IOException {
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        ProtectionManager protection = new ProtectionManager(plugin);
        Region region = region("disk-failure", UUID.randomUUID(), null, 89);
        protection.regions().put("disk-failure", region);
        RegionWriter writer = mock(RegionWriter.class);
        doThrow(new IOException("disk unavailable")).when(writer).archive(region);
        protection.setWriter(writer);
        CommandSender sender = mock(CommandSender.class);

        try (MockedStatic<InventoryUtil> currency = mockStatic(InventoryUtil.class)) {
            ProtectionManager.UnclaimOutcome outcome =
                    UnclaimSubcommand.unclaimWithRefund(sender, protection, region);

            assertEquals(ProtectionManager.UnclaimResult.PERSISTENCE_FAILED, outcome.result());
            currency.verifyNoInteractions();
        }
        assertTrue(protection.regions().containsKey("disk-failure"));
    }
}
