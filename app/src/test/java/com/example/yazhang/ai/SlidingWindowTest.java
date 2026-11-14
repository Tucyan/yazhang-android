package com.example.yazhang.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.yazhang.data.AgentMessageEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class SlidingWindowTest {

    @Test
    public void windowKeepsRecentTwelveMessagesWithinBudget() {
        JSONArray window = SlidingWindow.build(messages(20, "普通消息"), 12, 8000);

        assertTrue(window.length() <= 12);
        assertTrue(window.toString().length() <= 8000);
    }

    @Test
    public void windowUsesOldestFirstOrderAndDropsOldest() {
        List<AgentMessageEntity> newestFirst = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            newestFirst.add(0, message("user", "第 " + i + " 条"));
        }

        JSONArray window = SlidingWindow.build(newestFirst, 12, 8000);

        assertEquals(12, window.length());
        assertEquals("第 4 条", window.optJSONObject(0).optString("content"));
        assertEquals("第 15 条", window.optJSONObject(11).optString("content"));
    }

    @Test
    public void oversizedNewestMessageIsTruncatedNotDropped() {
        JSONArray window = SlidingWindow.build(messages(1, repeat("长", 12000)), 12, 400);

        assertEquals(1, window.length());
        assertTrue(window.toString().length() <= 400);
        assertTrue(window.optJSONObject(0).optString("content").length() > 0);
    }

    @Test
    public void controlCharactersAreRemoved() {
        List<AgentMessageEntity> input = new ArrayList<>();
        input.add(message("user", "打车\u0000\u0007 12 元"));

        JSONArray window = SlidingWindow.build(input, 12, 8000);

        assertEquals("打车 12 元", window.optJSONObject(0).optString("content"));
    }

    private static List<AgentMessageEntity> messages(int count, String prefix) {
        List<AgentMessageEntity> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(0, message("user", prefix + " " + i));
        }
        return messages;
    }

    private static AgentMessageEntity message(String role, String content) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.role = role;
        message.content = content;
        message.createdAt = 1L;
        return message;
    }

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(value);
        }
        return text.toString();
    }
}
