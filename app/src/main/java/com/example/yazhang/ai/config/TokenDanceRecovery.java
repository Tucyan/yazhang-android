package com.example.yazhang.ai.config;

import java.io.IOException;
import java.util.Locale;

/** Service-owned recovery instruction; never read from arbitrary provider responses. */
public final class TokenDanceRecovery extends IOException {

    public enum Action {
        TOP_UP_BALANCE,
        REAUTHORIZE_API_KEY,
        API_KEY_QUOTA
    }

    public final Action action;

    public TokenDanceRecovery(Action action) {
        super(action == Action.TOP_UP_BALANCE
                ? "AI 服务额度不足，本次没有记账。请检查服务商账户额度后重试，账本仍可手动使用。"
                : action == Action.REAUTHORIZE_API_KEY
                        ? "TokenDance 授权已失效，请重新连接。"
                        : "授权的周期额度已用完，可等待额度刷新，或重新授权调整额度。");
        this.action = action;
    }

    public String actionId() {
        return action.name().toLowerCase(Locale.ROOT);
    }

    public static TokenDanceRecovery from(String base, String header) {
        if (!Endpoints.isTokenDance(base) || header == null) {
            return null;
        }
        switch (header) {
            case "top_up_balance":
                return new TokenDanceRecovery(Action.TOP_UP_BALANCE);
            case "reauthorize_api_key":
                return new TokenDanceRecovery(Action.REAUTHORIZE_API_KEY);
            case "api_key_quota":
                return new TokenDanceRecovery(Action.API_KEY_QUOTA);
            default:
                return null;
        }
    }

    public static TokenDanceRecovery find(Throwable error) {
        for (int i = 0; error != null && i < 10; i++, error = error.getCause()) {
            if (error instanceof TokenDanceRecovery) {
                return (TokenDanceRecovery) error;
            }
        }
        return null;
    }
}
