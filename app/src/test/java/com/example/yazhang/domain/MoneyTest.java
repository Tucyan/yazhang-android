package com.example.yazhang.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void parsesOrdinaryDecimalAsFenWithoutLosingPrecision() {
        assertEquals(1230L, Money.parseFen("12.30"));
        assertEquals(1230L, Money.parseFen("12.3"));
        assertEquals(1200L, Money.parseFen("12"));
        assertEquals(1L, Money.parseFen("0.01"));
        assertEquals(10L, Money.parseFen("0.1"));
        assertEquals(99999999L, Money.parseFen("999999.99"));
    }

    @Test
    public void acceptsInclusiveUpperBound() {
        assertEquals(100000000L, Money.parseFen("1000000"));
        assertEquals(100000000L, Money.parseFen("1000000.00"));
    }

    @Test
    public void rejectsZeroNegativeAndAmountAboveUpperBound() {
        String[] invalid = {
                "0", "0.0", "0.00", "-0.00", "-0.01", "-2",
                "1000000.01", "1000001", "999999999999999999999999"
        };

        for (String raw : invalid) {
            assertThrows(raw, IllegalArgumentException.class, () -> Money.parseFen(raw));
        }
    }

    @Test
    public void rejectsNonOrdinaryDecimalSyntaxAndMoreThanTwoFractionDigits() {
        String[] invalid = {
                null, "", " ", " 1.00", "1.00 ", "+1.00", "01.00",
                ".01", "1.", "0.001", "1.234", "1,000.00", "1 000.00",
                "1e2", "1E-2", "NaN", "Infinity", "-Infinity", "abc",
                "１２.３０", "1_000.00", "0x10", "1\n"
        };

        for (String raw : invalid) {
            assertThrows(raw, IllegalArgumentException.class, () -> Money.parseFen(raw));
        }
    }

    @Test
    public void formatsFenAsYuanWithTwoDecimals() {
        assertEquals("0.01", Money.format(1L));
        assertEquals("12.30", Money.format(1230L));
        assertEquals("1000000.00", Money.format(100000000L));
    }

    @Test
    public void invalidAmountMessageCanBeShownDirectly() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Money.parseFen("0.001")
        );

        assertTrue(error.getMessage().contains("金额"));
        assertFalse(error.getMessage().contains("Exception"));
        assertFalse(error.getMessage().contains("\n\tat "));
    }
}
