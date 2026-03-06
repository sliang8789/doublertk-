package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/**
 * 停靠日志实体类
 * 记录每次停靠操作的详细信息
 */
@Entity(tableName = "docking_logs")
public class DockingLog {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // 基本信息
    @ColumnInfo(name = "job_id")
    private long jobId;

    @ColumnInfo(name = "job_name")
    private String jobName;

    @ColumnInfo(name = "leg_id")
    private long legId;

    @ColumnInfo(name = "leg_name")
    private String legName;

    @ColumnInfo(name = "target_point_name")
    private String targetPointName;

    // 时间信息
    @ColumnInfo(name = "start_time")
    private long startTime;  // 开始时间（毫秒时间戳）

    @ColumnInfo(name = "end_time")
    private long endTime;    // 结束时间（毫秒时间戳）

    @ColumnInfo(name = "duration")
    private long duration;   // 持续时间（毫秒）

    // 停靠结果
    @ColumnInfo(name = "success")
    private boolean success;  // 是否成功停靠

    @ColumnInfo(name = "status")
    private String status;    // 状态：SUCCESS, FAILED, CANCELLED

    // 目标位置信息
    @ColumnInfo(name = "target_north_x")
    private double targetNorthX;

    @ColumnInfo(name = "target_north_y")
    private double targetNorthY;

    @ColumnInfo(name = "target_south_x")
    private double targetSouthX;

    @ColumnInfo(name = "target_south_y")
    private double targetSouthY;

    @ColumnInfo(name = "target_heading")
    private double targetHeading;

    // 最终位置信息
    @ColumnInfo(name = "final_bow_x")
    private double finalBowX;

    @ColumnInfo(name = "final_bow_y")
    private double finalBowY;

    @ColumnInfo(name = "final_stern_x")
    private double finalSternX;

    @ColumnInfo(name = "final_stern_y")
    private double finalSternY;

    @ColumnInfo(name = "final_heading")
    private double finalHeading;

    // 误差信息
    @ColumnInfo(name = "final_bow_error")
    private double finalBowError;      // 最终船头误差（米）

    @ColumnInfo(name = "final_stern_error")
    private double finalSternError;    // 最终船尾误差（米）

    @ColumnInfo(name = "final_heading_error")
    private double finalHeadingError;  // 最终航向误差（度）

    @ColumnInfo(name = "max_bow_error")
    private double maxBowError;        // 过程中最大船头误差

    @ColumnInfo(name = "max_stern_error")
    private double maxSternError;      // 过程中最大船尾误差

    @ColumnInfo(name = "max_heading_error")
    private double maxHeadingError;    // 过程中最大航向误差

    @ColumnInfo(name = "avg_bow_error")
    private double avgBowError;        // 平均船头误差

    @ColumnInfo(name = "avg_stern_error")
    private double avgSternError;      // 平均船尾误差

    @ColumnInfo(name = "avg_heading_error")
    private double avgHeadingError;    // 平均航向误差

    // RTK状态统计
    @ColumnInfo(name = "rtk_fix_rate")
    private double rtkFixRate;         // RTK固定解比率（0-1）

    @ColumnInfo(name = "avg_pdop")
    private double avgPdop;            // 平均PDOP值

    @ColumnInfo(name = "avg_baseline")
    private double avgBaseline;        // 平均基线长度（米）

    // 报警统计
    @ColumnInfo(name = "boundary_alarm_count")
    private int boundaryAlarmCount;    // 边界报警次数

    @ColumnInfo(name = "rtk_lost_count")
    private int rtkLostCount;          // RTK失锁次数

    // 备注
    @ColumnInfo(name = "notes")
    private String notes;              // 备注信息

    @ColumnInfo(name = "operator")
    private String operator;           // 操作员

