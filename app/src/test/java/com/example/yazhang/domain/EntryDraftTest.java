package com.example.yazhang.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import org.junit.Test;

public class EntryDraftTest {

    @Test
    public void expenseFactoryCreatesExpenseWithOptionalFieldsEmpty() {
        LocalDate date = LocalDate.of(2026, 11, 8);

        EntryDraft draft = EntryDraft.expense(1230L, "FOOD", date);

        assertEquals("EXPENSE", draft.type);
        assertEquals(1230L, draft.amountFen);
        assertEquals("FOOD", draft.category);
        assertNull(draft.merchant);
        assertNull(draft.title);
        assertNull(draft.detail);
        assertEquals(date, draft.occurredOn);
    }

    @Test
    public void constructorKeepsEveryDraftField() {
        LocalDate date = LocalDate.of(2026, 11, 8);

        EntryDraft draft = new EntryDraft(
                "EXPENSE", 1880L, "TRANSPORT", "  打车平台  ", "回家", "晚间", date
        );

        assertEquals("EXPENSE", draft.type);
        assertEquals(1880L, draft.amountFen);
        assertEquals("TRANSPORT", draft.category);
        assertEquals("  打车平台  ", draft.merchant);
        assertEquals("回家", draft.title);
        assertEquals("晚间", draft.detail);
        assertEquals(date, draft.occurredOn);
    }
}
