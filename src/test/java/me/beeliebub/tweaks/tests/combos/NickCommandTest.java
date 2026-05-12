package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.combos.NickCommand;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NickCommandTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private NickCommand cmd;
    private NamespacedKey nickKey;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("tweaks");
        cmd = new NickCommand(plugin);
        nickKey = new NamespacedKey(plugin, "nickname");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void noArgsShowsUsageAndDoesNotMutatePdc() {
        PlayerMock player = server.addPlayer("Bee");
        cmd.onCommand(player, bukkitCmd, "nick", new String[0]);
        assertFalse(player.getPersistentDataContainer().has(nickKey));
    }

    @Test
    void setsValidLegacyColoredNickname() {
        PlayerMock player = server.addPlayer("Bee");
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"&aGreenBee"});
        assertEquals("&aGreenBee",
                player.getPersistentDataContainer().get(nickKey, PersistentDataType.STRING));
    }

    @Test
    void rejectsNicknameWithSpaces() {
        PlayerMock player = server.addPlayer("Bee");
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"Two", "Words"});
        assertFalse(player.getPersistentDataContainer().has(nickKey),
                "nicknames containing spaces must be refused");
    }

    @Test
    void rejectsMagicObfuscatedFormatCode() {
        PlayerMock player = server.addPlayer("Bee");
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"&kHidden"});
        assertFalse(player.getPersistentDataContainer().has(nickKey),
                "&k is banned because it animates illegibly");
    }

    @Test
    void rejectsNicknameLongerThan24VisibleChars() {
        PlayerMock player = server.addPlayer("Bee");
        // 25 visible chars, no color codes — should fail.
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"a".repeat(25)});
        assertFalse(player.getPersistentDataContainer().has(nickKey));
    }

    @Test
    void acceptsNicknameAt24VisibleCharsWithColorCodes() {
        PlayerMock player = server.addPlayer("Bee");
        // 24 visible chars + leading color code (which strips out for length calc).
        String nickname = "&a" + "x".repeat(24);
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{nickname});
        assertEquals(nickname,
                player.getPersistentDataContainer().get(nickKey, PersistentDataType.STRING));
    }

    @Test
    void offWithoutNicknameShowsHintAndIsNoOp() {
        PlayerMock player = server.addPlayer("Bee");
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"off"});
        assertFalse(player.getPersistentDataContainer().has(nickKey));
    }

    @Test
    void offClearsOwnNickname() {
        PlayerMock player = server.addPlayer("Bee");
        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"&aGreenBee"});
        assertTrue(player.getPersistentDataContainer().has(nickKey));

        cmd.onCommand(player, bukkitCmd, "nick", new String[]{"off"});
        assertFalse(player.getPersistentDataContainer().has(nickKey));
    }

    @Test
    void adminCanClearOnlineTargetNickname() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, Permissions.ADMIN_NICK, true);
        PlayerMock target = server.addPlayer("Bee");
        target.getPersistentDataContainer().set(nickKey, PersistentDataType.STRING, "&aGreenBee");

        cmd.onCommand(admin, bukkitCmd, "nick", new String[]{"off", "Bee"});
        assertFalse(target.getPersistentDataContainer().has(nickKey),
                "admin /nick off <player> should clear an online target's nickname");
    }

    @Test
    void nonAdminCannotClearOtherPlayersNickname() {
        PlayerMock notAdmin = server.addPlayer("Mallory");
        PlayerMock target = server.addPlayer("Bee");
        target.getPersistentDataContainer().set(nickKey, PersistentDataType.STRING, "&aGreenBee");

        cmd.onCommand(notAdmin, bukkitCmd, "nick", new String[]{"off", "Bee"});
        assertTrue(target.getPersistentDataContainer().has(nickKey),
                "without ADMIN_NICK, /nick off <player> must do nothing");
    }
}
