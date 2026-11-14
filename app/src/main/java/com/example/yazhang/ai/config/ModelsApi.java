package com.example.yazhang.ai.config;

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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

/** Bounded model catalog discovery and behavior-verified connection probes. */
public class ModelsApi {

    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final String RED_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAb0lEQVR4nO3PAQkAAAyEwO9feoshgnABdLep8QUNyPEFDcjxBQ3I8QUNyPEFDcjxBQ3I8QUNyPEFDcjxBQ3I8QUNyPEFDcjxBQ3I8QUNyPEFDcjxBQ3I8QUNyPEFDcjxBQ3I8QUNyPEFDcjxBQ3IPanc8OLDQitxAAAAAElFTkSuQmCC";
    private final JSONObject provider;
    private final String key;
    private final ConnectionFactory factory;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;

    public ModelsApi(JSONObject provider, String key) {
        this(provider, key, url -> (HttpURLConnection) url.openConnection());
    }

    public ModelsApi(JSONObject provider, String key, ConnectionFactory factory) {
        this.provider = provider == null ? new JSONObject() : provider;
        this.key = key == null ? "" : key.trim();
        this.factory = factory;
    }

    public JSONArray fetchModels() throws Exception {
        String base = provider.optString("base_url");
        if (key.isEmpty() && !Endpoints.isTokenDance(base)) {
            throw new IllegalStateException("请先填写 API Key");
        }
        JSONObject response =
                new JSONObject(request(Endpoints.models(base), "GET", null, 30000, 30000));
        JSONArray data = response.optJSONArray("data");
        if (data == null) {
            throw new IOException("模型列表响应格式不正确");
        }
        Map<String, JSONObject> unique = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id").trim();
            if (id.isEmpty()
                    || id.length() > 256
                    || !id.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")
                    || excluded(id)) {
                continue;
            }
            JSONArray protocols = item.optJSONArray("supported_protocols");
            if (protocols != null && !contains(protocols, "openai:chat-completions")) {
                continue;
            }
            JSONObject architecture = item.optJSONObject("architecture");
            JSONObject metadata = item.optJSONObject("metadata");
            JSONArray input = item.optJSONArray("input_modalities");
            if (input == null && metadata != null) {
                input = metadata.optJSONArray("input_modalities");
            }
            if (input == null && architecture != null) {
                input = architecture.optJSONArray("input_modalities");
            }
            JSONArray output = item.optJSONArray("output_modalities");
            if (output == null && metadata != null) {
                output = metadata.optJSONArray("output_modalities");
            }
            if (output == null && architecture != null) {
                output = architecture.optJSONArray("output_modalities");
            }
            boolean known = input != null;
            boolean vision = known && contains(input, "image");
            if (output != null && !contains(output, "text")) {
                continue;
            }
            JSONObject normalized = new JSONObject()
                    .put("id", id)
                    .put("name", nonempty(item.optString("name"), id))
                    .put("vision_known", known)
                    .put("supports_vision", vision);
            JSONObject effort = item.optJSONObject("effort");
            if (effort == null && metadata != null) {
                effort = metadata.optJSONObject("effort");
            }
            JSONArray levels = effort == null ? null : effort.optJSONArray("supported_levels");
            if (levels != null) {
                normalized.put("reasoning_levels", cleanLevels(levels));
            }
            JSONObject previous = unique.get(id);
            if (previous == null) {
                unique.put(id, normalized);
            } else {
                JSONObject merged = previous.optBoolean("vision_known")
                        || !normalized.optBoolean("vision_known")
                        ? previous
                        : normalized;
                if (!merged.has("reasoning_levels")
                        && (previous.has("reasoning_levels")
                        || normalized.has("reasoning_levels"))) {
                    JSONObject withEffort = new JSONObject(merged.toString());
                    withEffort.put("reasoning_levels",
                            previous.has("reasoning_levels")
                                    ? previous.getJSONArray("reasoning_levels")
                                    : normalized.getJSONArray("reasoning_levels"));
                    merged = withEffort;
                }
                unique.put(id, merged);
            }
        }
        JSONArray result = new JSONArray();
        for (JSONObject item : unique.values()) {
            result.put(item);
        }
        return result;
    }

    public JSONObject testConnection(boolean vision) {
        long started = System.currentTimeMillis();
        try {
            if (key.isEmpty()) {
                throw new IllegalStateException("请先填写 API Key");
            }
            String model = provider.optString("model_id").trim();
            if (model.isEmpty()) {
                throw new IllegalStateException("请先填写模型 ID");
            }
            JSONObject user = new JSONObject().put("role", "user");
            if (vision) {
                user.put("content", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "text")
                                .put("text",
                                        "Look at the image and identify its dominant color. "
                                                + "Reply with exactly one color name."))
                        .put(new JSONObject()
                                .put("type", "image_url")
                                .put("image_url", new JSONObject()
                                        .put("url", "data:image/png;base64," + RED_PNG))));
            } else {
                user.put("content",
                        "Reply with exactly this phrase and nothing else: YAZHANG TEXT OK");
            }
            JSONObject payload = new JSONObject()
                    .put("model", model)
                    .put("messages", new JSONArray().put(user))
                    .put("stream", false)
                    .put("max_tokens",
                            "off".equals(provider.optString("reasoning_effort", "off"))
                                    ? 128
                                    : 512);
            TokenDanceCatalog.assertChatModel(provider);
            ModelOptions.apply(payload, provider);
            String body = request(
                    Endpoints.chat(provider.optString("base_url")),
                    "POST",
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    30000,
                    60000);
            JSONArray choices = new JSONObject(body).optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("模型未返回回答，请检查接口协议");
            }
            JSONObject message = choices.optJSONObject(0) == null
                    ? null
                    : choices.optJSONObject(0).optJSONObject("message");
            String answer = message == null ? "" : message.optString("content").trim();
            boolean matched = vision
                    ? Pattern.compile("(?i)\\bRED\\b").matcher(answer).find()
                            && !Pattern.compile("(?i)\\b(blue|green|yellow|black|white)\\b")
                                    .matcher(answer).find()
                    : "YAZHANG TEXT OK".equalsIgnoreCase(answer);
            if (!matched) {
                throw new IOException(vision
                        ? "视觉探测未能确认图片中的红色，请检查模型视觉能力"
                        : "模型已响应，但文本验证内容不匹配");
            }
            return result(true,
                    vision ? "视觉探测成功：模型识别了红色图片" : "文本探测成功", started);
        } catch (Exception error) {
            String message = error instanceof CancellationException
                    ? "探测已取消"
                    : safeError(error);
            JSONObject failed = result(false, message, started);
            TokenDanceRecovery recovery = TokenDanceRecovery.find(error);
            if (recovery != null) {
                try {
                    failed.put("message", recovery.getMessage())
                            .put("recovery_action", recovery.actionId());
                } catch (org.json.JSONException ignored) {
                }
            }
            return failed;
        }
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection current = active;
        if (current != null) {
            current.disconnect();
        }
    }

    private String request(
            String address, String method, byte[] body, int connectTimeout, int readTimeout)
            throws Exception {
        checkCancelled();
        HttpURLConnection connection = factory.open(new URL(address));
        active = connection;
        try {
            checkCancelled();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (!key.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + key);
            }
            if (Endpoints.isTokenDance(provider.optString("base_url"))) {
                connection.setRequestProperty("X-App-URL", "app://yazhang");
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(body.length);
                checkCancelled();
                try (OutputStream out = connection.getOutputStream()) {
                    checkCancelled();
                    out.write(body);
                }
            }
            checkCancelled();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                TokenDanceRecovery recovery = TokenDanceRecovery.from(
                        provider.optString("base_url"),
                        connection.getHeaderField("TokenDance-Recovery-Action"));
                if (recovery != null) {
                    throw recovery;
                }
                throw new IOException(statusError(code));
            }
            try (InputStream in = connection.getInputStream()) {
                return read(in);
            }
        } catch (SocketTimeoutException error) {
            throw new IOException("请求超时，请稍后手动重试");
        } finally {
            active = null;
            connection.disconnect();
        }
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    private String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            checkCancelled();
            if (out.size() + count > MAX_BODY) {
                throw new IOException("模型返回数据过大");
            }
            out.write(buffer, 0, count);
        }
        return out.toString("UTF-8");
    }

    private String safeError(Exception error) {
        String message = error.getMessage();
        if (error instanceof IOException && message != null && (message.equals(
                "模型鉴权失败，请检查 API Key 或账户权限")
                || message.equals("接口路径或模型不存在（HTTP 404），请检查地址和模型 ID")
                || message.equals("模型请求过于频繁或额度不足，请稍后重试")
                || message.equals("模型未返回回答，请检查接口协议")
                || message.equals("视觉探测未能确认图片中的红色，请检查模型视觉能力")
                || message.equals("模型已响应，但文本验证内容不匹配")
                || message.equals("文本探测成功")
                || message.equals("请求超时，请稍后手动重试"))) {
            return message;
        }
        if (error instanceof IOException
                && message != null
                && message.matches("模型接口请求失败（HTTP [0-9]{3}），请检查地址和模型 ID")) {
            return message;
        }
        if (error instanceof IllegalStateException
                && ("请先填写 API Key".equals(message) || "请先填写模型 ID".equals(message))) {
            return message;
        }
        if (error instanceof IllegalArgumentException) {
            return "接口地址无效，请填写 HTTPS API 地址";
        }
        return "连接失败，请检查接口地址、模型 ID 和网络";
    }

    private String statusError(int code) {
        if (code == 401 || code == 403) {
            return "模型鉴权失败，请检查 API Key 或账户权限";
        }
        if (code == 404) {
            return "接口路径或模型不存在（HTTP 404），请检查地址和模型 ID";
        }
        if (code == 429) {
            return "模型请求过于频繁或额度不足，请稍后重试";
        }
        return "模型接口请求失败（HTTP " + code + "），请检查地址和模型 ID";
    }

    private JSONObject result(boolean success, String message, long started) {
        try {
            return new JSONObject()
                    .put("success", success)
                    .put("message", message)
                    .put("latency_ms", Math.max(0, System.currentTimeMillis() - started));
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JSONArray cleanLevels(JSONArray source) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            String level = source.optString(i).trim().toLowerCase(Locale.ROOT);
            if (level.matches("[a-z0-9_-]{1,32}") && !contains(out, level)) {
                out.put(level);
            }
        }
        return out;
    }

    private static boolean contains(JSONArray values, String target) {
        for (int i = 0; i < values.length(); i++) {
            if (target.equalsIgnoreCase(values.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean excluded(String id) {
        return Pattern.compile(
                "(?i)(?:ocr|asr|tts|embed(?:ding)?|rerank|image[-_]?generation|audio|video"
                        + "|seedance|seedream|happyhorse|kling)")
                .matcher(id)
                .find();
    }

    private static String nonempty(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? fallback : clean;
    }
}
