package me.beeliebub.tweaks.core;

import me.beeliebub.tweaks.lottery.LotteryMath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Player-facing messages for the server-wide lottery. */
public final class LotteryMessages {

    LotteryMessages() {
    }

    public Component usage() {
        return red("Usage: /lottery info | draw | entries | baseline <amount>");
    }

    public Component loading() {
        return yellow("The lottery is still loading. Please try again shortly.");
    }

    public Component paymentLoading() {
        return yellow("Lottery payments are still recovering. Please try again shortly.");
    }

    public Component noPermission() {
        return red("You do not have permission to administer the lottery.");
    }

    public Component info(int entrants, long baseline, String pot) {
        return Component.text("Lottery: ", NamedTextColor.GOLD)
                .append(Component.text(entrants + " entrant(s)", NamedTextColor.YELLOW))
                .append(Component.text(" | baseline $" + baseline, NamedTextColor.GRAY))
                .append(Component.text(" | current pot: " + pot, NamedTextColor.GREEN));
    }

    public Component entriesHeader(int count) {
        return Component.text("Lottery entrants (" + count + "):", NamedTextColor.GOLD);
    }

    public Component entry(String name) {
        return Component.text("- " + name, NamedTextColor.YELLOW);
    }

    public Component entriesTruncated(int omitted) {
        return Component.text("... and " + omitted + " more.", NamedTextColor.GRAY);
    }

    public Component baselineUpdated(long baseline) {
        return Component.text("Lottery baseline set to $" + baseline + ".", NamedTextColor.GREEN);
    }

    public Component baselineInvalid(String input) {
        return red("Baseline must be a non-negative whole number: " + input);
    }

    public Component baselineFailed() {
        return red("The lottery baseline could not be saved. Check the server log before trying again.");
    }

    public Component drawInFlight() {
        return yellow("A lottery draw is already in progress.");
    }

    public Component drawNotReady() {
        return loading();
    }

    public Component drawFailed() {
        return red("The lottery draw could not be completed. Check the server log before trying again.");
    }

    public Component drawRefused(LotteryMath.RefusalReason reason) {
        return switch (reason) {
            case NO_ENTRANTS -> red("The lottery cannot draw because there are no entrants.");
            case NO_GROWTH -> red("The lottery cannot draw because the House has not grown since the last draw.");
            case EMPTY_HOUSE -> red("The lottery cannot draw because the House balance is empty.");
            case INVALID_BASELINE -> red("The lottery baseline is invalid. Set a non-negative baseline first.");
            case POT_ROUNDS_TO_ZERO -> red("The lottery growth is too small to produce a whole-dollar pot.");
        };
    }

    public Component paymentAbandoned(String outcome, int entrantCount) {
        return yellow("The lottery draw was abandoned (" + outcome + "): no money moved, and all "
                + entrantCount + " entries were kept. It is safe to draw again.");
    }

    public Component paymentAbandonedBroadcast(int entrantCount) {
        return Component.text("Lottery draw abandoned: no money moved and its " + entrantCount
                + " entries were kept. It is safe to draw again.", NamedTextColor.YELLOW);
    }

    public Component paymentPending(String paymentId, String outcome) {
        return yellow("The lottery draw was recorded under payment " + paymentId + " (" + outcome
                + "): the payment remains unresolved and will be checked on the next server start.");
    }

    public Component paymentStuck(String paymentId) {
        return red("The lottery draw needs manual reconciliation for payment " + paymentId
                + ". The draw state was retained.");
    }

    public Component winner(String name, long amount) {
        return Component.text("Lottery winner: ", NamedTextColor.GOLD)
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text(" receives $" + amount + "!", NamedTextColor.GREEN));
    }

    private static Component red(String message) { return Component.text(message, NamedTextColor.RED); }

    private static Component yellow(String message) { return Component.text(message, NamedTextColor.YELLOW); }
}
