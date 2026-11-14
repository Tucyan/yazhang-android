package com.example.yazhang.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {EntryEntity.class, GoalEntity.class, AgentMessageEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "yazhang.db";
    private static volatile AppDatabase instance;

    public abstract EntryDao entryDao();

    public abstract GoalDao goalDao();

    public abstract AgentMessageDao agentMessageDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                            .build();
                }
            }
        }
        return instance;
    }
}
