package com.example.yazhang.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class Money {

    public static final long MAX_FEN = 100000000L;
    private static final Pattern PLAIN_DECIMAL = Pattern.compile(
            "(?:0|[1-9]\\d{0,6})(?:\\.\\d{1,2})?"
    );
    private static final String ERROR_MESSAGE =
            "金额须为 0.01 至 1,000,000.00 元，最多两位小数";

    private Money() {
    }

    public static String format(long fen) {
        return BigDecimal.valueOf(fen)
                .movePointLeft(2)
                .setScale(2, java.math.RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    public static long parseFen(String raw) {
        if (raw == null || !PLAIN_DECIMAL.matcher(raw).matches()) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }

        long fen = new BigDecimal(raw).movePointRight(2).longValueExact();
        if (fen <= 0 || fen > MAX_FEN) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
        return fen;
    }
}
