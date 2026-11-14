package com.example.yazhang.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "agent_messages",
        indices = {
                @Index({"sessionId", "createdAt"})
        }
)
public class AgentMessageEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String sessionId = "";

    @NonNull
    public String role = "";

    @NonNull
    public String content = "";

    public String toolName;

    public String safeArgsJson;

    public String chartJson;

    public long createdAt;
}
