package com.example.yazhang.ai;

import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.Money;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Strictly validates one image-recognition reply; model text never becomes a command. */
public final class AiProtocol {

    public static final String MODEL_ERROR_MESSAGE =
            "AI 返回的内容不完整，这次没有自动入账。图片仍保留，可以重试或手动填写。";

    private static final String AMOUNT_ERROR_MESSAGE =
            "图片中的金额无法可靠确认，所以没有自动记账。请手动核对金额后保存。";
    private static final String MULTI_PAYMENT_MESSAGE =
            "图片里有多笔订单，我不能替你选择。请改用单笔账单详情，或手动记一笔。";
    private static final String NOT_PAYMENT_MESSAGE =
            "没有找到明确的付款信息，所以没有记账。你可以换一张账单截图，或改用手动记账。";
    private static final String NEED_DATE_MESSAGE = "日期需要你确认一下，选好日期后再保存。";
    private static final String LOW_CONFIDENCE_MESSAGE =
            "AI 对这张图片的识别不够确定，所以没有自动记账。请核对后手动保存。";
    private static final String REVIEW_MESSAGE =
            "这张图片的付款信息需要你核对，所以我暂时没有记账。请补全金额、日期和分类后手动保存。";
    private static final String INJECTION_MESSAGE =
            "图片中包含要求 AI 执行操作的文字，我只把它当作图片内容并忽略了这些要求，也没有记账。";

