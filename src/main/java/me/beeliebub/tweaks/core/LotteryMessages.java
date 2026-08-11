package me.beeliebub.tweaks.core;

import me.beeliebub.tweaks.lottery.LotteryMath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Player-facing messages for the server-wide lottery. */
public final class LotteryMessages {

    private static final Component PREFIX = Component.text("[Lottery]", NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, true);

    LotteryMessages() {
    }

    public Component usage() {
        return prefixed("Usage: /lottery info | entries | draw | baseline <amount> | fallback [<amount>]",
                NamedTextColor.RED);
    }

    public Component loading() {
        return prefixed("The lottery is still loading. Please try again shortly.", NamedTextColor.YELLOW);
    }

    public Component paymentLoading() {
        return prefixed("Lottery payments are still recovering. Please try again shortly.", NamedTextColor.YELLOW);
    }

    public Component noPermission() {
        return prefixed("You do not have permission to administer the lottery.", NamedTextColor.RED);
    }

    public List<Component> info(int entrants, long baseline, long fallback, long fallbackBase,
                                LotteryMath.PotOutcome outcome) {
        Component fallbackLine = Component.text("Pot fallback threshold is currently ", NamedTextColor.GRAY)
                .append(Component.text(money(fallback), NamedTextColor.YELLOW))
                .append(Component.text(" and resets to ", NamedTextColor.GRAY))
                .append(Component.text(money(fallbackBase), NamedTextColor.YELLOW))
                .append(Component.text(" after a successful draw.", NamedTextColor.GRAY));
        Component commandLine = Component.text("/lot entries", NamedTextColor.YELLOW)
                .append(Component.text(" - to view who has a ticket, minimum 2 to draw", NamedTextColor.GRAY));
        Component statusLine = Component.text(entrants + " entrant(s)", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, false)
                .append(Component.text(" | baseline " + money(baseline) + " | ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("current pot ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(potStatus(outcome));
        return List.of(
                PREFIX,
                body("Receive 1 entry into lottery by losing money in casino games.", NamedTextColor.GRAY),
                body("Pot % is based on how much increase to House Balance baseline.", NamedTextColor.GRAY),
                fallbackLine,
                commandLine,
                body("------------------------------------------------", NamedTextColor.DARK_GRAY),
                statusLine
        );
    }

    public Component entriesHeader(int count) {
        return prefixed("Entrants (" + count + ")", NamedTextColor.RED);
    }

    public Component entry(String name) {
        return body("- " + name, NamedTextColor.YELLOW);
    }

    public Component entriesTruncated(int omitted) {
        return body("... and " + omitted + " more.", NamedTextColor.GRAY);
    }

    public Component entriesCooldown() {
        return prefixed("Please wait before checking lottery entries again.", NamedTextColor.YELLOW);
    }

    public Component baselineUpdated(long baseline) {
        return prefixed("Lottery baseline set to " + money(baseline) + ".", NamedTextColor.GREEN);
    }

    public Component baselineInvalid(String input) {
        return prefixed("Baseline must be a non-negative whole number: " + input, NamedTextColor.RED);
    }

    public Component baselineFailed() {
        return prefixed("The lottery baseline could not be saved. Check the server log before trying again.",
                NamedTextColor.RED);
    }

    public Component fallbackUpdated(long fallback) {
        return prefixed("Lottery fallback set to " + money(fallback) + ".", NamedTextColor.GREEN);
    }

    public Component fallbackInvalid(String input) {
        return prefixed("Fallback must be a non-negative whole number: " + input, NamedTextColor.RED);
    }

    public Component fallbackFailed() {
        return prefixed("The lottery fallback could not be saved. Check the server log before trying again.",
                NamedTextColor.RED);
    }

    public Component fallbackStatus(long live, long base) {
        return prefixed("Lottery fallback: live " + money(live) + " (configured base " + money(base) + ").",
                NamedTextColor.YELLOW);
    }

    public Component drawInFlight() {
        return prefixed("A lottery draw is already in progress.", NamedTextColor.YELLOW);
    }

    public Component drawNotReady() {
        return loading();
    }

    public Component drawFailed() {
        return prefixed("The lottery draw could not be completed. Check the server log before trying again.",
                NamedTextColor.RED);
    }

    public Component drawRefused(LotteryMath.RefusalReason reason) {
        return switch (reason) {
            case NOT_ENOUGH_ENTRANTS -> prefixed("Not enough entries to draw.", NamedTextColor.RED);
            case NO_GROWTH -> prefixed("The lottery cannot draw because the House has not grown since the last draw.",
                    NamedTextColor.RED);
            case HOUSE_AT_FLOOR -> prefixed("The House is already at its fallback floor, so there is nothing to pay out.",
                    NamedTextColor.RED);
            case INVALID_BASELINE -> prefixed("The lottery baseline is invalid. Set a non-negative baseline first.",
                    NamedTextColor.RED);
            case POT_ROUNDS_TO_ZERO -> prefixed("The lottery growth is too small to produce a whole-dollar pot.",
                    NamedTextColor.RED);
        };
    }

    public Component paymentAbandoned(String outcome, int entrantCount) {
        return prefixed("The lottery draw was abandoned (" + outcome + "): no money moved, and all "
                + entrantCount + " entries were kept. It is safe to draw again.", NamedTextColor.YELLOW);
    }

    public Component paymentAbandonedBroadcast(int entrantCount) {
        return prefixed("Lottery draw abandoned: no money moved and its " + entrantCount
                + " entries were kept. It is safe to draw again.", NamedTextColor.YELLOW);
    }

    public Component paymentPending(String paymentId, String outcome) {
        return prefixed("The lottery draw was recorded under payment " + paymentId + " (" + outcome
                + "): the payment remains unresolved and will be checked on the next server start.",
                NamedTextColor.YELLOW);
    }

    public Component paymentStuck(String paymentId) {
        return prefixed("The lottery draw needs manual reconciliation for payment " + paymentId
                + ". The draw state was retained.", NamedTextColor.RED);
    }

    public Component notEnoughEntries() {
        return prefixed("Not enough entries to draw", NamedTextColor.GREEN);
    }

    public Component noGrowthBroadcast() {
        return prefixed("No House growth since the last draw", NamedTextColor.GREEN);
    }

    public Component rollInDetail(long rolledIn, long fallback, long baseline) {
        return prefixed("Fallback raised by " + money(rolledIn) + " to " + money(fallback)
                + "; baseline advanced to " + money(baseline) + ".", NamedTextColor.GREEN);
    }

    public Component winner(String name, long amount) {
        return PREFIX
                .append(Component.text(" Winner: ", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                .append(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                .append(Component.text(" won " + money(amount), NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, false));
    }

    private static Component potStatus(LotteryMath.PotOutcome outcome) {
        if (outcome instanceof LotteryMath.PotOutcome.Payable payable) {
            return Component.text(money(payable.pot()), NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, false);
        }
        LotteryMath.RefusalReason reason = ((LotteryMath.PotOutcome.Refused) outcome).reason();
        String description = switch (reason) {
            case NOT_ENOUGH_ENTRANTS -> "not enough entries";
            case NO_GROWTH -> "no House growth";
            case HOUSE_AT_FLOOR -> "House at fallback floor";
            case INVALID_BASELINE -> "invalid baseline";
            case POT_ROUNDS_TO_ZERO -> "pot rounds to zero";
        };
        return Component.text("unavailable (" + description + ")", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, false);
    }

    private static Component prefixed(String message, NamedTextColor color) {
        return PREFIX.append(Component.text(" " + message, color)
                .decoration(TextDecoration.BOLD, false));
    }

    private static Component body(String message, NamedTextColor color) {
        return Component.text(message, color).decoration(TextDecoration.BOLD, false);
    }

    private static String money(long amount) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }
}
