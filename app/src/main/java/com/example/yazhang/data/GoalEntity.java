package com.example.yazhang.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "goals", indices = @Index("status"))
public class GoalEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public long targetFen;

    @NonNull
    public String targetDate = "";

    @NonNull
    public String status = STATUS_ACTIVE;

    public long currentFen;

    public long createdAt;

    public long updatedAt;

    public GoalEntity() {
    }

    @Ignore
    public GoalEntity(
            long id,
            String name,
            long targetFen,
            String targetDate,
            String status,
            long currentFen,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.targetFen = targetFen;
        this.targetDate = targetDate;
        this.status = status;
        this.currentFen = currentFen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