    private static final double AUTO_SAVE_CONFIDENCE = 0.8;
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList(
            "kind", "status", "amount_fen", "merchant", "category", "occurred_at", "confidence"
    ));
    private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList(
            "FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "ENTERTAINMENT", "OTHER"
    ));
    private static final Pattern INJECTION = Pattern.compile(
            "忽略(?:以上|之前|此前|前面|上面|所有|全部|你的)?(?:的)?(?:规则|指令|提示|要求)"
                    + "|(?:调用|执行|使用)(?:工具|函数|接口|命令)"
                    + "|(?:把|将)?余额(?:转|打|汇)"
                    + "|转账"
                    + "|删除(?:所有|全部)?(?:账目|数据|记录)"
                    + "|(?i)ignore\\s+(?:all\\s+)?(?:previous|above|prior)\\s+instructions"
                    + "|(?i)(?:call|invoke|use)\\s+(?:a\\s+)?(?:tool|function)s?"
                    + "|(?i)system\\s+prompt"
                    + "|(?i)transfer\\s+(?:money|funds|balance)"
    );

    private AiProtocol() {
    }

    public static ImageResult image(String raw) {
        JSONObject object = parseObject(raw);
        if (object == null || !hasOnlyKnownFields(object)) {
            return modelError();
        }
        String kind = stringField(object, "kind");
        String status = stringField(object, "status");
        String merchant = stringField(object, "merchant");
        String category = stringField(object, "category");
        String occurred = stringField(object, "occurred_at");
        if (!"receipt".equals(kind) || status == null) {
            return modelError();
        }
        status = status.toUpperCase(Locale.ROOT);

        if (containsInstruction(kind)
                || containsInstruction(status)
                || containsInstruction(merchant)
                || containsInstruction(category)
                || containsInstruction(occurred)) {
            return rejected(INJECTION_MESSAGE);
        }

        if ("MULTI_PAYMENT".equals(status)) {
            return rejected(MULTI_PAYMENT_MESSAGE);
        }
        if ("NOT_PAYMENT".equals(status)) {
            return rejected(NOT_PAYMENT_MESSAGE);
        }
        if (!"OK".equals(status) && !"NEEDS_REVIEW".equals(status)) {
            return modelError();
        }
        boolean review = "NEEDS_REVIEW".equals(status);

        Long parsedAmount = integerField(object, "amount_fen");
        if (parsedAmount != null && (parsedAmount <= 0 || parsedAmount > Money.MAX_FEN)) {
            return rejected(AMOUNT_ERROR_MESSAGE);
        }
        if (parsedAmount == null && !review) {
            return rejected(AMOUNT_ERROR_MESSAGE);
        }
        long amountFen = parsedAmount == null ? 0L : parsedAmount;

        String normalizedCategory = category == null
                ? null
                : category.trim().toUpperCase(Locale.ROOT);
        if (normalizedCategory != null && !CATEGORIES.contains(normalizedCategory)) {
            return modelError();
        }
        if (normalizedCategory == null && !review) {
            return modelError();
        }

        String cleanMerchant = merchant == null ? null : merchant.trim();
        if (cleanMerchant != null && cleanMerchant.isEmpty()) {
            cleanMerchant = null;
        } else if (cleanMerchant != null
                && (cleanMerchant.length() > 120 || hasControlCharacter(cleanMerchant))) {
            return modelError();
        }

        double confidence = numberField(object, "confidence", 0.0);
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            return modelError();
        }

        LocalDate occurredOn = null;
        if (occurred != null && !occurred.trim().isEmpty()) {
            try {
                occurredOn = LocalDate.parse(occurred.trim());
            } catch (DateTimeParseException invalid) {
                return modelError();
            }
            if (occurredOn.isAfter(LocalDate.now())) {
                return modelError();
            }
        }

        if (review) {
            return new ImageResult(true, amountFen, normalizedCategory, cleanMerchant,
                    occurredOn == null ? null : occurredOn.toString(), confidence, REVIEW_MESSAGE);
        }
        if (occurredOn == null) {
            return new ImageResult(true, amountFen, normalizedCategory, cleanMerchant,
                    null, confidence, NEED_DATE_MESSAGE);
        }
        if (confidence < AUTO_SAVE_CONFIDENCE) {
            return new ImageResult(true, amountFen, normalizedCategory, cleanMerchant,
                    occurredOn.toString(), confidence, LOW_CONFIDENCE_MESSAGE);
        }
        EntryDraft draft = new EntryDraft(
                "EXPENSE", amountFen, normalizedCategory, cleanMerchant, null, null, occurredOn);
        return new ImageResult(true, draft, amountFen, normalizedCategory, cleanMerchant,
                occurredOn.toString(), confidence, "识别成功。");
    }

    private static JSONObject parseObject(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (Exception invalid) {
            return null;
        }
    }

    private static boolean hasOnlyKnownFields(JSONObject object) {
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                return false;
            }
            Object value = object.opt(name);
            if (value != null
                    && value != JSONObject.NULL
                    && !(value instanceof String)
                    && !(value instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    private static String stringField(JSONObject object, String name) {
        Object value = object.opt(name);
        return value instanceof String ? (String) value : null;
    }

    private static Long integerField(JSONObject object, String name) {
        Object value = object.opt(name);
        if (!(value instanceof Number)) {
            return null;
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number)
                || number != Math.rint(number)
                || number < Long.MIN_VALUE
                || number > Long.MAX_VALUE) {
            return null;
        }
        return (long) number;
    }

    private static double numberField(JSONObject object, String name, double fallback) {
        Object value = object.opt(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean containsInstruction(String value) {
        return value != null && INJECTION.matcher(value).find();
    }

    private static boolean hasControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static ImageResult rejected(String message) {
        return new ImageResult(false, message);
    }

    private static ImageResult modelError() {
        return rejected(MODEL_ERROR_MESSAGE);
    }

    public static final class ImageResult {
        public final boolean accepted;
        public final boolean autoSave;
        public final EntryDraft draft;
        public final long amountFen;
        public final String category;
        public final String merchant;
        public final String occurredOn;
        public final double confidence;
        public final String userMessage;

        ImageResult(boolean accepted, String userMessage) {
            this(accepted, null, 0L, null, null, null, 0.0, userMessage);
        }

        ImageResult(boolean accepted, long amountFen, String category, String merchant,
                String occurredOn, double confidence, String userMessage) {
            this(accepted, null, amountFen, category, merchant, occurredOn, confidence,
                    userMessage);
        }

        ImageResult(boolean accepted, EntryDraft draft, long amountFen, String category,
                String merchant, String occurredOn, double confidence, String userMessage) {
            this.accepted = accepted;
            this.autoSave = accepted && draft != null;
            this.draft = draft;
            this.amountFen = amountFen;
            this.category = category;
            this.merchant = merchant;
            this.occurredOn = occurredOn;
            this.confidence = confidence;
            this.userMessage = userMessage;
        }
    }
}
