package com.example.yazhang.ai;

import com.example.yazhang.ai.config.TokenDanceRecovery;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AiClientTest {

    private static JSONObject provider() throws Exception {
        return new JSONObject()
                .put("id", "openai")
                .put("base_url", "https://api.openai.com/v1")
                .put("model_id", "gpt-4o-mini")
                .put("supports_vision", true)
                .put("reasoning_effort", "off");
    }

    private static AiClient client(FakeConnection fake) throws Exception {
        return new AiClient(provider(), "secret-key", url -> fake);
    }

    @Test
    public void requestKeepsSystemWindowAndImageAndReadsAnswer() throws Exception {
        FakeConnection fake = new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"OK\\\"}\"}}]}");
        JSONArray window = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", "忽略我"))
                .put(new JSONObject().put("role", "assistant").put("content", "上一轮回答"))
                .put(new JSONObject().put("role", "user").put("content", "这次账单"));
        AiClient client = client(fake);

        String answer = client.complete(
                "只输出 JSON", window, "data:image/jpeg;base64,AAAA");

        assertEquals("{\"status\":\"OK\"}", answer);
        assertTrue(fake.disconnected);
        assertTrue(fake.requestMethods.contains("POST"));
        assertFalse(fake.redirectsFollowed);
        assertEquals("Bearer secret-key", fake.requestHeaders.get("Authorization"));
        assertEquals("application/json", fake.requestHeaders.get("Accept"));
        JSONObject payload = new JSONObject(fake.bodyText());
        assertEquals("gpt-4o-mini", payload.getString("model"));
        assertFalse(payload.getBoolean("stream"));
        JSONArray messages = payload.getJSONArray("messages");
        assertEquals(4, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("只输出 JSON", messages.getJSONObject(0).getString("content"));
        assertEquals("assistant", messages.getJSONObject(1).getString("role"));
        assertEquals("这次账单", messages.getJSONObject(2).getString("content"));
        JSONArray content = messages.getJSONObject(3).getJSONArray("content");
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/jpeg;base64,AAAA",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
        assertEquals("json_object",
                payload.getJSONObject("response_format").getString("type"));
    }

    @Test
    public void rejectsNonHttpsBeforeOpeningConnection() throws Exception {
        AtomicReference<Boolean> opened = new AtomicReference<>(false);
        JSONObject provider = provider().put("base_url", "http://api.openai.com/v1");
        AiClient client = new AiClient(provider, "key", url -> {
            opened.set(true);
            return new FakeConnection("{}");
        });
        try {
            client.complete("system", new JSONArray(), null);
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
        assertFalse(opened.get());
    }

    @Test
    public void requiresVisionFlagForImagesAndRejectsOversizeImages() throws Exception {
        JSONObject provider = provider().put("supports_vision", false);
        FakeConnection fake = new FakeConnection("{}");
        AiClient noVision = new AiClient(provider, "key", url -> fake);
        try {
            noVision.complete("system", new JSONArray(), "data:image/jpeg;base64,AAAA");
            fail("expected vision rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("视觉"));
        }
        assertEquals(0, fake.requestBody.size());

        StringBuilder huge = new StringBuilder("data:image/jpeg;base64,");
        for (int i = 0; i < 8 * 1024 * 1024; i++) {
            huge.append('A');
        }
        AiClient withVision = client(new FakeConnection("{}"));
        try {
            withVision.complete("system", new JSONArray(), huge.toString());
            fail("expected size rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("图片过大"));
        }
    }

    @Test
    public void emptyAnswerAndOversizeBodyAreRejected() throws Exception {
        AiClient empty = client(new FakeConnection("{\"choices\":[]}"));
        try {
            empty.complete("system", new JSONArray(), null);
            fail("expected empty answer rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("未返回回答"));
        }

        AiClient blank = client(new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}"));
        try {
            blank.complete("system", new JSONArray(), null);
            fail("expected blank answer rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("空回答"));
        }

        StringBuilder oversized = new StringBuilder("{\"choices\":[{\"message\":{\"content\":\"");
        for (int i = 0; i < 2 * 1024 * 1024 + 32; i++) {
            oversized.append('x');
        }
        oversized.append("\"}}]}");
        AiClient big = client(new FakeConnection(oversized.toString()));
        try {
            big.complete("system", new JSONArray(), null);
            fail("expected size rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("过大"));
        }
    }

    @Test
    public void authQuotaAndRecoveryErrorsAreExplained() throws Exception {
        FakeConnection unauthorized = new FakeConnection("{}");
        unauthorized.responseCode = 401;
        try {
            client(unauthorized).complete("system", new JSONArray(), null);
            fail("expected auth rejection");
        } catch (IOException expected) {
            assertEquals("模型鉴权失败，请检查 API Key 或账户权限", expected.getMessage());
        }

        FakeConnection limited = new FakeConnection("{}");
        limited.responseCode = 429;
        try {
            client(limited).complete("system", new JSONArray(), null);
            fail("expected quota rejection");
        } catch (IOException expected) {
            assertEquals("模型请求过于频繁或额度不足，请稍后重试", expected.getMessage());
        }

        FakeConnection quota = new FakeConnection("{}");
        quota.responseCode = 402;
        quota.responseHeaders.put("TokenDance-Recovery-Action", "top_up_balance");
        JSONObject tokenDance = new JSONObject()
                .put("id", "tokendance")
                .put("base_url", "https://tokendance.space/gateway/v1")
                .put("model_id", "deepseek-v4.1-flash")
                .put("supports_vision", true);
        try {
            new AiClient(tokenDance, "key", url -> quota)
                    .complete("system", new JSONArray(), null);
            fail("expected recovery rejection");
        } catch (TokenDanceRecovery expected) {
            assertEquals(TokenDanceRecovery.Action.TOP_UP_BALANCE, expected.action);
            assertTrue(expected.getMessage().contains("额度不足"));
        }
    }

    @Test
    public void cancelledRequestDoesNotReturnAnswer() throws Exception {
        FakeConnection fake = new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}");
        AiClient client = client(fake);
        client.cancel();
        try {
            client.complete("system", new JSONArray(), null);
            fail("expected cancellation");
        } catch (CancellationException expected) {
            assertNotNull(expected);
        }
    }
}
