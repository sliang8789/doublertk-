package com.example.doublertk.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 航段数据访问对象
 */
@Dao
public interface LegDao {
    
    @Insert
    long insert(Leg leg);
    
    @Insert
    void insertAll(List<Leg> legs);
    
    @Update
    void update(Leg leg);
    
    @Delete
    void delete(Leg leg);
    
    @Query("SELECT * FROM leg WHERE jobId = :jobId ORDER BY sequence ASC")
    List<Leg> getByJobId(long jobId);
    
    @Query("SELECT * FROM leg WHERE id = :id")
    Leg getById(long id);
    
    @Query("SELECT * FROM leg WHERE jobId = :jobId AND sequence = :sequence")
    Leg getByJobIdAndSequence(long jobId, int sequence);
    
    @Query("SELECT COUNT(*) FROM leg WHERE jobId = :jobId")
    int getCountByJobId(long jobId);
    
    @Query("SELECT MAX(sequence) FROM leg WHERE jobId = :jobId")
    Integer getMaxSequenceByJobId(long jobId);
    
    @Query("DELETE FROM leg WHERE jobId = :jobId")
    void deleteByJobId(long jobId);
    
    @Query("DELETE FROM leg WHERE id = :id")
    void deleteById(long id);
    
    @Query("UPDATE leg SET status = :status WHERE id = :id")
    void updateStatus(long id, int status);
    
    @Query("SELECT * FROM leg WHERE jobId = :jobId AND status = :status ORDER BY sequence ASC")
    List<Leg> getByJobIdAndStatus(long jobId, int status);
}
