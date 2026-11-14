package com.example.yazhang.ai;

import com.example.yazhang.data.AgentMessageEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds the bounded, chronological request window from newest-first history. */
public final class SlidingWindow {

    private SlidingWindow() {
    }

    public static JSONArray build(
            List<AgentMessageEntity> newestFirst, int maxMessages, int maxChars) {
        JSONArray result = new JSONArray();
        if (newestFirst == null || maxMessages <= 0 || maxChars <= 2) {
            return result;
        }
        List<JSONObject> selected = new ArrayList<>();
        int used = 2;
        for (AgentMessageEntity message : newestFirst) {
            if (selected.size() >= maxMessages) {
                break;
            }
            if (message == null) {
                continue;
            }
            String role = message.role == null ? "" : message.role.trim();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = clean(message.content);
            if (content.isEmpty()) {
                continue;
            }
            JSONObject item = object(role, content);
            int size = item.toString().length();
            int separator = selected.isEmpty() ? 0 : 1;
            if (used + separator + size <= maxChars) {
                selected.add(item);
                used += separator + size;
                continue;
            }
            if (selected.isEmpty()) {
                JSONObject truncated = fit(role, content, maxChars - 2);
                if (truncated != null) {
                    selected.add(truncated);
                }
            }
            break;
        }
        Collections.reverse(selected);
        for (JSONObject item : selected) {
            result.put(item);
        }
        return result;
    }

    private static JSONObject fit(String role, String content, int maxChars) {
        if (maxChars <= 0) {
            return null;
        }
        String value = content;
        JSONObject item = object(role, value);
        while (item.toString().length() > maxChars && value.length() > 0) {
            int cut = Math.max(0, value.length() - Math.max(1, value.length() / 8));
            if (cut == value.length()) {
                cut = value.length() - 1;
            }
            if (cut > 0 && Character.isHighSurrogate(value.charAt(cut - 1))) {
                cut--;
            }
            value = value.substring(0, cut);
            item = object(role, value);
        }
        return value.isEmpty() ? null : item;
    }

    private static JSONObject object(String role, String content) {
        try {
            return new JSONObject().put("role", role).put("content", content);
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String clean(String value) {
        String text = value == null ? "" : value;
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isISOControl(c)) {
                safe.append(c);
            }
        }
        return safe.toString().trim();
    }
}
