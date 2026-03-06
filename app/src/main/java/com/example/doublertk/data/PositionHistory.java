package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 定位历史记录实体类
 * 存储船舶的历史定位数据，用于轨迹回放和分析
 */
@Entity(
    tableName = "position_history",
    indices = {
        @Index(value = {"jobId"}),
        @Index(value = {"timestamp"})
    }
)
public class PositionHistory {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private long jobId;                     // 关联作业ID
    private long shipId;                    // 关联船舶ID
    private long timestamp;                 // 时间戳 (毫秒)
    
    // 船头RTK位置
    private double bowLatitude;             // 船头纬度 (度)
    private double bowLongitude;            // 船头经度 (度)
    private double bowAltitude;             // 船头高程 (米)
    private int bowFixQuality;              // 船头定位质量
    private int bowSatellites;              // 船头卫星数
    private double bowHdop;                 // 船头HDOP
    
    // 船尾RTK位置
    private double sternLatitude;           // 船尾纬度 (度)
    private double sternLongitude;          // 船尾经度 (度)
    private double sternAltitude;           // 船尾高程 (米)
    private int sternFixQuality;            // 船尾定位质量
    private int sternSatellites;            // 船尾卫星数
    private double sternHdop;               // 船尾HDOP
    
    // 船舶中心位置（计算值）
    private double centerLatitude;          // 中心纬度
    private double centerLongitude;         // 中心经度
    private double centerAltitude;          // 中心高程
    
    // 姿态信息
    private double heading;                 // 航向 (度, 0=北)
    private double pitch;                   // 俯仰角 (度)
    private double roll;                    // 横滚角 (度)
    
    // 运动信息
    private double speed;                   // 速度 (m/s)
    private double course;                  // 航迹方向 (度)
    
    // 停靠相关
    private String targetStakeLabel;        // 目标泊位标签
    private double bowError;                // 船头到北桩距离 (米)
    private double sternError;              // 船尾到南桩距离 (米)
    private double headingError;            // 航向误差 (度)
    
    // 数据质量
    private boolean isRtkFixed;             // 是否为RTK固定解
    private double accuracy;                // 综合精度估计 (米)
    
    public PositionHistory() {
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public long getJobId() { return jobId; }
    public void setJobId(long jobId) { this.jobId = jobId; }
    
    public long getShipId() { return shipId; }
    public void setShipId(long shipId) { this.shipId = shipId; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public double getBowLatitude() { return bowLatitude; }
    public void setBowLatitude(double bowLatitude) { this.bowLatitude = bowLatitude; }
    
    public double getBowLongitude() { return bowLongitude; }
    public void setBowLongitude(double bowLongitude) { this.bowLongitude = bowLongitude; }
    
    public double getBowAltitude() { return bowAltitude; }
    public void setBowAltitude(double bowAltitude) { this.bowAltitude = bowAltitude; }
    
    public int getBowFixQuality() { return bowFixQuality; }
    public void setBowFixQuality(int bowFixQuality) { this.bowFixQuality = bowFixQuality; }
    
    public int getBowSatellites() { return bowSatellites; }
    public void setBowSatellites(int bowSatellites) { this.bowSatellites = bowSatellites; }
    
    public double getBowHdop() { return bowHdop; }
    public void setBowHdop(double bowHdop) { this.bowHdop = bowHdop; }
    
    public double getSternLatitude() { return sternLatitude; }
    public void setSternLatitude(double sternLatitude) { this.sternLatitude = sternLatitude; }
    
    public double getSternLongitude() { return sternLongitude; }
    public void setSternLongitude(double sternLongitude) { this.sternLongitude = sternLongitude; }
    
    public double getSternAltitude() { return sternAltitude; }
    public void setSternAltitude(double sternAltitude) { this.sternAltitude = sternAltitude; }
    
    public int getSternFixQuality() { return sternFixQuality; }
    public void setSternFixQuality(int sternFixQuality) { this.sternFixQuality = sternFixQuality; }
    
    public int getSternSatellites() { return sternSatellites; }
    public void setSternSatellites(int sternSatellites) { this.sternSatellites = sternSatellites; }
    
    public double getSternHdop() { return sternHdop; }
    public void setSternHdop(double sternHdop) { this.sternHdop = sternHdop; }
    
    public double getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(double centerLatitude) { this.centerLatitude = centerLatitude; }
    
    public double getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(double centerLongitude) { this.centerLongitude = centerLongitude; }
    
    public double getCenterAltitude() { return centerAltitude; }
    public void setCenterAltitude(double centerAltitude) { this.centerAltitude = centerAltitude; }
    
    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = heading; }
    
    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch = pitch; }
    
    public double getRoll() { return roll; }
    public void setRoll(double roll) { this.roll = roll; }
    
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    
    public double getCourse() { return course; }
    public void setCourse(double course) { this.course = course; }
    
    public String getTargetStakeLabel() { return targetStakeLabel; }
    public void setTargetStakeLabel(String targetStakeLabel) { this.targetStakeLabel = targetStakeLabel; }
    
    public double getBowError() { return bowError; }
    public void setBowError(double bowError) { this.bowError = bowError; }
    
    public double getSternError() { return sternError; }
    public void setSternError(double sternError) { this.sternError = sternError; }
    
    public double getHeadingError() { return headingError; }
    public void setHeadingError(double headingError) { this.headingError = headingError; }
    
    public boolean isRtkFixed() { return isRtkFixed; }
    public void setRtkFixed(boolean rtkFixed) { isRtkFixed = rtkFixed; }
    
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
}
