package com.example.yazhang.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "entries",
        indices = {
                @Index(value = {"requestKey"}, unique = true),
                @Index("occurredOn"),
                @Index("status")
        }
)
public class EntryEntity {

    public static final String STATUS_POSTED = "POSTED";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AI = "AI";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String type = "";

    public long amountFen;

    @NonNull
    public String category = "";

    public String merchant;

    public String title;

    public String detail;

    @NonNull
    public String occurredOn = "";

    public long occurredAt;

    @NonNull
    public String source = "";

    @NonNull
    public String status = "";

    public boolean categoryInferred;

    public double confidence;

    public String imageSha;

    @NonNull
    public String requestKey = "";

    public Long goalId;

    public long createdAt;

    public long updatedAt;
}
