package me.beeliebub.tweaks.discord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Main-thread-only bounded grouping buffer for casino settlement lines. */
public final class SettlementLineBuffer {

    public static final int MAX_BLOCK_CHARACTERS = 1_900;
    public static final int MAX_BLOCK_LINES = 40;
    public static final int MAX_PENDING_LINES = 200;

    private final Consumer<String> log;
    private final Consumer<String> recoveryLog;
    private final Deque<PendingLine> pending = new ArrayDeque<>();
    private boolean overflowing;
    private boolean flushRequested;

    public SettlementLineBuffer(Consumer<String> log) {
        this(log, log);
    }

    public SettlementLineBuffer(Consumer<String> warningLog, Consumer<String> recoveryLog) {
        this.log = Objects.requireNonNull(warningLog, "warningLog");
        this.recoveryLog = Objects.requireNonNull(recoveryLog, "recoveryLog");
    }

    public void add(SettlementLine line) {
        pending.addLast(new PendingLine(line, System.currentTimeMillis()));
        if (pending.size() >= MAX_BLOCK_LINES || estimatedCharacters() >= MAX_BLOCK_CHARACTERS) {
            flushRequested = true;
        }
        while (pending.size() > MAX_PENDING_LINES) {
            pending.removeFirst();
            if (!overflowing) {
                overflowing = true;
                log.accept("Discord settlement buffer overflowed; dropping oldest lines until it recovers.");
            }
        }
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Drops pending lines when a live channel edit makes their original destination stale. */
    public void clear() {
        pending.clear();
        flushRequested = false;
        overflowing = false;
    }

    /** True when waiting for the normal grouping window would exceed a block-size boundary. */
    public boolean flushRequested() {
        return flushRequested;
    }

    public long oldestAgeMillis() {
        PendingLine first = pending.peekFirst();
        return first == null ? 0L : Math.max(0L, System.currentTimeMillis() - first.createdAtMillis);
    }

    public List<String> drainBlocks() {
        if (pending.isEmpty()) return List.of();
        List<String> blocks = new ArrayList<>();
        flushRequested = false;
        while (!pending.isEmpty()) {
            StringBuilder body = new StringBuilder();
            int lines = 0;
            while (!pending.isEmpty() && lines < MAX_BLOCK_LINES) {
                String rendered = renderLine(pending.peekFirst().line);
                int projected = 10 + body.length() + rendered.length() + 1;
                if (lines > 0 && projected > MAX_BLOCK_CHARACTERS) break;
                pending.removeFirst();
                body.append(rendered).append('\n');
                lines++;
            }
            if (lines == 0) {
                // A single line can be longer than the conservative message budget. Keep it
                // intact rather than silently truncating a settlement; Discord will reject it
                // and the next flush can retry after the operator sees the warning.
                body.append(renderLine(pending.removeFirst().line)).append('\n');
            }
            blocks.add("```diff\n" + body + "```");
        }
        if (overflowing && pending.size() < MAX_PENDING_LINES) {
            overflowing = false;
            recoveryLog.accept("Discord settlement buffer recovered from overflow.");
        }
        return List.copyOf(blocks);
    }

    private int estimatedCharacters() {
        int total = 10;
        for (PendingLine line : pending) {
            total += renderLine(line.line).length() + 1;
            if (total >= MAX_BLOCK_CHARACTERS) return total;
        }
        return total + 3;
    }

    private static String renderLine(SettlementLine line) {
        String prefix = switch (line.sign()) {
            case WIN -> "+ ";
            case LOSS -> "- ";
            case NEUTRAL -> "  ";
        };
        return prefix + line.text();
    }

    private record PendingLine(SettlementLine line, long createdAtMillis) {
    }
}
