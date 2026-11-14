package com.example.yazhang.ai.config;

import java.net.URI;

/** Strict HTTPS endpoint helpers; redirects are never followed. */
public final class Endpoints {

    private Endpoints() {
    }

    public static String chat(String base) {
        try {
            String value = validated(base).replaceAll("/+$", "");
            return value.endsWith("/chat/completions")
                    ? value
                    : value + "/chat/completions";
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("接口地址须为不含用户名、密码或查询参数的 HTTPS 地址", error);
        }
    }

    public static String models(String base) {
        String value = validated(base).replaceAll("/+$", "");
        if (value.endsWith("/chat/completions")) {
            value = value.substring(0, value.length() - "/chat/completions".length());
        }
        return value + "/models";
    }

    public static boolean isTokenDance(String base) {
        try {
            URI uri = new URI(validated(base));
            return "tokendance.space".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && "/gateway/v1".equals(rawPath(uri));
        } catch (Exception error) {
            return false;
        }
    }

    private static String validated(String base) {
        try {
            URI uri = new URI(base == null ? "" : base.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("接口地址须为不含用户名、密码或查询参数的 HTTPS 地址");
            }
            return uri.toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("接口地址须为不含用户名、密码或查询参数的 HTTPS 地址", error);
        } catch (Exception error) {
            throw new IllegalArgumentException("接口地址须为不含用户名、密码或查询参数的 HTTPS 地址", error);
        }
    }

    private static String rawPath(URI uri) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return path.replaceAll("/+$", "");
    }
}
