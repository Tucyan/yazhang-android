package com.example.yazhang.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public final class StatsFilter {

    public static final String ALL = "ALL";
    private static final int MAX_DAYS = 366;

    public final String from;
    public final String to;
    public final String type;
    public final String category;

    private StatsFilter(String from, String to, String type, String category) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.category = category;
    }

    public static StatsFilter of(String from, String to, String type, String category) {
        String cleanFrom = requireDate("开始日期", from);
        String cleanTo = requireDate("结束日期", to);
        LocalDate start = LocalDate.parse(cleanFrom);
        LocalDate end = LocalDate.parse(cleanTo);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("统计区间最多 366 天，请缩小日期范围");
        }

        String cleanType = requireCode("账目类型", type);
        String cleanCategory = requireCode("分类", category);
        validateTypeAndCategory(cleanType, cleanCategory);
        return new StatsFilter(cleanFrom, cleanTo, cleanType, cleanCategory);
    }

    private static String requireDate(String label, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        try {
            LocalDate parsed = LocalDate.parse(clean);
            if (!parsed.toString().equals(clean)) {
                throw new IllegalArgumentException(label + "格式无效，请使用 YYYY-MM-DD");
            }
            return clean;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + "格式无效，请使用 YYYY-MM-DD", e);
        }
    }

    private static String requireCode(String label, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return clean;
    }

    private static void validateTypeAndCategory(String type, String category) {
        if (ALL.equals(type)) {
            if (!ALL.equals(category)) {
                throw new IllegalArgumentException("选择全部类型时，分类也只能选择全部");
            }
            return;
        }
        if (!EntryValidator.isValidType(type)) {
            throw new IllegalArgumentException("统计类型无效，请选择支出、收入或攒钱");
        }
        if (ALL.equals(category)) {
            return;
        }
        if (!EntryValidator.isKnownCategory(category)
                || !EntryValidator.categoryMatchesType(type, category)) {
            throw new IllegalArgumentException("分类与统计类型不符，请重新选择");
        }
    }
}
