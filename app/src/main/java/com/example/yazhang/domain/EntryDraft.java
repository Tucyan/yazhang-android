package com.example.yazhang.domain;

import java.time.LocalDate;

public final class EntryDraft {

    public final String type;
    public final long amountFen;
    public final String category;
    public final String merchant;
    public final String title;
    public final String detail;
    public final LocalDate occurredOn;

    public EntryDraft(
            String type,
            long amountFen,
            String category,
            String merchant,
            String title,
            String detail,
            LocalDate occurredOn
    ) {
        this.type = type;
        this.amountFen = amountFen;
        this.category = category;
        this.merchant = merchant;
        this.title = title;
        this.detail = detail;
        this.occurredOn = occurredOn;
    }

    public static EntryDraft expense(long amountFen, String category, LocalDate occurredOn) {
        return new EntryDraft("EXPENSE", amountFen, category, null, null, null, occurredOn);
    }
}
