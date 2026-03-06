package com.example.doublertk.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 停靠日志数据访问对象
 */
@Dao
public interface DockingLogDao {

    /**
     * 插入停靠日志
     */
    @Insert
    long insert(DockingLog log);

    /**
     * 更新停靠日志
     */
    @Update
    int update(DockingLog log);

    /**
     * 删除停靠日志
     */
    @Delete
    int delete(DockingLog log);

    /**
     * 根据ID查询停靠日志
     */
    @Query("SELECT * FROM docking_logs WHERE id = :id")
    DockingLog getById(long id);

    /**
     * 获取所有停靠日志（按时间倒序）
     */
    @Query("SELECT * FROM docking_logs ORDER BY start_time DESC")
    List<DockingLog> getAllLogs();

    /**
     * 根据作业ID查询停靠日志
     */
    @Query("SELECT * FROM docking_logs WHERE job_id = :jobId ORDER BY start_time DESC")
    List<DockingLog> getLogsByJobId(long jobId);

    /**
     * 根据状态查询停靠日志
     */
    @Query("SELECT * FROM docking_logs WHERE status = :status ORDER BY start_time DESC")
    List<DockingLog> getLogsByStatus(String status);

    /**
     * 查询成功的停靠日志
     */
    @Query("SELECT * FROM docking_logs WHERE success = 1 ORDER BY start_time DESC")
    List<DockingLog> getSuccessLogs();

    /**
     * 查询失败的停靠日志
     */
    @Query("SELECT * FROM docking_logs WHERE success = 0 AND status != 'IN_PROGRESS' ORDER BY start_time DESC")
    List<DockingLog> getFailedLogs();

    /**
     * 查询指定时间范围内的停靠日志
     */
    @Query("SELECT * FROM docking_logs WHERE start_time >= :startTime AND start_time <= :endTime ORDER BY start_time DESC")
    List<DockingLog> getLogsByTimeRange(long startTime, long endTime);

    /**
     * 获取停靠日志总数
     */
    @Query("SELECT COUNT(*) FROM docking_logs")
    int getLogCount();

    /**
     * 获取成功停靠次数
     */
    @Query("SELECT COUNT(*) FROM docking_logs WHERE success = 1")
    int getSuccessCount();

    /**
     * 获取失败停靠次数
     */
    @Query("SELECT COUNT(*) FROM docking_logs WHERE success = 0 AND status != 'IN_PROGRESS'")
    int getFailedCount();

    /**
     * 计算平均停靠时间（成功的）
     */
    @Query("SELECT AVG(duration) FROM docking_logs WHERE success = 1")
    Long getAverageDuration();

    /**
     * 计算平均船头误差（成功的）
     */
    @Query("SELECT AVG(final_bow_error) FROM docking_logs WHERE success = 1")
    Double getAverageBowError();

    /**
     * 计算平均船尾误差（成功的）
     */
    @Query("SELECT AVG(final_stern_error) FROM docking_logs WHERE success = 1")
    Double getAverageSternError();

    /**
     * 计算平均航向误差（成功的）
     */
    @Query("SELECT AVG(final_heading_error) FROM docking_logs WHERE success = 1")
    Double getAverageHeadingError();

    /**
     * 删除所有停靠日志
     */
    @Query("DELETE FROM docking_logs")
    int deleteAll();

    /**
     * 删除指定时间之前的日志
     */
    @Query("DELETE FROM docking_logs WHERE start_time < :timestamp")
    int deleteOldLogs(long timestamp);

    /**
     * 获取最近N条日志
     */
    @Query("SELECT * FROM docking_logs ORDER BY start_time DESC LIMIT :limit")
    List<DockingLog> getRecentLogs(int limit);

    /**
     * 根据作业名称搜索日志
     */
    @Query("SELECT * FROM docking_logs WHERE job_name LIKE '%' || :keyword || '%' ORDER BY start_time DESC")
    List<DockingLog> searchByJobName(String keyword);

    /**
     * 获取指定作业的成功率
     */
    @Query("SELECT CAST(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS REAL) / COUNT(*) FROM docking_logs WHERE job_id = :jobId")
    Double getSuccessRateByJobId(long jobId);

    /**
     * 获取总体成功率
     */
    @Query("SELECT CAST(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS REAL) / COUNT(*) FROM docking_logs WHERE status != 'IN_PROGRESS'")
    Double getOverallSuccessRate();
}
