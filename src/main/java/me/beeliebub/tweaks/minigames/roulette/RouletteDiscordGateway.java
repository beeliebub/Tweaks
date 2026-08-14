package me.beeliebub.tweaks.minigames.roulette;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Package-private boundary for the UUID-only Discord Roulette surface. */
final class RouletteDiscordGateway {

    private final RouletteSessionManager sessions;
    private final Map<RouletteBoardStore.BoardEntry, LastResult> lastResults = new HashMap<>();

    RouletteDiscordGateway(RouletteSessionManager sessions) {
        this.sessions = sessions;
    }

    DiscordBetResult placeDiscordBet(UUID playerId, BetType type, int selector, int amount) {
        return sessions.placeDiscordBet(playerId, type, selector, amount);
    }

    DiscordBoardStatus discordBoardStatus() {
        return sessions.discordBoardStatus(lastResults);
    }

    List<RouletteBet> discordBetsFor(UUID playerId) {
        return sessions.discordBetsFor(playerId);
    }

    void recordLastResult(RouletteBoardStore.BoardEntry board, int pocket, String colorName) {
        lastResults.put(board, new LastResult(pocket, colorName));
    }

    void replaceBoardIdentity(RouletteBoardStore.BoardEntry oldEntry,
                              RouletteBoardStore.BoardEntry newEntry) {
        LastResult result = lastResults.remove(oldEntry);
        if (result != null) {
            lastResults.put(newEntry, result);
        }
    }
}
