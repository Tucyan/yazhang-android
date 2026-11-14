package com.example.yazhang.domain;

public final class HomeSummary {

    public enum Kind {
        TODAY,
        YESTERDAY,
        EMPTY
    }

    public final Kind kind;
    public final long totalFen;

    public HomeSummary(Kind kind, long totalFen) {
        if (kind == null) {
            throw new IllegalArgumentException("首页汇总类型不能为空");
        }
        if (totalFen < 0) {
            throw new IllegalArgumentException("首页汇总金额不能为负数");
        }
        this.kind = kind;
        this.totalFen = totalFen;
    }
}
