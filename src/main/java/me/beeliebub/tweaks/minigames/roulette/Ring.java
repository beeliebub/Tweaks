package me.beeliebub.tweaks.minigames.roulette;

/**
 * Which physical ring of the board a scanned segment belongs to, parsed from its
 * {@code roulette_*} scoreboard tag (never from geometry — see {@link RouletteTagParser}).
 */
enum Ring {
    OUTER,
    MIDDLE,
    INNER,
    UNKNOWN
}
