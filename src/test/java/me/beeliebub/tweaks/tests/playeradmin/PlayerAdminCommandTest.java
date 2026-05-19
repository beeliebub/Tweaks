package me.beeliebub.tweaks.tests.playeradmin;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.playeradmin.PlayerAdminCommand;
import me.beeliebub.tweaks.playeradmin.PlayerAdminManager;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Smoke coverage for the dispatcher + manager. Drives each command label and verifies
// the visible side effect — gamemode change, potion effect, fly state, AFK state, nick PDC.
class PlayerAdminCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private PlayerAdminManager manager;
    private PlayerAdminCommand cmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = new PlayerAdminManager(plugin);
        cmd = new PlayerAdminCommand(manager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Command bukkitCmdNamed(String name) {
        Command c = mock(Command.class);
        when(c.getName()).thenReturn(name);
        return c;
    }

    // ============================================================
    // /survival, /creative
    // ============================================================

    @Test void gameModeRequiresPermission() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmdNamed("survival"), "survival", new String[0]);
        // Without permission no gamemode change happens.
        assertEquals(GameMode.SURVIVAL, player.getGameMode()); // default already survival; check perm error path
        MessageAssert.assertMessageSent(player, "No permission");
    }

    @Test void survivalSetsSurvivalMode() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setGameMode(GameMode.CREATIVE);
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmdNamed("survival"), "survival", new String[0]);
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
    }

    @Test void creativeSetsCreativeMode() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setGameMode(GameMode.SURVIVAL);
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmdNamed("creative"), "creative", new String[0]);
        assertEquals(GameMode.CREATIVE, player.getGameMode());
    }

    @Test void alreadyInTargetGameModeWarnsInsteadOfChanging() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setGameMode(GameMode.SURVIVAL);
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmdNamed("survival"), "survival", new String[0]);
        MessageAssert.assertMessageSent(player, "Already in survival mode");
    }

    @Test void gameModeFromConsoleIsRejected() {
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) server.getConsoleSender();
        console.addAttachment(plugin, Permissions.ADMIN_GAMEMODE, true);
        cmd.onCommand(console, bukkitCmdNamed("survival"), "survival", new String[0]);
        MessageAssert.assertMessageSent(console, "Only players can change their gamemode");
    }

    // ============================================================
    // /nv
    // ============================================================

    @Test void nightVisionTogglesOnWhenAbsent() {
        PlayerMock player = server.addPlayer();
        assertFalse(player.hasPotionEffect(PotionEffectType.NIGHT_VISION));
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]);
        assertTrue(player.hasPotionEffect(PotionEffectType.NIGHT_VISION));
    }

    @Test void nightVisionTogglesOffWhenPresent() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]); // on
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]); // off
        assertFalse(player.hasPotionEffect(PotionEffectType.NIGHT_VISION));
    }

    // ============================================================
    // /fly
    // ============================================================

    @Test void flyRejectedWhenNoAccess() {
        PlayerMock player = server.addPlayer();
        // No fly worlds configured, no advancement — denied.
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertFalse(player.getAllowFlight());
        MessageAssert.assertMessageSent(player, "don't have access to flight");
    }

    @Test void flyTogglesOffWhenAlreadyFlying() {
        PlayerMock player = server.addPlayer();
        player.setAllowFlight(true);
        player.setFlying(true);
        cmd.onCommand(player, bukkitCmd, "fly", new String[0]);
        assertFalse(player.getAllowFlight());
    }

    // ============================================================
    // /afk
    // ============================================================

    @Test void afkTogglesOnAndOff() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        assertTrue(manager.isAfk(player));
        cmd.onCommand(player, bukkitCmd, "afk", new String[0]);
        assertFalse(manager.isAfk(player));
    }

    // ============================================================
    // /nick
    // ============================================================

    @Test void nickEmptyArgsShowsUsage() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "nick", new String[0]);
        MessageAssert.assertMessageSent(player, "Usage: /nick");
    }

    @Test void nickRejectsSpaces() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"a", "b"});
        MessageAssert.assertMessageSent(player, "cannot contain spaces");
    }

    @Test void nickRejectsMagicCode() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"&kfoo"});
        MessageAssert.assertMessageSent(player, "magic effect");
    }

    @Test void nickSetsValidNickname() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"&cBee"});
        assertTrue(player.getPersistentDataContainer().has(manager.nickKey()));
    }

    @Test void nickOffWithoutSetTellsNoNickname() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"off"});
        MessageAssert.assertMessageSent(player, "don't have a nickname set");
    }

    @Test void nickOffClearsExistingNickname() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage();
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"&aFoo"});
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"off"});
        assertFalse(player.getPersistentDataContainer().has(manager.nickKey()));
    }
}
