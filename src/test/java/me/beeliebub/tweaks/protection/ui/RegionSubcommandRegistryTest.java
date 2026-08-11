package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link RegionSubcommandRegistry}'s construction order — the order handlers are registered
 * in IS the {@code /region} usage-print order, and must exactly match the original 16-line usage
 * listing's declaration order (see the class Javadoc). A silent reordering here would corrupt
 * usage output without a compile error, so this is the one invariant in the handler-per-subcommand
 * design that most needs a pinning test rather than a comment. No MockBukkit needed — handler
 * construction and {@code visibleNames}/{@code find} touch no Bukkit statics.
 */
class RegionSubcommandRegistryTest {

    private static final List<String> EXPECTED_ORDER = List.of(
            "claim", "clear", "wand", "select", "unclaim",
            "addmember", "removemember", "addmanager", "removemanager",
            "flag", "unflag", "flags", "info",
            "setparent", "unsetparent", "gui", "list", "tp", "togglebypass");

    private RegionSubcommandRegistry registry;
    private RegionCommandContext ctx;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        registry = new RegionSubcommandRegistry();
        // RegionCommandContext is a final class — construct it for real (as every other
        // test in this package does) rather than mocking it, with a mocked sender that
        // grants every permission so visibleNames()/showRootUsage() include every handler.
        ctx = new RegionCommandContext(mock(Tweaks.class), mock(ProtectionManager.class),
                mock(RegionSelectionManager.class));
        sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
    }

    @Test
    void visibleNames_matchesDeclarationOrderExactly() {
        assertEquals(EXPECTED_ORDER, registry.visibleNames(ctx, sender));
    }

    @Test
    void find_resolvesEveryPrimaryNameToAHandlerWithMatchingName() {
        for (String name : EXPECTED_ORDER) {
            RegionSubcommand handler = registry.find(name);
            assertNotNull(handler, "expected a handler registered under '" + name + "'");
            assertEquals(name, handler.name());
        }
    }

    @Test
    void find_resolvesAliasesToTheSameHandlerInstanceAsThePrimaryName() {
        assertSame(registry.find("addmember"), registry.find("am"));
        assertSame(registry.find("removemember"), registry.find("rm"));
        assertSame(registry.find("addmanager"), registry.find("aman"));
        assertSame(registry.find("removemanager"), registry.find("rman"));
    }

    @Test
    void find_isCaseInsensitive() {
        assertSame(registry.find("gui"), registry.find("GUI"));
        assertSame(registry.find("flag"), registry.find("Flag"));
    }

    @Test
    void find_unknownNameReturnsNull() {
        assertNull(registry.find("nonexistentcommand"));
        assertNull(registry.find(null));
    }

    @Test
    void find_unrelatedCurrencyNamesDoNotCollideWithRealSubcommands() {
        // Sanity check that claim/unclaim resolve to their own dedicated handler classes,
        // not to something else registered under a similar name.
        assertNotNull(registry.find("claim"));
        assertEquals(ClaimSubcommand.class, registry.find("claim").getClass());
        assertNotNull(registry.find("unclaim"));
        assertEquals(UnclaimSubcommand.class, registry.find("unclaim").getClass());
    }
}
