package com.example.yazhang.ui;

import com.example.yazhang.data.EntryEntity;
import com.example.yazhang.domain.Money;

final class Formats {

    private Formats() {
    }

    static String yuan(long fen) {
        return "¥" + Money.format(fen);
    }

    static String typeLabel(String type) {
        if ("EXPENSE".equals(type)) {
            return "支出";
        }
        if ("INCOME".equals(type)) {
            return "收入";
        }
        if ("SAVING".equals(type)) {
            return "攒钱";
        }
        return "未知类型";
    }

    static String categoryLabel(String category) {
        switch (category == null ? "" : category) {
            case "FOOD":
                return "餐饮";
            case "TRANSPORT":
                return "交通";
            case "SHOPPING":
                return "购物";
            case "HOUSING":
                return "居住";
            case "ENTERTAINMENT":
                return "娱乐";
            case "SALARY":
                return "工资";
            case "SAVING":
                return "攒钱";
            case "OTHER":
                return "其他";
            default:
                return "其他";
        }
    }

    static String entryTitle(EntryEntity entry) {
        if (entry.merchant != null && !entry.merchant.trim().isEmpty()) {
            return entry.merchant.trim();
        }
        if (entry.title != null && !entry.title.trim().isEmpty()) {
            return entry.title.trim();
        }
        return categoryLabel(entry.category);
    }

    static String signedYuan(EntryEntity entry) {
        if ("INCOME".equals(entry.type)) {
            return "+" + yuan(entry.amountFen);
        }
        if ("SAVING".equals(entry.type)) {
            return "存 " + yuan(entry.amountFen);
        }
        return "-" + yuan(entry.amountFen);
    }
}
