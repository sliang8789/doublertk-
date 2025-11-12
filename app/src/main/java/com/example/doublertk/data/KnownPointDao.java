package com.example.doublertk.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface KnownPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KnownPoint point);

    @Update
    int update(KnownPoint point);

    @Delete
    void delete(KnownPoint point);

    @Query("SELECT * FROM known_points WHERE csId = :csId ORDER BY id ASC")
    LiveData<List<KnownPoint>> getByCs(long csId);

    @Query("SELECT * FROM known_points WHERE csId = :csId ORDER BY id ASC")
    List<KnownPoint> getByCsSync(long csId);

    @Query("DELETE FROM known_points WHERE csId = :csId")
    void deleteByCs(long csId);

    @Query("SELECT COUNT(*) FROM known_points WHERE csId = :csId")
    LiveData<Integer> countLive(long csId);

    @Query("SELECT COUNT(*) FROM known_points WHERE csId = :csId")
    int count(long csId);

    @Query("DELETE FROM known_points")
    void deleteAll();

    @Query("DELETE FROM sqlite_sequence WHERE name = 'known_points'")
    void resetSequence();
}