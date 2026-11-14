package com.example.yazhang.ai;

import com.example.yazhang.data.AgentLedger;
import com.example.yazhang.data.EntryEntity;
import com.example.yazhang.data.GoalEntity;
import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.EntryValidator;
import com.example.yazhang.domain.Money;
import com.example.yazhang.domain.StatsFilter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Validates one model action and performs at most one local ledger operation. */
public final class AgentTools {

    private static final int MAX_DETAILS = 50;

    private final AgentLedger ledger;

    public AgentTools(AgentLedger ledger) {
        if (ledger == null) {
            throw new IllegalArgumentException("账本不能为空");
        }
        this.ledger = ledger;
    }

    public AgentReply execute(JSONObject action) {
        String kind = action == null ? "" : action.optString("kind");
        if ("create_entry".equals(kind)) {
            return createEntry(action.optJSONObject("entry"));
        }
        if ("query_statistics".equals(kind)) {
            return queryStatistics(action.optJSONObject("filter"));
        }
        if ("get_saving_progress".equals(kind)) {
            return savingProgress(action);
        }
        if ("clarify".equals(kind) || "final".equals(kind)) {
            String message = action.optString("message", "").trim();
            if (message.isEmpty()) {
                return AgentReply.of(AgentReply.KIND_NOTICE, AgentProtocol.MODEL_ERROR_MESSAGE);
            }
            return AgentReply.of(
                    "clarify".equals(kind) ? AgentReply.KIND_CLARIFY : AgentReply.KIND_FINAL,
                    message);
        }
        return AgentReply.of(AgentReply.KIND_NOTICE, AgentProtocol.MODEL_ERROR_MESSAGE);
    }

    private AgentReply createEntry(JSONObject entry) {
        if (entry == null) {
            return clarify("请告诉我要记的金额、分类和日期，我没有记账。");
        }
        String type = text(entry, "type", "EXPENSE").toUpperCase(Locale.ROOT);
        if (!Arrays.asList("EXPENSE", "INCOME", "SAVING").contains(type)) {
            return clarify("账目类型只支持支出、收入或攒钱，我没有记账。");
        }
        String merchant = nullable(entry.optString("merchant", null));
        String title = nullable(entry.optString("title", null));
        String label = merchant != null ? merchant : title;
        if (!entry.has("amount_fen")) {
            return clarify(amountQuestion(label, type));
        }
        long amountFen = entry.optLong("amount_fen", 0L);
        if (amountFen <= 0 || amountFen > Money.MAX_FEN) {
            return clarify("金额须大于 0 且不超过 1,000,000.00 元，我没有记账。"
                    + "请告诉我正确的金额。");
        }
        String category = text(entry, "category", "");
        if (category.isEmpty()) {
            return clarify(label == null
                    ? "这笔账目的分类还不确定，还缺少分类，我暂时没有记账。"
                    : "这笔" + label + "属于哪个分类？还缺少分类，我暂时没有记账。");
        }
        String rawDate = text(entry, "occurred_on", "");
        if (rawDate.isEmpty()) {
            return clarify(label == null
                    ? "这笔账目是哪一天发生的？还缺少日期，我暂时没有记账。"
                    : "这笔" + label + "是哪一天发生的？还缺少日期，我暂时没有记账。");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(rawDate);
        } catch (DateTimeParseException error) {
            return clarify("日期格式无效，请使用 YYYY-MM-DD；我没有记账。");
        }
        if (!date.toString().equals(rawDate)) {
            return clarify("日期格式无效，请使用 YYYY-MM-DD；我没有记账。");
        }

        EntryDraft draft = new EntryDraft(
                type,
                amountFen,
                category,
                merchant,
                title,
                nullable(entry.optString("detail", null)),
                date
        );
        String invalid = EntryValidator.validate(draft);
        if (invalid != null) {
            return clarify(invalid + "我没有记账。");
        }

        if ("SAVING".equals(type)) {
            GoalEntity goal = goalFor(entry);
            if (goal == null) {
                return clarify("还没有进行中的攒钱目标，所以我没有记账。请先在首页新建目标。");
            }
            return saveSaving(goal, draft, amountFen);
        }
        try {
            long id = ledger.create(draft);
            return new AgentReply(AgentReply.KIND_CREATED,
                    "已经记好了：" + typeLabel(type) + " " + yuan(amountFen)
                            + "（" + categoryLabel(category) + "）。",
                    null,
                    id);
        } catch (IllegalArgumentException error) {
            return clarify(message(error) + "我没有记账。");
        }
    }

    private AgentReply saveSaving(GoalEntity goal, EntryDraft draft, long amountFen) {
        try {
            long id = ledger.addSaving(goal.id, draft);
            long current = goal.currentFen + amountFen;
            int progress = goal.targetFen <= 0 ? 0
                    : (int) Math.max(0L, Math.min(100L, current * 100L / goal.targetFen));
            return new AgentReply(AgentReply.KIND_CREATED,
                    "已经存入「" + goal.name + "」：" + yuan(amountFen)
                            + "，当前 " + yuan(current) + " / " + yuan(goal.targetFen)
                            + "（" + progress + "%）。",
                    null,
                    id);
        } catch (IllegalArgumentException error) {
            return clarify(message(error) + "我没有记账。");
        }
    }

    private GoalEntity goalFor(JSONObject entry) {
        if (entry.has("goal_id")) {
            long id = entry.optLong("goal_id", 0L);
            for (GoalEntity goal : ledger.goals()) {
                if (goal.id == id) {
                    return goal;
                }
            }
            return null;
        }
        return ledger.activeGoal();
    }

