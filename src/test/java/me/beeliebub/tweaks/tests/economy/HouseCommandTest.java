package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HouseCommand;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HouseCommandTest {

    @TempDir
    File dataFolder;

    private ServerMock server;
    private Tweaks plugin;
    private HouseAccount house;
    private HousePaymentService housePaymentService;
    private HouseCommand command;
    private final Command bukkitCommand = mock(Command.class);

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        JavaPlugin housePlugin = mock(JavaPlugin.class);
        when(housePlugin.getDataFolder()).thenReturn(dataFolder);
        when(housePlugin.getLogger()).thenReturn(Logger.getLogger("HouseCommandTest-" + System.nanoTime()));
        house = new HouseAccount(housePlugin);
        house.flush().get();
        housePaymentService = new HousePaymentService(housePlugin, house, plugin.getEconomyManager());
        housePaymentService.replayPendingPayments().get();
        // Scheduler-bound: HouseCommand hops back onto Bukkit's scheduler to deliver a /house pay
        // ack, so it needs the real MockBukkit-registered plugin, not the standalone housePlugin
        // mock house/housePaymentService are otherwise wired to.
        command = new HouseCommand(plugin, house, housePaymentService);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // Drains every step of a /house pay's async chain (YamlStore's per-key queues, both managers'
    // compute/write hops) and then runs the main-thread ack HouseCommand schedules at the end.
    private void drainHousePayment() throws InterruptedException {
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
        server.getScheduler().performOneTick();
    }

    @Test
    void permissionGatePreventsHouseMutation() {
        PlayerMock player = server.addPlayer();
        player.setOp(false);

        command.onCommand(player, bukkitCommand, "house", new String[]{"add", "100"});

        assertEquals(0L, house.balance());
        MessageAssert.assertMessageSent(player, "permission");
    }

    @Test
    void payDebitsHouseAndCreditsOnlinePlayerExactlyOnce() throws Exception {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        admin.addAttachment(plugin, Permissions.HOUSE_ADMIN, true);
        plugin.getEconomyManager().setBalance(target.getUniqueId(), 0.0D);
        house.set(500L);

        command.onCommand(admin, bukkitCommand, "house", new String[]{"pay", target.getName(), "125"});
        drainHousePayment();

        assertEquals(375L, house.balance());
        assertEquals(125.0D, plugin.getEconomyManager().getBalance(target.getUniqueId()));
        MessageAssert.assertMessageSent(admin, "Paid $125");
    }

    @Test
    void removeRefusesToOverdrawHouse() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, Permissions.HOUSE_ADMIN, true);
        house.set(50L);

        command.onCommand(admin, bukkitCommand, "house", new String[]{"remove", "51"});

        assertEquals(50L, house.balance());
        MessageAssert.assertMessageSent(admin, "does not have enough");
    }
}
