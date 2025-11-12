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
public interface CoordinateSystemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CoordinateSystem cs);

    @Update
    int update(CoordinateSystem cs);

    @Delete
    void delete(CoordinateSystem cs);

    @Query("SELECT * FROM coordinate_systems ORDER BY createdAt DESC")
    LiveData<List<CoordinateSystem>> observeAll();

    @Query("SELECT * FROM coordinate_systems ORDER BY createdAt DESC")
    List<CoordinateSystem> getAll();

    @Query("SELECT COUNT(*) FROM coordinate_systems WHERE name = :name")
    int countByName(String name);

    @Query("SELECT * FROM coordinate_systems WHERE id = :id LIMIT 1")
    CoordinateSystem getById(long id);

    @Query("DELETE FROM coordinate_systems")
    void deleteAll();

    @Query("DELETE FROM sqlite_sequence WHERE name = 'coordinate_systems'")
    void resetSequence();
}