    private AgentReply queryStatistics(JSONObject filter) {
        if (filter == null
                || nullable(filter.optString("from", null)) == null
                || nullable(filter.optString("to", null)) == null
                || nullable(filter.optString("type", null)) == null
                || nullable(filter.optString("category", null)) == null) {
            return clarify("请先告诉我统计的日期范围、账目类型和分类，"
                    + "我才能查询并生成图表，也没有生成任何账目。");
        }
        StatsFilter statsFilter;
        try {
            statsFilter = StatsFilter.of(
                    filter.optString("from"),
                    filter.optString("to"),
                    filter.optString("type"),
                    filter.optString("category"));
        } catch (IllegalArgumentException error) {
            return clarify(message(error) + "我没有生成图表。");
        }
        List<EntryEntity> entries = ledger.entries(statsFilter);
        List<CategoryTotal> totals = ledger.stats(statsFilter);
        long count = ledger.countEntries(statsFilter);
        if (count <= 0 || entries.isEmpty()) {
            return AgentReply.of(AgentReply.KIND_STATISTICS,
                    "这段时间没有符合筛选条件的账目，所以没有生成图表。");
        }
        long sum = 0L;
        for (CategoryTotal total : totals) {
            sum += total.totalFen;
        }
        StringBuilder text = new StringBuilder()
                .append("筛选结果：共 ").append(count)
                .append(" 笔，合计 ").append(yuan(sum)).append("。");
        for (CategoryTotal total : totals) {
            text.append('\n').append(categoryLabel(total.category))
                    .append(' ').append(yuan(total.totalFen));
        }
        if (filter.optBoolean("include_entries", false)) {
            text.append("\n明细：");
            int limit = Math.min(entries.size(), MAX_DETAILS);
            for (int i = 0; i < limit; i++) {
                EntryEntity entry = entries.get(i);
                text.append('\n')
                        .append(entry.occurredOn).append(" · ")
                        .append(categoryLabel(entry.category)).append(" · ")
                        .append(entryLabel(entry)).append(" · ")
                        .append(signedYuan(entry));
            }
            if (count > MAX_DETAILS) {
                text.append("\n仅显示前 ").append(MAX_DETAILS).append(" 条明细。");
            }
        }
        return new AgentReply(AgentReply.KIND_STATISTICS, text.toString(),
                chart(totals), 0L);
    }

    private AgentReply savingProgress(JSONObject action) {
        GoalEntity goal = null;
        if (action.has("goal_id")) {
            long id = action.optLong("goal_id", 0L);
            for (GoalEntity item : ledger.goals()) {
                if (item.id == id) {
                    goal = item;
                    break;
                }
            }
        } else {
            goal = ledger.activeGoal();
        }
        if (goal == null) {
            return clarify(ledger.goals().isEmpty()
                    ? "还没有攒钱目标，可以先在首页新建一个，我没有记账。"
                    : "当前没有进行中的攒钱目标，我没有记账。请先在首页新建目标。");
        }
        int progress = goal.targetFen <= 0 ? 0
                : (int) Math.max(0L, Math.min(100L, goal.currentFen * 100L / goal.targetFen));
        return AgentReply.of(AgentReply.KIND_SAVING,
                "攒钱目标「" + goal.name + "」：已存 " + yuan(goal.currentFen)
                        + " / " + yuan(goal.targetFen) + "，进度 " + progress + "%。"
                        + "目标日期 " + goal.targetDate + "。");
    }

    private static String chart(List<CategoryTotal> totals) {
        try {
            JSONArray chart = new JSONArray();
            for (CategoryTotal total : totals) {
                chart.put(new JSONObject()
                        .put("category", total.category)
                        .put("totalFen", total.totalFen));
            }
            return chart.toString();
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static AgentReply clarify(String message) {
        String text = message == null ? "" : message.trim();
        if (!text.contains("没有记账") && !text.contains("没有执行")) {
            text = text + "我没有记账。";
        }
        return AgentReply.of(AgentReply.KIND_CLARIFY, text);
    }

    private static String amountQuestion(String label, String type) {
        if (label != null) {
            return "EXPENSE".equals(type)
                    ? "这笔" + label + "花了多少钱？还缺少金额，我暂时没有记账。"
                    : "这笔" + label + "的金额是多少？还缺少金额，我暂时没有记账。";
        }
        return "这笔" + typeLabel(type) + "的金额是多少？还缺少金额，我暂时没有记账。";
    }

    private static String typeLabel(String type) {
        if ("INCOME".equals(type)) {
            return "收入";
        }
        if ("SAVING".equals(type)) {
            return "攒钱";
        }
        return "支出";
    }

    private static String categoryLabel(String category) {
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
            case "ALL":
                return "全部";
            default:
                return "其他";
        }
    }

    private static String entryLabel(EntryEntity entry) {
        if (entry.merchant != null && !entry.merchant.trim().isEmpty()) {
            return entry.merchant.trim();
        }
        if (entry.title != null && !entry.title.trim().isEmpty()) {
            return entry.title.trim();
        }
        return categoryLabel(entry.category);
    }

    private static String signedYuan(EntryEntity entry) {
        if ("INCOME".equals(entry.type)) {
            return "+" + yuan(entry.amountFen);
        }
        if ("SAVING".equals(entry.type)) {
            return "存 " + yuan(entry.amountFen);
        }
        return "-" + yuan(entry.amountFen);
    }

    private static String yuan(long fen) {
        return "¥" + Money.format(fen);
    }

    private static String text(JSONObject object, String key, String fallback) {
        String value = object == null ? null : nullable(object.optString(key, null));
        return value == null ? fallback : value;
    }

    private static String nullable(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String message(IllegalArgumentException error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty()
                ? "本地账本无法完成这次操作，请重试。" : text.trim();
    }
}
