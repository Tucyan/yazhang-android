package com.example.yazhang.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.LocalDate;
import org.junit.Test;

public class EntryValidatorTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    public void acceptsEveryExpenseCategory() {
        String[] categories = {
                "FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "ENTERTAINMENT", "OTHER"
        };

        for (String category : categories) {
            assertNull(category, EntryValidator.validate(draft("EXPENSE", 1230L, category, TODAY)));
        }
    }

    @Test
    public void acceptsOnlyMatchingIncomeAndSavingCategories() {
        assertNull(EntryValidator.validate(draft("INCOME", 500000L, "SALARY", TODAY)));
        assertNull(EntryValidator.validate(draft("SAVING", 500000L, "SAVING", TODAY)));

        assertInvalid(draft("INCOME", 500000L, "FOOD", TODAY));
        assertInvalid(draft("INCOME", 500000L, "SAVING", TODAY));
        assertInvalid(draft("SAVING", 500000L, "SALARY", TODAY));
        assertInvalid(draft("SAVING", 500000L, "OTHER", TODAY));
    }

    @Test
    public void rejectsUnsupportedTypesAndCategoriesIncludingWrongCase() {
        String[] invalidTypes = {null, "", "expense", "TRANSFER", " EXPENSE"};
        for (String type : invalidTypes) {
            assertInvalid(draft(type, 1230L, "FOOD", TODAY));
        }

        String[] invalidCategories = {null, "", "food", "SALARY", "SAVING", "UNKNOWN"};
        for (String category : invalidCategories) {
            assertInvalid(draft("EXPENSE", 1230L, category, TODAY));
        }
    }

    @Test
    public void enforcesPositiveAmountNotAboveOneMillionYuan() {
        assertNull(EntryValidator.validate(draft("EXPENSE", 1L, "FOOD", TODAY)));
        assertNull(EntryValidator.validate(draft("EXPENSE", 100000000L, "FOOD", TODAY)));

        assertInvalid(draft("EXPENSE", 0L, "FOOD", TODAY));
        assertInvalid(draft("EXPENSE", -1L, "FOOD", TODAY));
        assertInvalid(draft("EXPENSE", 100000001L, "FOOD", TODAY));
        assertInvalid(draft("EXPENSE", Long.MAX_VALUE, "FOOD", TODAY));
    }

    @Test
    public void acceptsHistoryTodayAndTomorrowButRejectsLaterFutureDates() {
        assertNull(EntryValidator.validate(draft("EXPENSE", 1230L, "FOOD", LocalDate.of(2000, 1, 1))));
        assertNull(EntryValidator.validate(draft("EXPENSE", 1230L, "FOOD", LocalDate.of(2024, 2, 29))));
        assertNull(EntryValidator.validate(draft("EXPENSE", 1230L, "FOOD", TODAY)));
        assertNull(EntryValidator.validate(draft("EXPENSE", 1230L, "FOOD", TODAY.plusDays(1))));

        assertInvalid(draft("EXPENSE", 1230L, "FOOD", null));
        assertInvalid(draft("EXPENSE", 1230L, "FOOD", TODAY.plusDays(2)));
        assertInvalid(draft("EXPENSE", 1230L, "FOOD", TODAY.plusYears(1)));
        assertThrows(DateTimeException.class, () -> LocalDate.of(2023, 2, 29));
    }

    @Test
    public void optionalMerchantAndTitleMayBeNullAndUseTrimmedLengthLimit() {
        assertNull(EntryValidator.validate(detailed(null, null, null)));
        assertNull(EntryValidator.validate(detailed("   ", "  商家  ", null)));

        String merchant120 = "  " + repeat('商', 120) + "  ";
        String title120 = "  " + repeat('题', 120) + "  ";
        assertNull(EntryValidator.validate(detailed(merchant120, title120, null)));

        assertInvalid(detailed(" " + repeat('商', 121) + " ", null, null));
        assertInvalid(detailed(null, " " + repeat('题', 121) + " ", null));
    }

    @Test
    public void detailMayBeNullAndHasTwoThousandCharacterLimit() {
        assertNull(EntryValidator.validate(detailed(null, null, repeat('详', 2000))));
        assertInvalid(detailed(null, null, repeat('详', 2001)));
    }

    @Test
    public void rejectsControlCharactersInAllFreeTextFields() {
        String[] controls = {"\u0000", "\u0001", "\n", "\t", "\u007f", "\u0085"};
        for (String control : controls) {
            assertInvalid(detailed("商家" + control, null, null));
            assertInvalid(detailed(null, "标题" + control, null));
            assertInvalid(detailed(null, null, "详情" + control));
        }
    }

    @Test
    public void rejectsMissingDraftAndReturnsDisplayableActionableMessages() {
        assertInvalid(null);

        String amountMessage = assertInvalid(draft("EXPENSE", 0L, "FOOD", TODAY));
        String dateMessage = assertInvalid(draft("EXPENSE", 1230L, "FOOD", null));
        String merchantMessage = assertInvalid(detailed(repeat('商', 121), null, null));
        assertTrue(amountMessage.contains("金额"));
        assertTrue(dateMessage.contains("日期"));
        assertTrue(merchantMessage.contains("商家"));
    }

    private static EntryDraft draft(
            String type, long amountFen, String category, LocalDate occurredOn
    ) {
        return new EntryDraft(type, amountFen, category, null, null, null, occurredOn);
    }

    private static EntryDraft detailed(String merchant, String title, String detail) {
        return new EntryDraft("EXPENSE", 1230L, "FOOD", merchant, title, detail, TODAY);
    }

    private static String assertInvalid(EntryDraft draft) {
        String message = EntryValidator.validate(draft);
        assertNotNull(message);
        assertFalse(message.trim().isEmpty());
        assertTrue(message.matches(".*[\\u4e00-\\u9fff].*"));
        assertFalse(message.contains("Exception"));
        assertFalse(message.contains("\n\tat "));
        return message;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
