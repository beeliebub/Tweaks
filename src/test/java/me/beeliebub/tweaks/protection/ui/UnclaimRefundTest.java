package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for {@code /region unclaim}'s currency-refund flow — moved into the
 * production package (was {@code tests.protection.ClaimCostTest}'s tests 6-8, reflecting into
 * {@code ProtectionCommand.handleUnclaim}) since that logic is now split between
 * {@link UnclaimSubcommand#execute} (parsing/authorization) and
 * {@link UnclaimSubcommand#unclaimWithRefund} (the actual unclaim + refund — now also shared
 * by {@link RegionGUI}'s unclaim-confirmation dialog, which previously issued no refund at all).
 *
 * <p>These tests call {@code unclaimWithRefund} directly to isolate refund behavior from
 * authorization (already covered by the owner-only-gate test below and by
 * {@code RegionGUIManagerAuthTest} for the GUI side).
 */
class UnclaimRefundTest {

    private static ServerMock server;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    @Test
    void unclaimWithRefund_refundsCostToOnlineOwner() {
        PlayerMock owner = server.addPlayer("OwnerPlayer");

        Region region = new Region(
                "costly", owner.getUniqueId(), List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                89
        );

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("costly", region);

        assertEquals(0, InventoryUtil.getResourceRupeeBalance(owner),
                "player starts with zero Resource Rupees");

        UnclaimSubcommand.unclaimWithRefund(owner, protection, region);

        int balance = InventoryUtil.getResourceRupeeBalance(owner);
        assertTrue(balance >= 89,
                "owner must receive a refund >= 89 Resource Rupees after unclaim; got " + balance);

        assertFalse(protection.regions().containsKey("costly"),
                "region must be evicted from cache after successful unclaim");
    }

    @Test
    void unclaimWithRefund_skipsRefundForOfflineOwner() {
        // Use a UUID that is not backed by any online MockBukkit player.
        UUID offlineOwnerUuid = UUID.fromString("deadbeef-dead-dead-dead-deaddeadbeef");

        Region region = new Region(
                "ghost", offlineOwnerUuid, List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                89
        );

        // unclaimWithRefund logs via pm.plugin().getLogger() when a refund is owed but
        // unresolvable (see UnclaimSubcommand) — stub a real Logger so that call doesn't NPE
        // on Mockito's default null-returning stub.
        Tweaks pluginMock = mock(Tweaks.class);
        when(pluginMock.getLogger()).thenReturn(Logger.getLogger("test"));
        ProtectionManager protection = new ProtectionManager(pluginMock);
        protection.regions().put("ghost", region);

        // Console-style sender: not the owner, and resolveRefundRecipient falls back to
        // Bukkit.getPlayer(ownerId), which is null since that player has never joined.
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        UnclaimSubcommand.unclaimWithRefund(console, protection, region);

        assertFalse(protection.regions().containsKey("ghost"),
                "ghost region must be removed from cache even when owner is offline");

        // No rupees should have been added to any online player — the offline owner can
        // never be resolved to a Player, so addResourceRupees was never called.
        for (PlayerMock p : server.getOnlinePlayers()) {
            if (p.getUniqueId().equals(offlineOwnerUuid)) {
                fail("Unexpectedly found the 'offline' owner as online — test setup error");
            }
        }
    }

    @Test
    void unclaimWithRefund_zeroCostRegion_noRefundAttempted() {
        // A free/admin/legacy region has cost == 0; unclaiming must not add rupees to anyone.
        PlayerMock owner = server.addPlayer("FreeOwner");

        Region region = new Region(
                "freeregion", owner.getUniqueId(), List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                0
        );

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("freeregion", region);

        int balanceBefore = InventoryUtil.getResourceRupeeBalance(owner);
        assertEquals(0, balanceBefore, "owner starts with zero balance");

        UnclaimSubcommand.unclaimWithRefund(owner, protection, region);

        int balanceAfter = InventoryUtil.getResourceRupeeBalance(owner);
        assertEquals(0, balanceAfter,
                "unclaiming a zero-cost region must not add any Resource Rupees to the owner");

        assertFalse(protection.regions().containsKey("freeregion"),
                "zero-cost region must still be evicted from cache on unclaim");
    }

    @Test
    void execute_deniesNonOwnerManager_beforeReachingRefundLogic() {
        // unclaimWithRefund itself performs no authorization (callers are expected to have
        // already checked) — the actual owner-only gate lives in execute(). A plain manager
        // (not owner, not admin) must be refused there, before ProtectionManager.unclaim is
        // ever called.
        UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");

        // Non-Player senders (console) always pass isOwnerOrAdmin, so this needs a real
        // manager-role Player to exercise the denial path.
        PlayerMock manager = server.addPlayer("ManagerPlayer");

        Region region = new Region(
                "guarded", ownerId, List.of(),
                java.util.Map.of(), java.util.Map.of(),
                null, null, null,
                List.of(), java.util.Map.of(),
                50
        ).addManager(manager.getUniqueId());

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("guarded", region);
        RegionCommandContext ctx = new RegionCommandContext(
                mock(Tweaks.class), protection, mock(RegionSelectionManager.class));

        new UnclaimSubcommand().execute(ctx, manager, new String[]{"guarded"});

        assertTrue(protection.regions().containsKey("guarded"),
                "a manager must not be able to unclaim — region must survive the attempt");
        assertEquals(0, InventoryUtil.getResourceRupeeBalance(manager),
                "denied unclaim attempt must not issue any refund");
    }
}
