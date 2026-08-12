package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BalanceCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager em;
    private BalanceCommand cmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        em = plugin.getEconomyManager();
        cmd = new BalanceCommand(em);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // addPlayer() triggers the registered EconomyListener, granting the first-login daily
    // reward. We drain all queued messages after adding a player to start each test clean.

    @Test
    void ownBalanceNoArgShowsBalance() {
        PlayerMock player = server.addPlayer();
        // Drain join messages (daily reward notification + any join message).
        drainMessages(player);

        cmd.onCommand(player, bukkitCmd, "balance", new String[0]);

        MessageAssert.assertMessageSent(player, "Your balance:");
    }

    @Test
    void ownBalanceShowsFormattedAmount() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        em.setBalance(uuid, 1234L);
        cmd.onCommand(player, bukkitCmd, "balance", new String[0]);

        // Should contain the formatted $1,234
        MessageAssert.assertMessageSent(player, "$1,234");
    }

    @Test
    void hideTogglesTrueFromFalse() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        assertFalse(em.isBalanceHidden(uuid), "balance should not be hidden initially");

        cmd.onCommand(player, bukkitCmd, "balance", new String[]{"hide"});

        assertTrue(em.isBalanceHidden(uuid), "balance should be hidden after /balance hide");
        MessageAssert.assertMessageSent(player, "hidden");
    }

    @Test
    void hideTogglesBackToVisible() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        drainMessages(player);

        // Set hidden first, then toggle back.
        em.setBalanceHidden(uuid, true);
        cmd.onCommand(player, bukkitCmd, "balance", new String[]{"hide"});

        assertFalse(em.isBalanceHidden(uuid), "balance should be visible after second /balance hide");
        MessageAssert.assertMessageSent(player, "visible");
    }

    @Test
    void adminAddChangesBalance() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        UUID targetId = target.getUniqueId();
        drainMessages(admin);
        drainMessages(target);

        admin.addAttachment(plugin, Permissions.ADMIN_BALANCE, true);
        em.setBalance(targetId, 0L);

        cmd.onCommand(admin, bukkitCmd, "balance",
                new String[]{"add", target.getName(), "500"});

        assertEquals(500L, em.getBalance(targetId),
                "balance should increase by the added amount");
        MessageAssert.assertMessageSent(admin, target.getName());
    }

    @Test
    void adminSetChangesBalance() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        UUID targetId = target.getUniqueId();
        drainMessages(admin);
        drainMessages(target);

        admin.addAttachment(plugin, Permissions.ADMIN_BALANCE, true);

        cmd.onCommand(admin, bukkitCmd, "balance",
                new String[]{"set", target.getName(), "999"});

        assertEquals(999L, em.getBalance(targetId),
                "balance should be exactly the set amount");
    }

    @Test
    void adminRemoveChangesBalance() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        UUID targetId = target.getUniqueId();
        drainMessages(admin);
        drainMessages(target);

        admin.addAttachment(plugin, Permissions.ADMIN_BALANCE, true);
        em.setBalance(targetId, 300L);

        cmd.onCommand(admin, bukkitCmd, "balance",
                new String[]{"remove", target.getName(), "100"});

        assertEquals(200L, em.getBalance(targetId),
                "balance should decrease by the removed amount");
    }

    @Test
    void nonAdminCannotViewOtherPlayerBalance() {
        PlayerMock noPerms = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drainMessages(noPerms);
        drainMessages(target);

        // Ensure no permission (non-op, no explicit attachment)
        noPerms.setOp(false);

        boolean result = cmd.onCommand(noPerms, bukkitCmd, "balance",
                new String[]{target.getName()});

        // Command must handle the request (return true) and not leak balance data.
        assertTrue(result, "onCommand should return true even on permission denial");
        MessageAssert.assertMessageSent(noPerms, "permission");
    }

    @Test
    void nonAdminCannotUseAdminMutations() {
        PlayerMock noPerms = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drainMessages(noPerms);
        drainMessages(target);

        noPerms.setOp(false);
        UUID targetId = target.getUniqueId();
        em.setBalance(targetId, 100L);

        cmd.onCommand(noPerms, bukkitCmd, "balance",
                new String[]{"add", target.getName(), "9999"});

        // Balance must be unchanged.
        assertEquals(100L, em.getBalance(targetId),
                "balance must not change when sender lacks ADMIN_BALANCE");
        MessageAssert.assertMessageSent(noPerms, "permission");
    }

    @Test
    void invalidAmountShowsError() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drainMessages(admin);
        drainMessages(target);

        admin.addAttachment(plugin, Permissions.ADMIN_BALANCE, true);

        cmd.onCommand(admin, bukkitCmd, "balance",
                new String[]{"set", target.getName(), "notanumber"});

        MessageAssert.assertMessageSent(admin, "Invalid amount");
    }

    @Test
    void adminAddReportsBalanceOutsideSupportedRange() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drainMessages(admin);
        drainMessages(target);

        admin.addAttachment(plugin, Permissions.ADMIN_BALANCE, true);
        em.setBalance(target.getUniqueId(), EconomyManager.MAX_BALANCE);

        cmd.onCommand(admin, bukkitCmd, "balance",
                new String[]{"add", target.getName(), "1"});

        assertEquals(EconomyManager.MAX_BALANCE, em.getBalance(target.getUniqueId()));
        MessageAssert.assertMessageSent(admin, "outside the supported range");
    }

    @Test
    void decimalAdminAmountIsRejected() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drainMessages(admin);
        drainMessages(target);

        admin.addAttachment(plugin, Permissions.ADMIN_BALANCE, true);
        em.setBalance(target.getUniqueId(), 0L);
        cmd.onCommand(admin, bukkitCmd, "balance",
                new String[]{"set", target.getName(), "1.5"});

        assertEquals(0L, em.getBalance(target.getUniqueId()));
        MessageAssert.assertMessageSent(admin, "Invalid amount");
    }

    // ---- Helpers ------------------------------------------------------------

    /** Drain all pending messages from the player's message queue. */
    private void drainMessages(PlayerMock player) {
        //noinspection StatementWithEmptyBody
        while (player.nextComponentMessage() != null) { /* discard */ }
    }
}
