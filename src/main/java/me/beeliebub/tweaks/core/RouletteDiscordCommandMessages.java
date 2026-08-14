package me.beeliebub.tweaks.core;

import me.beeliebub.tweaks.minigames.roulette.DiscordBoardStatus;
import me.beeliebub.tweaks.minigames.roulette.BetType;
import me.beeliebub.tweaks.minigames.roulette.RouletteBet;

/** Plain-text, ephemeral response factories for the optional Discord Roulette bridge. */
public final class RouletteDiscordCommandMessages {

    RouletteDiscordCommandMessages() {
    }

    public String accountNotLinked() {
        return "Your Discord account is not linked to a Minecraft account.";
    }

    public String bettingDisabled() {
        return "Discord Roulette betting is currently disabled.";
    }

    public String wrongChannel() {
        return "Use this command in the configured Roulette betting channel.";
    }

    public String cooldown() {
        return "Please wait a moment before placing another Discord bet.";
    }

    public String unavailable() {
        return "The Roulette bridge is temporarily unavailable.";
    }

    public String serverRestarting() {
        return "The server is restarting; please try again shortly.";
    }

    public String noDesignatedBoard() {
        return "No Roulette board is designated for Discord betting.";
    }

    public String boardUnavailable() {
        return "The designated Roulette board is currently unavailable.";
    }

    public String bettingClosed() {
        return "Betting is closed while the Roulette wheel is spinning.";
    }

    public String invalidBet() {
        return "That bet is invalid or outside the board's wager range.";
    }

    public String exposureLimit() {
        return "That bet would exceed your maximum exposure for this round.";
    }

    public String insufficientFunds(long balance) {
        return "Insufficient funds. Your balance is $" + balance + ".";
    }

    public String insufficientFunds() {
        return "Insufficient funds for that wager.";
    }

    public String balanceUnrepresentable() {
        return "Your balance cannot represent that wager.";
    }

    public String betRejected() {
        return "The bet could not be placed. Please try again.";
    }

    public String betPlaced(int amount, String target, int multiplier) {
        return "Bet placed: $" + amount + " on " + target + " (pays " + multiplier + ":1).";
    }

    public String balance(long balance) {
        return "Balance: $" + balance;
    }

    public String roulette(DiscordBoardStatus status) {
        if (!status.exists()) return noDesignatedBoard();
        StringBuilder message = new StringBuilder("Roulette board: ")
                .append(status.active() ? "available" : "unavailable")
                .append(" | wager range $" ).append(status.minBet()).append("-$").append(status.maxBet());
        if (status.state() == null || status.state().name().equals("IDLE")) {
            message.append(" | idle");
        } else if (status.state().name().equals("BETTING")) {
            message.append(" | betting open (about ").append(status.secondsRemaining()).append("s)");
        } else {
            message.append(" | ").append(status.state().name().toLowerCase());
        }
        if (status.lastResult() != null) {
            message.append(" | last: ").append(status.lastResult().pocket()).append(' ')
                    .append(titleCase(status.lastResult().colorName()));
        }
        return message.toString();
    }

    public String noBets() {
        return "You have no bets in the current Roulette round.";
    }

    public String myBets(java.util.List<RouletteBet> bets) {
        if (bets.isEmpty()) return noBets();
        StringBuilder result = new StringBuilder("Your current bets:");
        for (RouletteBet bet : bets) {
            result.append(" ").append(bet.amount()).append(" on ")
                    .append(target(bet)).append(';');
        }
        return result.toString();
    }

    public String target(RouletteBet bet) {
        return target(bet.type(), bet.selector());
    }

    public String target(BetType type, int selector) {
        return switch (type) {
            case STRAIGHT -> "pocket " + selector;
            case DOZEN -> "dozen " + selector;
            case COLOR -> selector == RouletteBet.COLOR_RED ? "red" : "black";
        };
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String value = raw.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
