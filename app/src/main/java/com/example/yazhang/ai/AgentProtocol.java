package com.example.yazhang.ai;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Strict structural validation for the single JSON action returned by the model. */
public final class AgentProtocol {

    public static final String MODEL_ERROR_MESSAGE =
            "AI 返回的内容无法识别，我没有执行任何操作。请重试或换一种说法。";

    private static final int MAX_RAW = 1024 * 1024;
    private static final Set<String> KINDS = new HashSet<>(Arrays.asList(
            "create_entry",
            "query_statistics",
            "get_saving_progress",
            "clarify",
            "final"
    ));

    private AgentProtocol() {
    }

    public static JSONObject parse(String raw) throws Exception {
        String clean = strip(raw);
        if (clean.isEmpty() || clean.length() > MAX_RAW) {
            throw new IllegalArgumentException("模型回复为空或过大");
        }
        JSONObject reply = new JSONObject(clean);
        String kind = required(reply, "kind", 40);
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("模型返回了未知动作");
        }
        JSONObject safe = new JSONObject().put("kind", kind);
        String message = optional(reply, "message", 4000);
        safe.put("message", message == null ? "" : message);

        if ("create_entry".equals(kind)) {
            JSONObject entry = reply.optJSONObject("entry");
            if (entry != null) {
                safe.put("entry", normalizeEntry(entry));
            }
        } else if ("query_statistics".equals(kind)) {
            JSONObject filter = reply.optJSONObject("filter");
            if (filter != null) {
                safe.put("filter", normalizeFilter(filter));
            }
        } else if ("get_saving_progress".equals(kind)) {
            Long goalId = optionalLong(reply, "goal_id");
            if (goalId != null) {
                safe.put("goal_id", goalId);
            }
        } else if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("模型没有给出回答");
        }
        return safe;
    }

    private static JSONObject normalizeEntry(JSONObject entry) throws Exception {
        JSONObject safe = new JSONObject();
        String type = optional(entry, "type", 20);
        if (type != null) {
            safe.put("type", type);
        }
        Long amount = optionalLong(entry, "amount_fen");
        if (amount != null) {
            safe.put("amount_fen", amount);
        }
        String category = optional(entry, "category", 40);
        if (category != null) {
            safe.put("category", category);
        }
        for (String field : new String[]{"merchant", "title", "detail"}) {
            String value = optional(entry, field, "detail".equals(field) ? 2000 : 120);
            if (value != null) {
                safe.put(field, value);
            }
        }
        String occurredOn = optional(entry, "occurred_on", 20);
        if (occurredOn != null) {
            safe.put("occurred_on", occurredOn);
        }
        Long goalId = optionalLong(entry, "goal_id");
        if (goalId != null) {
            safe.put("goal_id", goalId);
        }
        return safe;
    }

    private static JSONObject normalizeFilter(JSONObject filter) throws Exception {
        JSONObject safe = new JSONObject();
        for (String field : new String[]{"from", "to", "type", "category"}) {
            String value = optional(filter, field, 40);
            if (value != null) {
                safe.put(field, value);
            }
        }
        Object include = filter.opt("include_entries");
        if (include != null && include != JSONObject.NULL) {
            if (!(include instanceof Boolean)) {
                throw new IllegalArgumentException("明细开关格式无效");
            }
            safe.put("include_entries", include);
        }
        return safe;
    }

    private static String required(JSONObject object, String key, int max) throws Exception {
        String value = optional(object, key, max);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("模型缺少字段：" + key);
        }
        return value;
    }

    private static String optional(JSONObject object, String key, int max) throws Exception {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("模型字段格式无效：" + key);
        }
        String text = ((String) value).trim();
        if (text.length() > max) {
            throw new IllegalArgumentException("模型字段过长：" + key);
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n') {
                throw new IllegalArgumentException("模型字段包含不可见字符");
            }
        }
        return text.isEmpty() ? null : text;
    }

    private static Long optionalLong(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("模型字段格式无效：" + key);
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.floor(number)
                || number < Long.MIN_VALUE || number > Long.MAX_VALUE) {
            throw new IllegalArgumentException("模型字段格式无效：" + key);
        }
        return (long) number;
    }

    private static String strip(String raw) {
        String text = raw == null ? "" : raw.trim();
        text = text.replaceFirst("(?i)^```(?:json)?\\s*", "");
        text = text.replaceFirst("\\s*```$", "");
        return text.trim();
    }
}
