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
public interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Job job);

    @Update
    int update(Job job);

    @Delete
    void delete(Job job);

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    LiveData<List<Job>> observeAll();

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    List<Job> getAll();

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    Job getById(long id);

    @Query("DELETE FROM jobs")
    void deleteAll();

    @Query("DELETE FROM sqlite_sequence WHERE name = 'jobs'")
    void resetSequence();
}

