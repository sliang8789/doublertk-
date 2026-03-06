package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 航段实体类
 * 存储作业中的航段信息，用于定义船舶的航行路线
 */
@Entity(
    tableName = "leg",
    indices = {
        @Index(value = {"jobId"})
    }
)
public class Leg {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private long jobId;                     // 关联作业ID
    private String name;                    // 航段名称
    private int sequence;                   // 航段顺序
    
    // 起点坐标
    private double startLatitude;           // 起点纬度
    private double startLongitude;          // 起点经度
    private double startX;                  // 起点局部X坐标 (米)
    private double startY;                  // 起点局部Y坐标 (米)
    
    // 终点坐标
    private double endLatitude;             // 终点纬度
    private double endLongitude;            // 终点经度
    private double endX;                    // 终点局部X坐标 (米)
    private double endY;                    // 终点局部Y坐标 (米)
    
    // 航段属性
    private double length;                  // 航段长度 (米)
    private double bearing;                 // 航段方位角 (度)
    private double targetSpeed;             // 目标航速 (m/s)
    private double crossTrackTolerance;     // 横向偏差容限 (米)
    
    // 目标泊位（如果是停靠航段）
    private String targetStakeLabel;        // 目标泊位标签
    private double targetNorthX;            // 北桩X坐标
    private double targetNorthY;            // 北桩Y坐标
    private double targetSouthX;            // 南桩X坐标
    private double targetSouthY;            // 南桩Y坐标
    
    // 状态
    private int status;                     // 状态: 0=未开始, 1=进行中, 2=已完成
    private long startTime;                 // 开始时间
    private long endTime;                   // 结束时间
    private long createTime;                // 创建时间
    
    // 状态常量
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_COMPLETED = 2;
    
    public Leg() {
        this.createTime = System.currentTimeMillis();
        this.status = STATUS_PENDING;
        this.crossTrackTolerance = 2.0;
        this.targetSpeed = 0.5;
    }
    
    /**
     * 计算航段长度和方位角
     */
    public void calculateLengthAndBearing() {
        double dx = endX - startX;
        double dy = endY - startY;
        this.length = Math.sqrt(dx * dx + dy * dy);
        this.bearing = Math.toDegrees(Math.atan2(dx, dy));
        if (this.bearing < 0) {
            this.bearing += 360;
        }
    }
    
    /**
     * 判断是否为停靠航段
     */
    public boolean isDockingLeg() {
        return targetStakeLabel != null && !targetStakeLabel.isEmpty();
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public long getJobId() { return jobId; }
    public void setJobId(long jobId) { this.jobId = jobId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    
    public double getStartLatitude() { return startLatitude; }
    public void setStartLatitude(double startLatitude) { this.startLatitude = startLatitude; }
    
    public double getStartLongitude() { return startLongitude; }
    public void setStartLongitude(double startLongitude) { this.startLongitude = startLongitude; }
    
    public double getStartX() { return startX; }
    public void setStartX(double startX) { this.startX = startX; }
    
    public double getStartY() { return startY; }
    public void setStartY(double startY) { this.startY = startY; }
    
    public double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(double endLatitude) { this.endLatitude = endLatitude; }
    
    public double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(double endLongitude) { this.endLongitude = endLongitude; }
    
    public double getEndX() { return endX; }
    public void setEndX(double endX) { this.endX = endX; }
    
    public double getEndY() { return endY; }
    public void setEndY(double endY) { this.endY = endY; }
    
    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }
    
    public double getBearing() { return bearing; }
    public void setBearing(double bearing) { this.bearing = bearing; }
    
    public double getTargetSpeed() { return targetSpeed; }
    public void setTargetSpeed(double targetSpeed) { this.targetSpeed = targetSpeed; }
    
    public double getCrossTrackTolerance() { return crossTrackTolerance; }
    public void setCrossTrackTolerance(double crossTrackTolerance) { this.crossTrackTolerance = crossTrackTolerance; }
    
    public String getTargetStakeLabel() { return targetStakeLabel; }
    public void setTargetStakeLabel(String targetStakeLabel) { this.targetStakeLabel = targetStakeLabel; }
    
    public double getTargetNorthX() { return targetNorthX; }
    public void setTargetNorthX(double targetNorthX) { this.targetNorthX = targetNorthX; }
    
    public double getTargetNorthY() { return targetNorthY; }
    public void setTargetNorthY(double targetNorthY) { this.targetNorthY = targetNorthY; }
    
    public double getTargetSouthX() { return targetSouthX; }
    public void setTargetSouthX(double targetSouthX) { this.targetSouthX = targetSouthX; }
    
    public double getTargetSouthY() { return targetSouthY; }
    public void setTargetSouthY(double targetSouthY) { this.targetSouthY = targetSouthY; }
    
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
