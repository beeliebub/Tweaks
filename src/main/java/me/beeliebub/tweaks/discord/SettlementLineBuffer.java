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
    private static final int BLOCK_OVERHEAD = 11;

    private final Consumer<String> log;
    private final Consumer<String> recoveryLog;
    private final Deque<PendingUnit> pending = new ArrayDeque<>();
    private int pendingLines;
    private boolean overflowing;
    private boolean flushRequested;

    public SettlementLineBuffer(Consumer<String> log) {
        this(log, log);
    }

    public SettlementLineBuffer(Consumer<String> warningLog, Consumer<String> recoveryLog) {
        this.log = Objects.requireNonNull(warningLog, "warningLog");
        this.recoveryLog = Objects.requireNonNull(recoveryLog, "recoveryLog");
    }

    /** Adds a line, coalescing each contiguous non-zero group into one atomic pending unit. */
    public void add(SettlementLine line) {
        Objects.requireNonNull(line, "line");
        if (line.groupId() == 0L) {
            pending.addLast(new PendingUnit(0L, line));
        } else {
            PendingUnit last = pending.peekLast();
            if (last != null && last.groupId == line.groupId()) {
                last.lines.add(new PendingLine(line, System.currentTimeMillis()));
            } else {
                pending.addLast(new PendingUnit(line.groupId(), line));
            }
        }
        pendingLines++;
        if (pendingLines >= MAX_BLOCK_LINES || estimatedCharacters() >= MAX_BLOCK_CHARACTERS) {
            flushRequested = true;
        }
        while (pendingLines > MAX_PENDING_LINES) {
            PendingUnit dropped = pending.removeFirst();
            pendingLines -= dropped.lines.size();
            if (!overflowing) {
                overflowing = true;
                log.accept("Discord settlement buffer overflowed; dropping oldest complete groups until it recovers.");
            }
        }
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Drops pending lines when a live channel edit makes their original destination stale. */
    public void clear() {
        pending.clear();
        pendingLines = 0;
        flushRequested = false;
        overflowing = false;
    }

    /** True when waiting for the normal grouping window would exceed a block-size boundary. */
    public boolean flushRequested() {
        return flushRequested;
    }

    public long oldestAgeMillis() {
        PendingUnit first = pending.peekFirst();
        if (first == null || first.lines.isEmpty()) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - first.lines.get(0).createdAtMillis);
    }

    /** Drains complete groups, splitting only an individual oversized group when necessary. */
    public List<String> drainBlocks() {
        if (pending.isEmpty()) return List.of();
        List<String> blocks = new ArrayList<>();
        flushRequested = false;
        StringBuilder body = new StringBuilder();
        int bodyLines = 0;
        while (!pending.isEmpty()) {
            PendingUnit unit = pending.peekFirst();
            if (fits(body, bodyLines, unit)) {
                appendUnit(body, unit);
                bodyLines += unit.lines.size();
                removeFirstUnit();
                continue;
            }
            if (bodyLines > 0) {
                blocks.add(block(body));
                body = new StringBuilder();
                bodyLines = 0;
                continue;
            }
            // The unit itself is too large for one block. It is the only permitted split path.
            blocks.addAll(splitUnit(unit));
            removeFirstUnit();
        }
        if (bodyLines > 0) {
            blocks.add(block(body));
        }
        if (overflowing && pendingLines < MAX_PENDING_LINES) {
            overflowing = false;
            recoveryLog.accept("Discord settlement buffer recovered from overflow.");
        }
        return List.copyOf(blocks);
    }

    private void removeFirstUnit() {
        PendingUnit removed = pending.removeFirst();
        pendingLines -= removed.lines.size();
    }

    private static boolean fits(StringBuilder body, int bodyLines, PendingUnit unit) {
        if (bodyLines + unit.lines.size() > MAX_BLOCK_LINES) {
            return false;
        }
        return BLOCK_OVERHEAD + body.length() + unit.renderedCharacters() <= MAX_BLOCK_CHARACTERS;
    }

    private static int appendUnit(StringBuilder body, PendingUnit unit) {
        for (PendingLine line : unit.lines) {
            body.append(renderLine(line.line)).append('\n');
        }
        return unit.lines.size();
    }

    private List<String> splitUnit(PendingUnit unit) {
        if (unit.groupId == 0L) {
            StringBuilder body = new StringBuilder();
            appendUnit(body, unit);
            return List.of(block(body));
        }

        SettlementLine header = unit.lines.get(0).line.sign() == SettlementLine.Sign.HEADER
                ? unit.lines.get(0).line : null;
        List<String> blocks = new ArrayList<>();
        int next = 0;
        while (next < unit.lines.size()) {
            StringBuilder body = new StringBuilder();
            int lines = 0;
            if (next > 0 && header != null) {
                body.append(renderLine(header)).append('\n');
                lines++;
            }
            while (next < unit.lines.size()) {
                SettlementLine line = unit.lines.get(next).line;
                String rendered = renderLine(line);
                boolean fits = lines < MAX_BLOCK_LINES
                        && BLOCK_OVERHEAD + body.length() + rendered.length() + 1
                        <= MAX_BLOCK_CHARACTERS;
                if (!fits && lines > 0) {
                    // A long individual line remains intact; it is safer to send the complete
                    // settlement than to truncate money information.
                    if (lines == 1 && header != null && line.sign() != SettlementLine.Sign.HEADER) {
                        body.append(rendered).append('\n');
                        next++;
                    }
                    break;
                }
                body.append(rendered).append('\n');
                lines++;
                next++;
            }
            if (lines == 0) {
                SettlementLine line = unit.lines.get(next++).line;
                body.append(renderLine(line)).append('\n');
            }
            blocks.add(block(body));
        }
        return blocks;
    }

    private int estimatedCharacters() {
        int total = BLOCK_OVERHEAD;
        for (PendingUnit unit : pending) {
            total += unit.renderedCharacters();
            if (total >= MAX_BLOCK_CHARACTERS) return total;
        }
        return total;
    }

    private static String block(StringBuilder body) {
        return "```diff\n" + body + "```";
    }

    private static String renderLine(SettlementLine line) {
        return switch (line.sign()) {
            case WIN -> "+ " + line.text();
            case LOSS -> "- " + line.text();
            case NEUTRAL -> "  " + line.text();
            case HEADER -> line.text();
        };
    }

    private static final class PendingUnit {
        private final long groupId;
        private final List<PendingLine> lines = new ArrayList<>();

        private PendingUnit(long groupId, SettlementLine first) {
            this.groupId = groupId;
            this.lines.add(new PendingLine(first, System.currentTimeMillis()));
        }

        private int renderedCharacters() {
            int total = 0;
            for (PendingLine line : lines) {
                total += renderLine(line.line).length() + 1;
            }
            return total;
        }
    }

    private record PendingLine(SettlementLine line, long createdAtMillis) {
    }
}
