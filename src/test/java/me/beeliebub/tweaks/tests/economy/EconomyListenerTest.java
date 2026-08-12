package me.beeliebub.tweaks.tests.economy;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyListener;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyListenerTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // Helper: fire the join event manually via the listener under test.
    private EconomyListener buildListener() {
        EconomyManager em = plugin.getEconomyManager();
        RankManager rm = plugin.getRankManager();
        return new EconomyListener(plugin, em, rm);
    }

    @Test
    void firstLoginGrantsBaseRewardAndSetsStreak() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        EconomyListener listener = buildListener();
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        EconomyManager em = plugin.getEconomyManager();

        // Day-1 multiplier = 1.0, rank bonus = 0.0 → reward = 100.0
        assertEquals(100L, em.getBalance(uuid),
                "balance should equal the base reward on first login");
        assertEquals(1, em.getLoginStreak(uuid),
                "streak should be 1 after first login");
        assertNotEquals(0L, em.getLastLogin(uuid),
                "last_login should be recorded after first login");
    }

    @Test
    void secondLoginWithin24HoursGrantsNoReward() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyListener listener = buildListener();
        EconomyManager em = plugin.getEconomyManager();

        // First login.
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        long balanceAfterFirst = em.getBalance(uuid);

        // Immediately fire a second join — still within 24 hours.
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(balanceAfterFirst, em.getBalance(uuid),
                "no second reward should be granted within 24 hours");
        assertEquals(1, em.getLoginStreak(uuid),
                "streak should not increment on same-day login");
    }

    @Test
    void loginAfter48HoursResetsStreakToOne() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyListener listener = buildListener();
        EconomyManager em = plugin.getEconomyManager();

        // addPlayer() fires the registered EconomyListener (first-login reward already applied).
        // Pre-seed state: pretend the last login was 72 hours ago with a streak of 5.
        // Zero the balance so the assertion reflects only the reward from our manual fire.
        long past = System.currentTimeMillis() - (72L * 60 * 60 * 1000);
        em.setLastLogin(uuid, past);
        em.setLoginStreak(uuid, 5);
        em.setBalance(uuid, 0L);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(1, em.getLoginStreak(uuid),
                "streak must reset to 1 after more than 48 hours of absence");
        // Day-1 multiplier → 100.0
        assertEquals(100L, em.getBalance(uuid),
                "reward should be the base amount when streak resets");
    }

    @Test
    void consecutiveDayIncrementsStreak() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyListener listener = buildListener();
        EconomyManager em = plugin.getEconomyManager();

        // addPlayer() fires the registered EconomyListener (first-login reward already applied).
        // Pre-seed: last login 25 hours ago, streak 3. Zero balance for clean assertion.
        long yesterday = System.currentTimeMillis() - (25L * 60 * 60 * 1000);
        em.setLastLogin(uuid, yesterday);
        em.setLoginStreak(uuid, 3);
        em.setBalance(uuid, 0L);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(4, em.getLoginStreak(uuid),
                "streak should increment by 1 for a consecutive-day login");
        // Day-4 multiplier = 1.3 → reward = 130.0
        assertEquals(130L, em.getBalance(uuid),
                "reward should reflect the day-4 streak multiplier");
    }

    @Test
    void streakCapsAtSeven() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyListener listener = buildListener();
        EconomyManager em = plugin.getEconomyManager();

        // Pre-seed streak at 7 with a 25-hour gap.
        long yesterday = System.currentTimeMillis() - (25L * 60 * 60 * 1000);
        em.setLastLogin(uuid, yesterday);
        em.setLoginStreak(uuid, 7);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(7, em.getLoginStreak(uuid),
                "streak must not exceed 7");
    }

    @Test
    void dailyRewardMessageUsesDollarFormatting() {
        PlayerMock player = server.addPlayer();
        EconomyListener listener = buildListener();

        // First login triggers reward
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // Base reward is 100. formatBalance(100) should be "$100".
        // Expected message part: "+$100"
        me.beeliebub.tweaks.tests.MessageAssert.assertMessageSent(player, "Daily reward: +$100 (Day 1 streak)");
    }

    @Test
    void fractionalDailyRewardIsFlooredBeforeCredit() {
        plugin.getConfig().set("economy.daily-reward-base", 100.9D);
        PlayerMock player = server.addPlayer();

        assertEquals(100L, plugin.getEconomyManager().getBalance(player.getUniqueId()));
    }

    @Test
    void nonFiniteDailyRewardIsSkippedWithoutUpdatingLoginState() {
        plugin.getConfig().set("economy.streak-multipliers.1", Double.POSITIVE_INFINITY);
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        EconomyManager em = plugin.getEconomyManager();

        assertEquals(0L, em.getBalance(uuid));
        assertEquals(0, em.getLoginStreak(uuid));
        assertEquals(0L, em.getLastLogin(uuid));
    }
}
