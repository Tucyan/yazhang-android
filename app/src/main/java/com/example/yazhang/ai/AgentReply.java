package com.example.yazhang.ai;

public final class AgentReply {

    public static final String KIND_CLARIFY = "clarify";
    public static final String KIND_CREATED = "created";
    public static final String KIND_STATISTICS = "statistics";
    public static final String KIND_SAVING = "saving_progress";
    public static final String KIND_FINAL = "final";
    public static final String KIND_REJECTED = "rejected";
    public static final String KIND_NOTICE = "notice";
    public static final String KIND_CANCELLED = "cancelled";

    public final String kind;
    public final String text;
    public final String chartJson;
    public final long entryId;

    public AgentReply(String kind, String text, String chartJson, long entryId) {
        this.kind = kind;
        this.text = text;
        this.chartJson = chartJson;
        this.entryId = entryId;
    }

    public static AgentReply of(String kind, String text) {
        return new AgentReply(kind, text, null, 0L);
    }
}
