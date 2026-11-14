package com.example.yazhang.domain;

public final class StatsCategoryOptions {

    public static final String ALL_LABEL = "全部分类";

    private static final String[] EXPENSE_CODES = {
            "FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "ENTERTAINMENT", "OTHER"
    };
    private static final String[] EXPENSE_LABELS = {
            "餐饮", "交通", "购物", "居住", "娱乐", "其他"
    };
    private static final String[] INCOME_CODES = {"SALARY"};
    private static final String[] INCOME_LABELS = {"工资"};
    private static final String[] SAVING_CODES = {"SAVING"};
    private static final String[] SAVING_LABELS = {"攒钱"};

    private StatsCategoryOptions() {
    }

    public static String[] codesFor(String type) {
        if ("EXPENSE".equals(type)) {
            return withAllCode(EXPENSE_CODES);
        }
        if ("INCOME".equals(type)) {
            return withAllCode(INCOME_CODES);
        }
        if ("SAVING".equals(type)) {
            return withAllCode(SAVING_CODES);
        }
        return new String[]{StatsFilter.ALL};
    }

    public static String[] labelsFor(String type) {
        if ("EXPENSE".equals(type)) {
            return withAllLabel(EXPENSE_LABELS);
        }
        if ("INCOME".equals(type)) {
            return withAllLabel(INCOME_LABELS);
        }
        if ("SAVING".equals(type)) {
            return withAllLabel(SAVING_LABELS);
        }
        return new String[]{ALL_LABEL};
    }

    private static String[] withAllCode(String[] codes) {
        String[] result = new String[codes.length + 1];
        result[0] = StatsFilter.ALL;
        System.arraycopy(codes, 0, result, 1, codes.length);
        return result;
    }

    private static String[] withAllLabel(String[] labels) {
        String[] result = new String[labels.length + 1];
        result[0] = ALL_LABEL;
        System.arraycopy(labels, 0, result, 1, labels.length);
        return result;
    }
}
