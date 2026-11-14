package com.example.yazhang.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AgentMessageDao {

    @Insert
    long insert(AgentMessageEntity message);

    @Query("SELECT * FROM agent_messages WHERE sessionId = :sessionId "
            + "ORDER BY id DESC LIMIT :limit")
    List<AgentMessageEntity> recent(String sessionId, int limit);
}
