package com.example.yazhang.ai;

public final class UserMessage {

    public final boolean allowed;
    public final String text;

    public UserMessage(boolean allowed, String text) {
        this.allowed = allowed;
        this.text = text;
    }
}
