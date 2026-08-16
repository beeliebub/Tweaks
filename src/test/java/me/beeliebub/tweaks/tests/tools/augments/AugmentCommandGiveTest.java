package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import me.beeliebub.tweaks.tools.augments.AugmentCommand;
import me.beeliebub.tweaks.tools.augments.AugmentDialog;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import org.bukkit.command.Command;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AugmentCommandGiveTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;
    private AugmentCommand command;
    private Command bukkitCommand;
    private PlayerMock admin;
    private PlayerMock target;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
        command = new AugmentCommand(augments, new AugmentDialog(augments));
        bukkitCommand = mock(Command.class);
        admin = server.addPlayer("Admin");
        target = server.addPlayer("Target");
        while (admin.nextComponentMessage() != null) {}
        while (target.nextComponentMessage() != null) {}
        admin.addAttachment(plugin, Permissions.AUGMENT_ADMIN, true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void givesPrimaryWithOmittedLevelAndExplicitRiderLevel() {
        command.onCommand(admin, bukkitCommand, "augment", new String[]{
                "give", "Target", "minecraft:efficiency", "minecraft:vanishing_curse:2"});

        AugmentGemItem.GemData data = readOnlyGem();
        assertEquals(Enchantment.EFFICIENCY, data.enchantment());
        assertEquals(1, data.level());
        assertEquals(1, data.curses().size());
        assertEquals(2, data.curses().getFirst().level());
        MessageAssert.assertMessageSent(admin, "minecraft:efficiency");
    }

    @Test
    void cursePrimaryCreatesCurseOnlyGem() {
        command.onCommand(admin, bukkitCommand, "augment", new String[]{
                "give", "Target", "minecraft:vanishing_curse"});

        AugmentGemItem.GemData data = readOnlyGem();
        assertEquals(Enchantment.VANISHING_CURSE, data.enchantment());
        assertTrue(data.curses().isEmpty());
    }

    @Test
    void rejectsNonCurseDuplicateAndPrimaryRidersWithoutGivingAnything() {
        int before = countItems();
        command.onCommand(admin, bukkitCommand, "augment", new String[]{
                "give", "Target", "minecraft:efficiency", "5", "minecraft:sharpness"});
        assertEquals(before, countItems());
        MessageAssert.assertMessageSent(admin, "not a registered curse");

        command.onCommand(admin, bukkitCommand, "augment", new String[]{
                "give", "Target", "minecraft:efficiency", "5",
                "minecraft:vanishing_curse", "minecraft:vanishing_curse"});
        assertEquals(before, countItems());
        MessageAssert.assertMessageSent(admin, "more than once");

        command.onCommand(admin, bukkitCommand, "augment", new String[]{
                "give", "Target", "minecraft:vanishing_curse", "1", "minecraft:vanishing_curse"});
        assertEquals(before, countItems());
        MessageAssert.assertMessageSent(admin, "primary");
    }

    @Test
    void permissionStillGatesGive() {
        PlayerMock nonAdmin = server.addPlayer("NoAdmin");
        nonAdmin.setOp(false);
        while (nonAdmin.nextComponentMessage() != null) {}
        command.onCommand(nonAdmin, bukkitCommand, "augment", new String[]{
                "give", "Target", "minecraft:efficiency"});

        assertEquals(0, countItems());
        MessageAssert.assertMessageSent(nonAdmin, "permission");
    }

    @Test
    void tabCompletionUsesCurseKeysOnlyForRiders() {
        List<String> completions = command.onTabComplete(admin, bukkitCommand, "augment",
                new String[]{"give", "Target", "minecraft:efficiency", "5", "minecraft:"});

        assertTrue(completions.contains("minecraft:vanishing_curse"));
        assertTrue(completions.contains("minecraft:binding_curse"));
        assertFalse(completions.contains("minecraft:efficiency"));
    }

    private AugmentGemItem.GemData readOnlyGem() {
        return Arrays.stream(target.getInventory().getStorageContents())
                .map(augments.gemItem()::read)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    private int countItems() {
        return (int) Arrays.stream(target.getInventory().getStorageContents())
                .filter(item -> item != null && !item.isEmpty())
                .count();
    }
}
