package com.example.doublertk.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 定位历史数据访问对象
 */
@Dao
public interface PositionHistoryDao {
    
    @Insert
    long insert(PositionHistory history);
    
    @Insert
    void insertAll(List<PositionHistory> histories);
    
    @Delete
    void delete(PositionHistory history);
    
    @Query("SELECT * FROM position_history WHERE jobId = :jobId ORDER BY timestamp ASC")
    List<PositionHistory> getByJobId(long jobId);
    
    @Query("SELECT * FROM position_history WHERE jobId = :jobId ORDER BY timestamp DESC LIMIT :limit")
    List<PositionHistory> getRecentByJobId(long jobId, int limit);
    
    @Query("SELECT * FROM position_history WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<PositionHistory> getByTimeRange(long startTime, long endTime);
    
    @Query("SELECT * FROM position_history WHERE jobId = :jobId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<PositionHistory> getByJobIdAndTimeRange(long jobId, long startTime, long endTime);
    
    @Query("SELECT COUNT(*) FROM position_history WHERE jobId = :jobId")
    int getCountByJobId(long jobId);
    
    @Query("DELETE FROM position_history WHERE jobId = :jobId")
    void deleteByJobId(long jobId);
    
    @Query("DELETE FROM position_history WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("SELECT * FROM position_history WHERE id = :id")
    PositionHistory getById(long id);
    
    @Query("SELECT * FROM position_history ORDER BY timestamp DESC LIMIT 1")
    PositionHistory getLatest();
    
    @Query("SELECT * FROM position_history WHERE jobId = :jobId ORDER BY timestamp DESC LIMIT 1")
    PositionHistory getLatestByJobId(long jobId);
}
