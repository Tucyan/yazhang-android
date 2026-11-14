package com.example.yazhang.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.yazhang.domain.CategoryTotal;

import java.util.List;

@Dao
public interface EntryDao {

    @Insert
    long insert(EntryEntity entry);

    @Query("SELECT * FROM entries WHERE id = :id")
    EntryEntity findById(long id);

    @Query("SELECT * FROM entries WHERE requestKey = :requestKey LIMIT 1")
    EntryEntity findByRequestKey(String requestKey);

    @Query("SELECT * FROM entries WHERE imageSha = :imageSha LIMIT 1")
    EntryEntity findByImageSha(String imageSha);

    @Query("SELECT COALESCE(SUM(amountFen), 0) FROM entries "
            + "WHERE status = 'POSTED' AND type = 'EXPENSE' AND occurredOn = :date")
    long expenseTotalOn(String date);

    @Query("SELECT category AS category, SUM(amountFen) AS totalFen FROM entries "
            + "WHERE status = 'POSTED' AND occurredOn BETWEEN :from AND :to "
            + "AND (:type = 'ALL' OR type = :type) "
            + "AND (:category = 'ALL' OR category = :category) "
            + "GROUP BY category ORDER BY category ASC")
    List<CategoryTotal> categoryTotals(String from, String to, String type, String category);

    @Query("SELECT * FROM entries "
            + "WHERE status = 'POSTED' AND occurredOn BETWEEN :from AND :to "
            + "AND (:type = 'ALL' OR type = :type) "
            + "AND (:category = 'ALL' OR category = :category) "
            + "ORDER BY occurredOn DESC, occurredAt DESC, id DESC LIMIT 50")
    List<EntryEntity> entries(String from, String to, String type, String category);

    @Query("SELECT COUNT(*) FROM entries "
            + "WHERE status = 'POSTED' AND occurredOn BETWEEN :from AND :to "
            + "AND (:type = 'ALL' OR type = :type) "
            + "AND (:category = 'ALL' OR category = :category)")
    long countEntries(String from, String to, String type, String category);

    @Query("SELECT * FROM entries ORDER BY occurredOn DESC, occurredAt DESC, id DESC")
    List<EntryEntity> allEntries();

    @Query("SELECT requestKey FROM entries")
    List<String> allRequestKeys();

    @Query("SELECT COUNT(*) FROM entries")
    long countAll();
}
