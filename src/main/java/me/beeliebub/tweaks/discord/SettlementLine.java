package me.beeliebub.tweaks.discord;

/** One settlement line before it is rendered into a Discord diff block. */
public record SettlementLine(String text, Sign sign) {

    public static SettlementLine forNet(String text, long net) {
        Sign sign = net > 0 ? Sign.WIN : net < 0 ? Sign.LOSS : Sign.NEUTRAL;
        return new SettlementLine(text, sign);
    }

    public SettlementLine {
        if (text == null || sign == null) {
            throw new IllegalArgumentException("Settlement line text and sign are required");
        }
        if (text.indexOf('`') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Settlement line text must stay inside its diff fence");
        }
    }

    public enum Sign { WIN, LOSS, NEUTRAL }
}
