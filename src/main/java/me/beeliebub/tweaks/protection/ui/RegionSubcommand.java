package me.beeliebub.tweaks.protection.ui;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * One `/region` subcommand: co-locates execution and tab completion so the two
 * can never drift apart the way the previous monolithic {@code switch}/{@code switch}
 * pair in {@code ProtectionCommand} could (tab completion lived ~700 lines away
 * from the handler it complemented).
 *
 * <h2>Arg-array convention — deliberately asymmetric, do not "fix"</h2>
 * {@link #execute} receives {@code args} with the subcommand token already popped
 * (matching the original {@code onCommand} dispatch, which called {@code popFirst}
 * before invoking a handler). {@link #tabComplete} receives the RAW args array with
 * the subcommand token still at index 0 (matching the original {@code onTabComplete},
 * whose ~200 lines of index-sensitive suggestion logic were written against that
 * layout). Normalizing both to popped args would require re-deriving every index in
 * the moved tab-completion code — a wrong index produces silently wrong suggestions,
 * not a compile error. The differing parameter name ({@code rawArgs}) is deliberate:
 * it marks the convention at every call site rather than relying on a comment alone.
 */
interface RegionSubcommand {

    /** Primary dispatch name, e.g. {@code "addmember"}. */
    String name();

    /** Additional names that resolve to this same handler, e.g. {@code ["am"]}. */
    default List<String> aliases() {
        return List.of();
    }

    /** Required permission node, or {@code null} if this subcommand has no gate. */
    String permission();

    /** Minimum number of post-subcommand arguments required by execute(). */
    default int minArgs() {
        return 0;
    }

    /** False for aliases — they dispatch and tab-complete but contribute no usage line. */
    default boolean visibleInUsage() {
        return true;
    }

    /** Usage line(s) for {@code /region} root help and per-subcommand usage errors. */
    List<RegionUsageEntry> usage();

    /** Execute with the subcommand token already popped from {@code args}. */
    void execute(RegionCommandContext ctx, CommandSender sender, String[] args);

    /**
     * Tab-complete with the RAW args array (subcommand token still at {@code rawArgs[0]}) —
     * see the class Javadoc for why this differs from {@link #execute}'s convention.
     */
    List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs);
}
