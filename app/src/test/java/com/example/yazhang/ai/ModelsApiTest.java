package com.example.yazhang.ai;

import com.example.yazhang.ai.config.ModelsApi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModelsApiTest {

    private static JSONObject provider() throws Exception {
        return new JSONObject()
                .put("id", "openai")
                .put("base_url", "https://api.openai.com/v1")
                .put("model_id", "gpt-4o-mini")
                .put("supports_vision", true);
    }

    @Test
    public void modelListKeepsChatModelsAndMergesModalities() throws Exception {
        String body = "{\"data\":["
                + "{\"id\":\"gpt-4o-mini\",\"name\":\"GPT\","
                + "\"architecture\":{\"input_modalities\":[\"text\",\"image\"],"
                + "\"output_modalities\":[\"text\"]}},"
                + "{\"id\":\"text-only\",\"input_modalities\":[\"text\"],"
                + "\"output_modalities\":[\"text\"]},"
                + "{\"id\":\"image-gen\",\"output_modalities\":[\"image\"]},"
                + "{\"id\":\"embed-1\",\"input_modalities\":[\"text\"]},"
                + "{\"id\":\"gpt-4o-mini\",\"input_modalities\":[\"text\"],"
                + "\"output_modalities\":[\"text\"],"
                + "\"effort\":{\"supported_levels\":[\"LOW\",\"low\",\"bogus\"]}},"
                + "{\"id\":\"bad id with spaces\"}"
                + "]}";
        FakeConnection fake = new FakeConnection(body);
        AtomicReference<String> opened = new AtomicReference<>();
        ModelsApi api = new ModelsApi(provider(), "key", url -> {
            opened.set(url.toString());
            return fake;
        });

        JSONArray models = api.fetchModels();

        assertEquals("https://api.openai.com/v1/models", opened.get());
        assertEquals(2, models.length());
        JSONObject first = models.getJSONObject(0);
        assertEquals("gpt-4o-mini", first.getString("id"));
        assertTrue(first.getBoolean("vision_known"));
        assertTrue(first.getBoolean("supports_vision"));
        assertEquals(2, first.getJSONArray("reasoning_levels").length());
        assertEquals("low", first.getJSONArray("reasoning_levels").getString(0));
        JSONObject textOnly = models.getJSONObject(1);
        assertEquals("text-only", textOnly.getString("id"));
        assertFalse(textOnly.getBoolean("supports_vision"));
        assertTrue(textOnly.getBoolean("vision_known"));
    }

    @Test
    public void modelsUrlDropsChatCompletionsSuffix() throws Exception {
        FakeConnection fake = new FakeConnection("{\"data\":[]}");
        AtomicReference<String> opened = new AtomicReference<>();
        JSONObject provider = provider()
                .put("base_url", "https://api.openai.com/v1/chat/completions");
        new ModelsApi(provider, "key", url -> {
            opened.set(url.toString());
            return fake;
        }).fetchModels();
        assertEquals("https://api.openai.com/v1/models", opened.get());
    }

    @Test
    public void textProbeRequiresExactPhrase() throws Exception {
        FakeConnection ok = new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"yazhang text ok\"}}]}");
        JSONObject success = new ModelsApi(provider(), "key", url -> ok).testConnection(false);
        assertTrue(success.getBoolean("success"));

        FakeConnection wrong = new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}");
        JSONObject failed = new ModelsApi(provider(), "key", url -> wrong)
                .testConnection(false);
        assertFalse(failed.getBoolean("success"));
        assertEquals("模型已响应，但文本验证内容不匹配", failed.getString("message"));
    }

    @Test
    public void visionProbeRequiresRedWithoutOtherColors() throws Exception {
        FakeConnection red = new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"Red\"}}]}");
        assertTrue(new ModelsApi(provider(), "key", url -> red)
                .testConnection(true).getBoolean("success"));

        FakeConnection confused = new FakeConnection(
                "{\"choices\":[{\"message\":{\"content\":\"red and blue\"}}]}");
        JSONObject failed = new ModelsApi(provider(), "key", url -> confused)
                .testConnection(true);
        assertFalse(failed.getBoolean("success"));
        assertEquals("视觉探测未能确认图片中的红色，请检查模型视觉能力",
                failed.getString("message"));
    }

    @Test
    public void nonHttpsAndMissingKeyAreRejectedWithoutNetwork() throws Exception {
        AtomicReference<Boolean> opened = new AtomicReference<>(false);
        JSONObject insecure = provider().put("base_url", "http://api.openai.com/v1");
        ModelsApi api = new ModelsApi(insecure, "", url -> {
            opened.set(true);
            return new FakeConnection("{}");
        });
        try {
            api.fetchModels();
            fail("expected rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("API Key"));
        }
        assertFalse(opened.get());

        ModelsApi secureNoKey = new ModelsApi(provider(), "", url -> {
            opened.set(true);
            return new FakeConnection("{}");
        });
        try {
            secureNoKey.fetchModels();
            fail("expected key rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("API Key"));
        }
        assertFalse(opened.get());
    }

    @Test
    public void statusErrorsAreMappedToSafeMessages() throws Exception {
        FakeConnection unauthorized = new FakeConnection("{}");
        unauthorized.responseCode = 401;
        JSONObject result = new ModelsApi(provider(), "key", url -> unauthorized)
                .testConnection(false);
        assertFalse(result.getBoolean("success"));
        assertEquals("模型鉴权失败，请检查 API Key 或账户权限", result.getString("message"));
        assertNotNull(result);
    }
}
