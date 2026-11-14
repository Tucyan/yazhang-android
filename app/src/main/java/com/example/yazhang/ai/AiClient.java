package com.example.yazhang.ai;

import com.example.yazhang.ai.config.Endpoints;
import com.example.yazhang.ai.config.ModelOptions;
import com.example.yazhang.ai.config.TokenDanceCatalog;
import com.example.yazhang.ai.config.TokenDanceRecovery;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;

/**
 * Synchronous, bounded OpenAI-compatible transport. Callers own a worker executor and
 * must discard stale callbacks.
 */
public final class AiClient {

    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_CHARS = 8 * 1024 * 1024;
    private static final int MAX_MESSAGE_CHARS = 24000;

    private final JSONObject provider;
    private final String apiKey;
    private final ConnectionFactory factory;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;

    public AiClient(JSONObject provider, String key) {
        this(provider, key, url -> (HttpURLConnection) url.openConnection());
    }

    public AiClient(JSONObject provider, String key, ConnectionFactory factory) {
        this.provider = provider == null ? new JSONObject() : provider;
        this.apiKey = key == null ? "" : key.trim();
        this.factory = factory;
    }

    public String complete(String system, JSONArray window, String imageDataUrl)
            throws Exception {
        return complete(system, window, imageDataUrl, "");
    }

    public String complete(
            String system, JSONArray window, String imageDataUrl, String userText)
            throws Exception {
        checkCancelled();
        String base = provider.optString("base_url");
        if (apiKey.isEmpty()) {
            if (Endpoints.isTokenDance(base)) {
                throw new TokenDanceRecovery(TokenDanceRecovery.Action.REAUTHORIZE_API_KEY);
            }
            throw new IllegalStateException("请先在设置中填写 API Key 和模型");
        }
        String endpoint = Endpoints.chat(base);
        String model = provider.optString("model_id").trim();
        if (model.isEmpty()) {
            throw new IllegalStateException("请先填写模型 ID");
        }
        if (imageDataUrl != null
                && !imageDataUrl.isEmpty()
                && !provider.optBoolean("supports_vision")) {
            throw new IllegalStateException("当前模型未开启视觉能力，请在设置中开启或改用手动记账");
        }
        if (imageDataUrl != null && imageDataUrl.length() > MAX_IMAGE_CHARS) {
            throw new IllegalArgumentException("图片过大，请先裁剪");
        }

        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system));
        for (int i = 0; i < window.length(); i++) {
            JSONObject message = window.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String role = message.optString("role");
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            Object content = message.opt("content");
            if (!(content instanceof String)) {
                continue;
            }
            String text = ((String) content).trim();
            if (text.isEmpty()) {
                continue;
            }
            messages.put(new JSONObject()
                    .put("role", role)
                    .put("content", text.substring(0, Math.min(MAX_MESSAGE_CHARS, text.length()))));
        }
        Object userContent = "";
        if (imageDataUrl != null && !imageDataUrl.isEmpty()) {
            userContent = new JSONArray()
                    .put(new JSONObject().put("type", "text").put("text", ""))
                    .put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", imageDataUrl)));
        } else if (userText != null && !userText.trim().isEmpty()) {
            String text = userText.trim();
            userContent = text.substring(0, Math.min(MAX_MESSAGE_CHARS, text.length()));
        }
        messages.put(new JSONObject().put("role", "user").put("content", userContent));

        JSONObject payload = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", false);
        TokenDanceCatalog.assertChatModel(provider);
        ModelOptions.apply(payload, provider);
        String host = new java.net.URI(endpoint).getHost();
        if ("api.deepseek.com".equalsIgnoreCase(host)
                || "api.openai.com".equalsIgnoreCase(host)) {
            payload.put("response_format", new JSONObject().put("type", "json_object"));
        }
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = factory.open(new URL(endpoint));
        active = connection;
        try {
            checkCancelled();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            if (Endpoints.isTokenDance(base)) {
                connection.setRequestProperty("X-App-URL", "app://yazhang");
            }
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            checkCancelled();
            if (code < 200 || code >= 300) {
                TokenDanceRecovery recovery = TokenDanceRecovery.from(
                        base, connection.getHeaderField("TokenDance-Recovery-Action"));
                if (recovery != null) {
                    throw recovery;
                }
                throw new IOException(statusError(code));
            }
            String raw;
            try (InputStream in = connection.getInputStream()) {
                raw = read(in);
            }
            JSONArray choices = new JSONObject(raw).optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("模型未返回回答，请检查接口协议");
            }
            JSONObject first = choices.optJSONObject(0);
            JSONObject message = first == null ? null : first.optJSONObject("message");
            Object content = message == null ? null : message.opt("content");
            String answer = content instanceof String ? ((String) content).trim() : "";
            if (answer.isEmpty()) {
                throw new IOException("模型返回了空回答，请调整模型设置");
            }
            checkCancelled();
            return answer;
        } catch (SocketTimeoutException timeout) {
            throw new IOException("模型响应超时，请稍后手动重试");
        } finally {
            active = null;
            connection.disconnect();
        }
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection current = active;
        if (current != null) {
            current.disconnect();
        }
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("请求已取消");
        }
    }

    private String read(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            checkCancelled();
            if (out.size() + count > MAX_BODY) {
                throw new IOException("模型返回数据过大，请重试");
            }
            out.write(buffer, 0, count);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private String statusError(int code) {
        if (code == 401 || code == 403) {
            return "模型鉴权失败，请检查 API Key 或账户权限";
        }
        if (code == 429) {
            return "模型请求过于频繁或额度不足，请稍后重试";
        }
        return "模型接口请求失败（HTTP " + code + "），请检查地址和模型 ID";
    }
}
