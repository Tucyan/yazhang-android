package com.example.yazhang.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class EntryValidator {

    private static final Set<String> TYPES = new HashSet<>(
            Arrays.asList("EXPENSE", "INCOME", "SAVING")
    );
    private static final Set<String> EXPENSE_CATEGORIES = new HashSet<>(Arrays.asList(
            "FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "ENTERTAINMENT", "OTHER"
    ));

    private EntryValidator() {
    }

    public static String validate(EntryDraft draft) {
        if (draft == null) {
            return "账目草稿为空，请补充后再保存";
        }
        if (!isValidType(draft.type)) {
            return "账目类型只支持支出、收入或攒钱，请修改后再保存";
        }
        if (draft.amountFen <= 0 || draft.amountFen > Money.MAX_FEN) {
            return "金额须大于 0 且不超过 1,000,000.00 元，请修改后再保存";
        }
        if (!categoryMatchesType(draft.type, draft.category)) {
            return "分类与账目类型不符，请选择正确的分类后再保存";
        }
        if (draft.occurredOn == null) {
            return "账目日期不能为空，请选择日期后再保存";
        }
        if (draft.occurredOn.isAfter(LocalDate.now().plusDays(1))) {
            return "账目日期最多只能选择到明天，请修改后再保存";
        }

        String textError = validateText(draft.merchant, "商家", 120, true);
        if (textError != null) {
            return textError;
        }
        textError = validateText(draft.title, "标题", 120, true);
        if (textError != null) {
            return textError;
        }
        return validateText(draft.detail, "详情", 2000, false);
    }

    static boolean isValidType(String type) {
        return TYPES.contains(type);
    }

    static boolean isKnownCategory(String category) {
        return EXPENSE_CATEGORIES.contains(category)
                || "SALARY".equals(category)
                || "SAVING".equals(category);
    }

    static boolean categoryMatchesType(String type, String category) {
        if ("EXPENSE".equals(type)) {
            return EXPENSE_CATEGORIES.contains(category);
        }
        if ("INCOME".equals(type)) {
            return "SALARY".equals(category);
        }
        return "SAVING".equals(category);
    }

    private static String validateText(
            String value, String fieldName, int maxLength, boolean trimBeforeLengthCheck
    ) {
        if (value == null) {
            return null;
        }
        if (containsControlCharacter(value)) {
            return fieldName + "不能包含控制字符，请删除不可见字符后再保存";
        }
        int length = trimBeforeLengthCheck ? value.trim().length() : value.length();
        if (length > maxLength) {
            return fieldName + "最多 " + maxLength + " 字，请精简后再保存";
        }
        return null;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
