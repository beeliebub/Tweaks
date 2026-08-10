package me.beeliebub.tweaks.skyblock.ui.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Bukkit-free literal filtering and twelve-entry pagination for administrator lists. */
public final class AdminPage<T> {
    public static final int PAGE_SIZE = 12;

    private AdminPage() {
    }

    public static <T> Page<T> create(List<T> source, int requestedPage, String filter,
                                     Function<T, String> searchableText) {
        List<T> values = source == null ? List.of() : List.copyOf(source);
        String normalized = literalFilter(filter);
        List<T> filtered = new ArrayList<>();
        for (T value : values) {
            String text = searchableText == null ? String.valueOf(value) : searchableText.apply(value);
            if (normalized.isBlank() || text != null && text.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(value);
            }
        }
        int pageCount = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = Math.min(page * PAGE_SIZE, filtered.size());
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        return new Page<>(filtered.subList(start, end), page, pageCount, page > 0,
                page + 1 < pageCount, normalized);
    }

    public static String literalFilter(String filter) {
        return filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
    }

    public record Page<T>(List<T> values, int page, int pageCount, boolean hasPrevious,
                          boolean hasNext, String filter) {
        public Page {
            values = values == null ? List.of() : List.copyOf(values);
            pageCount = Math.max(1, pageCount);
            page = Math.max(0, Math.min(page, pageCount - 1));
            filter = filter == null ? "" : filter;
        }

        public String label() {
            return (page + 1) + "/" + pageCount;
        }
    }
}
