package com.example.yazhang.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface GoalDao {

    @Insert
    long insert(GoalEntity goal);

    @Update
    int update(GoalEntity goal);

    @Query("SELECT * FROM goals WHERE id = :id")
    GoalEntity findById(long id);

    @Query("SELECT * FROM goals ORDER BY status ASC, id DESC")
    List<GoalEntity> findAll();

    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    GoalEntity activeGoal();
}
