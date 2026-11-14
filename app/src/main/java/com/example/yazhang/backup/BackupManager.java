package com.example.yazhang.backup;

import com.example.yazhang.data.EntryEntity;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.EntryValidator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BackupManager {

    private static final int SCHEMA_VERSION = 1;
    private static final String RESTORE_ERROR_PREFIX = "恢复文件";

    private BackupManager() {
    }

    public static JSONObject snapshot(
            List<EntryEntity> entries, JSONObject ignoredCredentials
    ) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        JSONArray array = new JSONArray();
        if (entries != null) {
            for (EntryEntity entry : entries) {
                array.put(toJson(entry));
            }
        }
        root.put("entries", array);
        return root;
    }

    public static String toCsv(List<EntryEntity> entries) {
        StringBuilder csv = new StringBuilder(
                "id,type,amountFen,category,merchant,title,detail,"
                        + "occurredOn,occurredAt,source,status,requestKey,createdAt,updatedAt\n"
        );
        if (entries == null) {
            return csv.toString();
        }
        for (EntryEntity entry : entries) {
            appendCsv(csv, String.valueOf(entry.id));
            csv.append(',');
            appendCsv(csv, entry.type);
            csv.append(',');
            appendCsv(csv, String.valueOf(entry.amountFen));
            csv.append(',');
            appendCsv(csv, entry.category);
            csv.append(',');
            appendCsv(csv, entry.merchant);
            csv.append(',');
            appendCsv(csv, entry.title);
            csv.append(',');
            appendCsv(csv, entry.detail);
            csv.append(',');
            appendCsv(csv, entry.occurredOn);
            csv.append(',');
            appendCsv(csv, String.valueOf(entry.occurredAt));
            csv.append(',');
            appendCsv(csv, entry.source);
            csv.append(',');
            appendCsv(csv, entry.status);
            csv.append(',');
            appendCsv(csv, entry.requestKey);
            csv.append(',');
            appendCsv(csv, String.valueOf(entry.createdAt));
            csv.append(',');
            appendCsv(csv, String.valueOf(entry.updatedAt));
            csv.append('\n');
        }
        return csv.toString();
    }

    public static RestorePlan inspect(String json, Set<String> existingRequestKeys) {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException error) {
            throw invalid("格式不正确，请选择芽账导出的 JSON 备份");
        }

        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw invalid("版本不受支持，请使用 schemaVersion=1 的备份");
        }

        JSONArray array = root.optJSONArray("entries");
        if (array == null) {
            throw invalid("缺少账目数据，请确认导出文件完整");
        }

        Set<String> seen = new HashSet<>();
        if (existingRequestKeys != null) {
            seen.addAll(existingRequestKeys);
        }

        List<EntryEntity> newEntries = new ArrayList<>();
        int skippedCount = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                throw invalid("第 " + (i + 1) + " 条账目格式不正确");
            }
            EntryEntity entry = parseEntry(item, i + 1);
            if (!seen.add(entry.requestKey)) {
                skippedCount++;
                continue;
            }
            newEntries.add(entry);
        }
        return new RestorePlan(newEntries, skippedCount);
    }

    private static EntryEntity parseEntry(JSONObject item, int position) {
        String prefix = RESTORE_ERROR_PREFIX + "第 " + position + " 条账目";

        String requestKey = trimToEmpty(item.optString("requestKey", ""));
        if (requestKey.isEmpty()) {
            throw invalid(prefix + "缺少请求键，无法去重");
        }

        long amountFen;
        try {
            amountFen = item.getLong("amountFen");
        } catch (JSONException error) {
            throw invalid(prefix + "金额格式不正确");
        }

        String occurredOn = trimToEmpty(item.optString("occurredOn", ""));
        LocalDate date;
        try {
            date = LocalDate.parse(occurredOn);
        } catch (DateTimeParseException error) {
            throw invalid(prefix + "日期格式不正确");
        }

        String type = trimToEmpty(item.optString("type", ""));
        String category = trimToEmpty(item.optString("category", ""));
        String merchant = nullableString(item, "merchant");
        String title = nullableString(item, "title");
        String detail = nullableString(item, "detail");

        EntryDraft draft = new EntryDraft(
                type, amountFen, category, merchant, title, detail, date
        );
        String validationError = EntryValidator.validate(draft);
        if (validationError != null) {
            throw invalid(prefix + "无法恢复：" + validationError);
        }

        EntryEntity entry = new EntryEntity();
        entry.id = 0;
        entry.type = type;
        entry.amountFen = amountFen;
        entry.category = category;
        entry.merchant = merchant;
        entry.title = title;
        entry.detail = detail;
        entry.occurredOn = date.toString();
        entry.occurredAt = item.optLong(
                "occurredAt",
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        );
        entry.source = trimToEmpty(item.optString("source", EntryEntity.SOURCE_MANUAL));
        entry.status = EntryEntity.STATUS_POSTED;
        entry.categoryInferred = item.optBoolean("categoryInferred", false);
        entry.confidence = item.optDouble("confidence", 0.0);
        entry.imageSha = nullableString(item, "imageSha");
        entry.requestKey = requestKey;
        entry.goalId = item.has("goalId") && !item.isNull("goalId")
                ? item.optLong("goalId")
                : null;
        entry.createdAt = item.optLong("createdAt", System.currentTimeMillis());
        entry.updatedAt = item.optLong("updatedAt", entry.createdAt);
        return entry;
    }

    private static JSONObject toJson(EntryEntity entry) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", entry.type);
        json.put("amountFen", entry.amountFen);
        json.put("category", entry.category);
        json.put("merchant", entry.merchant == null ? JSONObject.NULL : entry.merchant);
        json.put("title", entry.title == null ? JSONObject.NULL : entry.title);
        json.put("detail", entry.detail == null ? JSONObject.NULL : entry.detail);
        json.put("occurredOn", entry.occurredOn);
        json.put("occurredAt", entry.occurredAt);
        json.put("source", entry.source);
        json.put("status", entry.status);
        json.put("categoryInferred", entry.categoryInferred);
        json.put("confidence", entry.confidence);
        json.put("imageSha", entry.imageSha == null ? JSONObject.NULL : entry.imageSha);
        json.put("requestKey", entry.requestKey);
        json.put("goalId", entry.goalId == null ? JSONObject.NULL : entry.goalId);
        json.put("createdAt", entry.createdAt);
        json.put("updatedAt", entry.updatedAt);
        return json;
    }

    private static void appendCsv(StringBuilder csv, String value) {
        if (value == null) {
            return;
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            csv.append(value);
            return;
        }
        csv.append('"').append(value.replace("\"", "\"\"")).append('"');
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullableString(JSONObject item, String key) {
        if (!item.has(key) || item.isNull(key)) {
            return null;
        }
        return item.optString(key, null);
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(RESTORE_ERROR_PREFIX + "：" + detail);
    }

    public static final class RestorePlan {

        public final List<EntryEntity> newEntries;
        public final int skippedCount;

        RestorePlan(List<EntryEntity> newEntries, int skippedCount) {
            this.newEntries = newEntries;
            this.skippedCount = skippedCount;
        }
    }
}
