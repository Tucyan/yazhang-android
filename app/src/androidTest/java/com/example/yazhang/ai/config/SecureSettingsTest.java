package com.example.yazhang.ai.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SecureSettingsTest {

    private Context context;
    private SecureSettings settings;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("provider_private", Context.MODE_PRIVATE)
                .edit().clear().commit();
        settings = new SecureSettings(context);
    }

    @Test
    public void apiKeyIsEncryptedAndDestinationChangesRequireANewKey() throws Exception {
        JSONObject config = new JSONObject()
                .put("name", "测试服务")
                .put("base_url", "https://api.example.com/v1")
                .put("model_id", "test-model")
                .put("supports_vision", true);

        String id = settings.addProvider(config, "sk-secret-value");

        assertEquals("sk-secret-value", settings.getApiKey(id));
        assertTrue(settings.getProviders().toString().contains("测试服务"));
        assertFalse(settings.getProviders().toString().contains("sk-secret-value"));
        SharedPreferences preferences =
                context.getSharedPreferences("provider_private", Context.MODE_PRIVATE);
        String stored = preferences.getString("key_" + id, "");
        assertTrue(stored.length() > 0);
        assertFalse(stored.contains("sk-secret-value"));

        JSONObject moved = new JSONObject()
                .put("id", id)
                .put("base_url", "https://other.example.com/v1");
        assertThrows(IllegalArgumentException.class,
                () -> settings.saveProvider(moved, null));
    }

}
