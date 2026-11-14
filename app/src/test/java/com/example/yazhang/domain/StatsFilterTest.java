package com.example.yazhang.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class StatsFilterTest {

    @Test
    public void keepsExplicitDateTypeAndCategory() {
        StatsFilter filter = StatsFilter.of("2026-11-03", "2026-11-09", "EXPENSE", "FOOD");

        assertEquals("2026-11-03", filter.from);
        assertEquals("2026-11-09", filter.to);
        assertEquals("EXPENSE", filter.type);
        assertEquals("FOOD", filter.category);
    }

    @Test
    public void acceptsAllAsAnExplicitUnfilteredChoice() {
        StatsFilter filter = StatsFilter.of("2026-01-01", "2026-12-31", "ALL", "ALL");

        assertEquals("ALL", filter.type);
        assertEquals("ALL", filter.category);
    }

    @Test
    public void acceptsAtMostThreeHundredSixtySixInclusiveDays() {
        StatsFilter.of("2026-01-01", "2027-01-01", "EXPENSE", "ALL");
        StatsFilter.of("2024-01-01", "2024-12-31", "EXPENSE", "ALL");

        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-01-01", "2027-01-02", "EXPENSE", "ALL"));
    }

    @Test
    public void rejectsMissingMalformedOrReversedDates() {
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of(null, "2026-11-09", "EXPENSE", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-03", " ", "EXPENSE", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-02-30", "2026-03-01", "EXPENSE", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-10", "2026-11-09", "EXPENSE", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-1-1", "2026-01-02", "EXPENSE", "FOOD"));
    }

    @Test
    public void rejectsUnknownTypesAndIncompatibleCategories() {
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-01", "2026-11-09", "expense", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-01", "2026-11-09", "TRANSFER", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-01", "2026-11-09", "INCOME", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-01", "2026-11-09", "SAVING", "OTHER"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-01", "2026-11-09", "ALL", "FOOD"));
        assertThrows(IllegalArgumentException.class,
                () -> StatsFilter.of("2026-11-01", "2026-11-09", "EXPENSE", null));
    }
}
