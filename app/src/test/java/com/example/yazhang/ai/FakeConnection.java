package com.example.yazhang.ai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory HttpURLConnection used to verify transport boundaries without network. */
final class FakeConnection extends HttpURLConnection {

    final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
    final Map<String, String> requestHeaders = new HashMap<>();
    final Map<String, String> responseHeaders = new HashMap<>();
    final List<String> requestMethods = new ArrayList<>();
    int responseCode = 200;
    String responseBody = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
    boolean disconnected;
    boolean redirectsFollowed;
    int connectTimeout;
    int readTimeout;

    FakeConnection(String responseBody) {
        super(url());
        this.responseBody = responseBody;
    }

    private static URL url() {
        try {
            return new URL("https://example.com/chat/completions");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public void setRequestMethod(String method) throws java.net.ProtocolException {
        super.setRequestMethod(method);
        requestMethods.add(method);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        readTimeout = timeout;
    }

    @Override
    public void setInstanceFollowRedirects(boolean follow) {
        redirectsFollowed = follow;
    }

    @Override
    public void setRequestProperty(String key, String value) {
        requestHeaders.put(key, value);
    }

    @Override
    public void setDoOutput(boolean doOutput) {
    }

    @Override
    public void setFixedLengthStreamingMode(int length) {
    }

    @Override
    public OutputStream getOutputStream() {
        return requestBody;
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public String getHeaderField(String name) {
        return responseHeaders.get(name);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void disconnect() {
        disconnected = true;
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public void connect() throws IOException {
    }

    String bodyText() {
        return requestBody.toString();
    }
}
