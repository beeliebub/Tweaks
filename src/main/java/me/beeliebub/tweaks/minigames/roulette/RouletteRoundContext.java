package me.beeliebub.tweaks.minigames.roulette;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable per-board round holder, mirroring {@code blackjack.Session}. Collapses what would
 * otherwise be several parallel {@code Map<BoardEntry, X>} fields on {@link RouletteSessionManager}
 * into one map keyed on {@link RouletteBoardStore.BoardEntry}.
 */
final class RouletteRoundContext {

    final RouletteBoardStore.BoardEntry board;
    final RouletteRound round = new RouletteRound();

    /**
     * Rakeback rate snapshotted per player at bet-placement time, never re-read at settlement.
     * {@code RankManager.getCasinoRakebackRate} delegates to {@code EconomyManager.getRank}, which
     * returns {@code 0} for an unloaded player — settling from a live lookup would silently zero an
     * offline bettor's rakeback (a round settles regardless of who is still online, so an offline
     * bettor is an expected case, not an edge case).
     */
    final Map<UUID, Double> rakebackRates = new HashMap<>();

    int windowTaskId = -1;
    int spinTaskId = -1;
    int lingerTaskId = -1;
    long windowOpenedTick = -1L;
    int frame = 0;

    /**
     * The betting window length (in ticks), snapshotted from {@code minigames.roulette
     * .betting-window-seconds} the instant this round's window opens, never re-read afterward. A
     * live re-read here would desync an already-scheduled expiry task's actual delay from the
     * countdown text shown to players if a /tconfig edit lands mid-round.
     */
    long bettingWindowTicks = -1L;

    /** Non-null only while a rotation animation is in flight this spin. */
    List<RouletteRestPoseStore.SegmentPose> restPoseSnapshot;
    RouletteRenderer.SpinPlan spinPlan;

    RouletteRoundContext(RouletteBoardStore.BoardEntry board) {
        this.board = board;
    }
}