    // 构造函数
    public DockingLog() {
        this.startTime = System.currentTimeMillis();
        this.status = "IN_PROGRESS";
        this.success = false;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public long getLegId() {
        return legId;
    }

    public void setLegId(long legId) {
        this.legId = legId;
    }

    public String getLegName() {
        return legName;
    }

    public void setLegName(String legName) {
        this.legName = legName;
    }

    public String getTargetPointName() {
        return targetPointName;
    }

    public void setTargetPointName(String targetPointName) {
        this.targetPointName = targetPointName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
        this.duration = endTime - startTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTargetNorthX() {
        return targetNorthX;
    }

    public void setTargetNorthX(double targetNorthX) {
        this.targetNorthX = targetNorthX;
    }

    public double getTargetNorthY() {
        return targetNorthY;
    }

    public void setTargetNorthY(double targetNorthY) {
        this.targetNorthY = targetNorthY;
    }

    public double getTargetSouthX() {
        return targetSouthX;
    }

    public void setTargetSouthX(double targetSouthX) {
        this.targetSouthX = targetSouthX;
    }

    public double getTargetSouthY() {
        return targetSouthY;
    }

    public void setTargetSouthY(double targetSouthY) {
        this.targetSouthY = targetSouthY;
    }

    public double getTargetHeading() {
        return targetHeading;
    }

    public void setTargetHeading(double targetHeading) {
        this.targetHeading = targetHeading;
    }

    public double getFinalBowX() {
        return finalBowX;
    }

    public void setFinalBowX(double finalBowX) {
        this.finalBowX = finalBowX;
    }

    public double getFinalBowY() {
        return finalBowY;
    }

    public void setFinalBowY(double finalBowY) {
        this.finalBowY = finalBowY;
    }

    public double getFinalSternX() {
        return finalSternX;
    }

    public void setFinalSternX(double finalSternX) {
        this.finalSternX = finalSternX;
    }

    public double getFinalSternY() {
        return finalSternY;
    }

    public void setFinalSternY(double finalSternY) {
        this.finalSternY = finalSternY;
    }

    public double getFinalHeading() {
        return finalHeading;
    }

    public void setFinalHeading(double finalHeading) {
        this.finalHeading = finalHeading;
    }

    public double getFinalBowError() {
        return finalBowError;
    }

    public void setFinalBowError(double finalBowError) {
        this.finalBowError = finalBowError;
    }

    public double getFinalSternError() {
        return finalSternError;
    }

    public void setFinalSternError(double finalSternError) {
        this.finalSternError = finalSternError;
    }

    public double getFinalHeadingError() {
        return finalHeadingError;
    }

    public void setFinalHeadingError(double finalHeadingError) {
        this.finalHeadingError = finalHeadingError;
    }

    public double getMaxBowError() {
        return maxBowError;
    }

    public void setMaxBowError(double maxBowError) {
        this.maxBowError = maxBowError;
    }

    public double getMaxSternError() {
        return maxSternError;
    }

    public void setMaxSternError(double maxSternError) {
        this.maxSternError = maxSternError;
    }

    public double getMaxHeadingError() {
        return maxHeadingError;
    }

    public void setMaxHeadingError(double maxHeadingError) {
        this.maxHeadingError = maxHeadingError;
    }

    public double getAvgBowError() {
        return avgBowError;
    }

    public void setAvgBowError(double avgBowError) {
        this.avgBowError = avgBowError;
    }

    public double getAvgSternError() {
        return avgSternError;
    }

    public void setAvgSternError(double avgSternError) {
        this.avgSternError = avgSternError;
    }

    public double getAvgHeadingError() {
        return avgHeadingError;
    }

    public void setAvgHeadingError(double avgHeadingError) {
        this.avgHeadingError = avgHeadingError;
    }

    public double getRtkFixRate() {
        return rtkFixRate;
    }

    public void setRtkFixRate(double rtkFixRate) {
        this.rtkFixRate = rtkFixRate;
    }

    public double getAvgPdop() {
        return avgPdop;
    }

    public void setAvgPdop(double avgPdop) {
        this.avgPdop = avgPdop;
    }

    public double getAvgBaseline() {
        return avgBaseline;
    }

    public void setAvgBaseline(double avgBaseline) {
        this.avgBaseline = avgBaseline;
    }

    public int getBoundaryAlarmCount() {
        return boundaryAlarmCount;
    }

    public void setBoundaryAlarmCount(int boundaryAlarmCount) {
        this.boundaryAlarmCount = boundaryAlarmCount;
    }

    public int getRtkLostCount() {
        return rtkLostCount;
    }

    public void setRtkLostCount(int rtkLostCount) {
        this.rtkLostCount = rtkLostCount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * 获取格式化的持续时间
     * @return 格式化的时间字符串（如：2分30秒）
     */
    public String getFormattedDuration() {
        if (duration <= 0) return "0秒";

        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "分" + seconds + "秒";
        } else {
            return seconds + "秒";
        }
    }

    /**
     * 获取总体精度评级
     * @return 精度评级（优秀/良好/一般/较差）
     */
    public String getAccuracyRating() {
        double avgError = (avgBowError + avgSternError) / 2;

        if (avgError < 0.1) return "优秀";
        if (avgError < 0.3) return "良好";
        if (avgError < 0.5) return "一般";
        return "较差";
    }
}
