package com.example.doublertk.data;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 停靠日志仓库
 * 提供停靠日志的数据访问和业务逻辑
 */
public class DockingLogRepository {

    private final DockingLogDao dockingLogDao;
    private final ExecutorService executorService;

    public DockingLogRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        this.dockingLogDao = database.dockingLogDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 插入停靠日志
     */
    public Future<Long> insert(DockingLog log) {
        return dockingLogDao.insert(log);
    }

    /**
     * 更新停靠日志
     */
    public Future<Integer> update(DockingLog log) {
        return dockingLogDao.update(log);
    }

    /**
     * 删除停靠日志
     */
    public Future<Integer> delete(DockingLog log) {
        return dockingLogDao.delete(log);
    }

    /**
     * 根据ID查询停靠日志
     */
    public Future<DockingLog> getById(long id) {
        return dockingLogDao.getById(id);
    }

    /**
     * 获取所有停靠日志
     */
    public Future<List<DockingLog>> getAllLogs() {
        return dockingLogDao.getAllLogs();
    }

    /**
     * 根据作业ID查询停靠日志
     */
    public Future<List<DockingLog>> getLogsByJobId(long jobId) {
        return dockingLogDao.getLogsByJobId(jobId);
    }

    /**
     * 根据状态查询停靠日志
     */
    public Future<List<DockingLog>> getLogsByStatus(String status) {
        return dockingLogDao.getLogsByStatus(status);
    }

    /**
     * 查询成功的停靠日志
     */
    public Future<List<DockingLog>> getSuccessLogs() {
        return dockingLogDao.getSuccessLogs();
    }

    /**
     * 查询失败的停靠日志
     */
    public Future<List<DockingLog>> getFailedLogs() {
        return dockingLogDao.getFailedLogs();
    }

    /**
     * 查询指定时间范围内的停靠日志
     */
    public Future<List<DockingLog>> getLogsByTimeRange(long startTime, long endTime) {
        return dockingLogDao.getLogsByTimeRange(startTime, endTime);
    }

    /**
     * 获取停靠日志总数
     */
    public Future<Integer> getLogCount() {
        return dockingLogDao.getLogCount();
    }

    /**
     * 获取成功停靠次数
     */
    public Future<Integer> getSuccessCount() {
        return dockingLogDao.getSuccessCount();
    }

    /**
     * 获取失败停靠次数
     */
    public Future<Integer> getFailedCount() {
        return dockingLogDao.getFailedCount();
    }

    /**
     * 计算平均停靠时间
     */
    public Future<Long> getAverageDuration() {
        return dockingLogDao.getAverageDuration();
    }

    /**
     * 计算平均船头误差
     */
    public Future<Double> getAverageBowError() {
        return dockingLogDao.getAverageBowError();
    }

    /**
     * 计算平均船尾误差
     */
    public Future<Double> getAverageSternError() {
        return dockingLogDao.getAverageSternError();
    }

    /**
     * 计算平均航向误差
     */
    public Future<Double> getAverageHeadingError() {
        return dockingLogDao.getAverageHeadingError();
    }

    /**
     * 删除所有停靠日志
     */
    public Future<Integer> deleteAll() {
        return dockingLogDao.deleteAll();
    }

    /**
     * 删除指定时间之前的日志
     */
    public Future<Integer> deleteOldLogs(long timestamp) {
        return dockingLogDao.deleteOldLogs(timestamp);
    }

    /**
     * 获取最近N条日志
     */
    public Future<List<DockingLog>> getRecentLogs(int limit) {
        return dockingLogDao.getRecentLogs(limit);
    }

    /**
     * 根据作业名称搜索日志
     */
    public Future<List<DockingLog>> searchByJobName(String keyword) {
        return dockingLogDao.searchByJobName(keyword);
    }

    /**
     * 获取指定作业的成功率
     */
    public Future<Double> getSuccessRateByJobId(long jobId) {
        return dockingLogDao.getSuccessRateByJobId(jobId);
    }

    /**
     * 获取总体成功率
     */
    public Future<Double> getOverallSuccessRate() {
        return dockingLogDao.getOverallSuccessRate();
    }

    /**
     * 创建新的停靠日志
     */
    public DockingLog createNewLog(long jobId, String jobName) {
        DockingLog log = new DockingLog();
        log.setJobId(jobId);
        log.setJobName(jobName);
        log.setStartTime(System.currentTimeMillis());
        log.setStatus("IN_PROGRESS");
        return log;
    }

    /**
     * 完成停靠日志（成功）
     */
    public void completeLog(DockingLog log, double finalBowError, double finalSternError, double finalHeadingError) {
        log.setEndTime(System.currentTimeMillis());
        log.setSuccess(true);
        log.setStatus("SUCCESS");
        log.setFinalBowError(finalBowError);
        log.setFinalSternError(finalSternError);
        log.setFinalHeadingError(finalHeadingError);
        update(log);
    }

    /**
     * 取消停靠日志
     */
    public void cancelLog(DockingLog log) {
        log.setEndTime(System.currentTimeMillis());
        log.setSuccess(false);
        log.setStatus("CANCELLED");
        update(log);
    }

    /**
     * 停靠失败
     */
    public void failLog(DockingLog log, String reason) {
        log.setEndTime(System.currentTimeMillis());
        log.setSuccess(false);
        log.setStatus("FAILED");
        log.setNotes(reason);
        update(log);
    }

    /**
     * 获取统计信息
     */
    public Future<DockingStatistics> getStatistics() {
        return executorService.submit(() -> {
            DockingStatistics stats = new DockingStatistics();
            try {
                stats.totalCount = getLogCount().get();
                stats.successCount = getSuccessCount().get();
                stats.failedCount = getFailedCount().get();
                stats.successRate = getOverallSuccessRate().get();
                stats.avgDuration = getAverageDuration().get();
                stats.avgBowError = getAverageBowError().get();
                stats.avgSternError = getAverageSternError().get();
                stats.avgHeadingError = getAverageHeadingError().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return stats;
        });
    }

    /**
     * 停靠统计信息
     */
    public static class DockingStatistics {
        public int totalCount;
        public int successCount;
        public int failedCount;
        public double successRate;
        public long avgDuration;
        public double avgBowError;
        public double avgSternError;
        public double avgHeadingError;

        public String getFormattedSuccessRate() {
            return String.format("%.1f%%", successRate * 100);
        }

        public String getFormattedAvgDuration() {
            long seconds = avgDuration / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return minutes + "分" + seconds + "秒";
        }
    }
}
