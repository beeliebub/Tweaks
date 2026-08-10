package me.beeliebub.tweaks.tests.skyblock.ui;

import me.beeliebub.tweaks.skyblock.ui.admin.AdminActions;
import me.beeliebub.tweaks.skyblock.ui.admin.AdminPage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPageTest {
    @Test
    void filtersLiteralTextCaseInsensitivelyWithoutTreatingItAsRegex() {
        List<String> values = List.of("Alpha.*", "alphabet soup", "beta", "ALPHA[one]");

        AdminPage.Page<String> page = AdminPage.create(values, 0, " ALPHA.* ",
                value -> value);

        assertEquals(List.of("Alpha.*"), page.values());
        assertEquals(1, page.pageCount());
        assertEquals("alpha.*", page.filter());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void paginatesTwelveEntriesAndClampsRequestedPage() {
        List<String> values = IntStream.range(0, 25)
                .mapToObj(index -> "entry-" + index).toList();

        AdminPage.Page<String> first = AdminPage.create(values, 0, "", value -> value);
        AdminPage.Page<String> second = AdminPage.create(values, 1, "", value -> value);
        AdminPage.Page<String> last = AdminPage.create(values, 99, "", value -> value);

        assertEquals(values.subList(0, 12), first.values());
        assertEquals(0, first.page());
        assertEquals(3, first.pageCount());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());

        assertEquals(values.subList(12, 24), second.values());
        assertEquals(1, second.page());
        assertTrue(second.hasPrevious());
        assertTrue(second.hasNext());

        assertEquals(List.of("entry-24"), last.values());
        assertEquals(2, last.page());
        assertEquals("3/3", last.label());
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
    }

    @Test
    void adminActionsMoveAndRemoveByValidatedIndexes() {
        assertEquals(List.of("a", "c", "b"), AdminActions.move(List.of("a", "b", "c"), 1, 2).orElseThrow());
        assertEquals(List.of("a", "c"), AdminActions.removeAt(List.of("a", "b", "c"), 1).orElseThrow());
        assertTrue(AdminActions.move(List.of("a"), 0, 2).isEmpty());
        assertEquals("sky_block-1", AdminActions.validId(" SKY_BLOCK-1 ", "id").orElseThrow());
    }

    @Test
    void adminActionsRejectInvalidInputWithoutMutatingSource() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        assertTrue(AdminActions.move(source, -1, 1).isEmpty());
        assertTrue(AdminActions.removeAt(source, 2).isEmpty());
        assertTrue(AdminActions.removeAt(null, 0).isEmpty());
        assertTrue(AdminActions.validId("has space", "id").isEmpty());
        assertEquals(List.of("a", "b"), source);
    }
}
