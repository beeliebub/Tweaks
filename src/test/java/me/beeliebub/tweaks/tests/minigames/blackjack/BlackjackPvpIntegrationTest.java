package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener.MoneyButtonRef;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener.PvpSession;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener.PvpTableEntry;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackPvpGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

public class BlackjackPvpIntegrationTest {

    private ServerMock server;
    private Tweaks plugin;
    private BlackjackListener listener;
    private EconomyManager economy;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        listener = plugin.getBlackjackListener();
        economy = plugin.getEconomyManager();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Full lifecycle test for the three-round PvP pipeline (bead i0m7):
     * REGISTERING -> BETTING -> CONFIRMING_BETS -> ACTIVE.
     *
     * Round 1 (REGISTERING): both players press MIDDLE to claim their seat.
     * Round 2 (BETTING): wagers are set, then both press MIDDLE to lock stakes.
     * Round 3 (CONFIRMING_BETS): both press MIDDLE to confirm terms and deal.
     */
    @Test
    public void pvpSessionLifecycleTest() {
        World world = server.addSimpleWorld("test");
        Location center = new Location(world, 10.5, 64.0, 10.5);
        Location middleA = new Location(world, 10, 64, 8);
        Location middleB = new Location(world, 10, 64, 12);

        setupButton(middleA, BlockFace.NORTH);
        setupButton(middleB, BlockFace.SOUTH);

        PvpTableEntry entry = new PvpTableEntry(center, null,
                middleA, BlockFace.NORTH,
                middleB, BlockFace.SOUTH);

        listener.registerPvpButtonsForTable(entry);

        PlayerMock playerA = server.addPlayer("Alice");
        PlayerMock playerB = server.addPlayer("Bob");
        economy.setBalance(playerA.getUniqueId(), 1000);
        economy.setBalance(playerB.getUniqueId(), 1000);

        // --- Round 1: Registration (REGISTERING -> BETTING) ---
        click(playerA, middleA);  // A claims their seat
        click(playerB, middleB);  // B claims their seat -> both registered -> BETTING

        String tableKey = BlackjackListener.blockKey(middleA);
        PvpSession session = listener.pvpSessions().get(tableKey);
        assertNotNull(session, "Session must be created once both players register");
        assertEquals(BlackjackPvpGame.State.BETTING, session.game.state(),
                "State must be BETTING after both players register");

        // --- Set Stakes (during BETTING phase) ---
        listener.promptPvpWager(playerA, new MoneyButtonRef(entry, 0));
        listener.applyWager(playerA, "100");
        assertEquals(100, session.moneyStakeA, "Alice wager fail");

        listener.promptPvpWager(playerB, new MoneyButtonRef(entry, 1));
        listener.applyWager(playerB, "200");
        assertEquals(200, session.moneyStakeB, "Bob wager fail");

        // --- Round 2: Lock Stakes (BETTING -> CONFIRMING_BETS) ---
        click(playerA, middleA);  // A locks stake (readyA=true)
        click(playerB, middleB);  // B locks stake -> both ready -> CONFIRMING_BETS

        assertEquals(BlackjackPvpGame.State.CONFIRMING_BETS, session.game.state(),
                "State must be CONFIRMING_BETS after both players lock stakes");

        // --- Round 3: Confirm & Deal (CONFIRMING_BETS -> ACTIVE) ---
        click(playerA, middleA);  // A confirms
        click(playerB, middleB);  // B confirms -> both confirmed -> ACTIVE (cards dealt)

        assertEquals(BlackjackPvpGame.State.ACTIVE, session.game.state(),
                "State must be ACTIVE after both players confirm");
        assertEquals(2, session.game.handA().size(), "Hand A must have 2 cards after deal");
        assertEquals(2, session.game.handB().size(), "Hand B must have 2 cards after deal");
    }

    private void setupButton(Location loc, BlockFace facing) {
        loc.getBlock().setType(Material.OAK_BUTTON);
        if (loc.getBlock().getBlockData() instanceof Switch sw) {
            sw.setFacing(facing);
            loc.getBlock().setBlockData(sw);
        }
    }

    private void click(Player player, Location loc) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), loc.getBlock(), BlockFace.UP);
        listener.onButtonClick(event);
    }
}
