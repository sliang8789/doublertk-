package com.example.doublertk.heading;

import com.example.doublertk.rtk.RTKPosition;

/**
 * 双天线航向解算器
 * 根据船头和船尾两个RTK天线的位置计算船舶航向
 * 
 * 原理:
 * 1. 将两个RTK天线的WGS84坐标转换为局部平面坐标
 * 2. 计算从船尾指向船头的方向角
 * 3. 应用天线安装偏差校正
 * 
 * 精度分析:
 * - 假设RTK定位精度为2cm
 * - 天线基线长度为10m
 * - 航向精度 ≈ arctan(0.02/10) ≈ 0.11° < 0.2°
 */
public class DualAntennaHeading {
    
    // 地球参数
    private static final double EARTH_RADIUS = 6378137.0; // WGS84椭球长半轴（米）
    private static final double METERS_PER_DEGREE_LAT = 111319.9; // 每度纬度对应的米数
    
    // 天线配置
    private double antennaBaseline;      // 天线基线长度（米）
    private double headingOffset;        // 航向偏差校正（度）
    private double bowAntennaOffset;     // 船头天线沿船长方向偏移（米）
    private double sternAntennaOffset;   // 船尾天线沿船长方向偏移（米）
    
    // 计算结果
    private double lastHeading = 0;
    private double lastPitch = 0;
    private double lastRoll = 0;
    private double measuredBaseline = 0;
    private boolean baselineValid = false;
    
    // 基线验证阈值
    private double baselineTolerancePercent = 0.1; // 10%容差
    
    public DualAntennaHeading() {
        this.antennaBaseline = 10.0; // 默认10米
        this.headingOffset = 0;
    }
    
    public DualAntennaHeading(double antennaBaseline) {
        this.antennaBaseline = antennaBaseline;
        this.headingOffset = 0;
    }
    
    /**
     * 计算航向
     * @param bowPosition 船头RTK位置
     * @param sternPosition 船尾RTK位置
     * @return 航向角（度，0=北，顺时针增加）
     */
    public double calculateHeading(RTKPosition bowPosition, RTKPosition sternPosition) {
        if (bowPosition == null || sternPosition == null) {
            return lastHeading;
        }
        
        // 获取经纬度
        double bowLat = bowPosition.getLatitude();
        double bowLon = bowPosition.getLongitude();
        double sternLat = sternPosition.getLatitude();
        double sternLon = sternPosition.getLongitude();
        
        // 计算航向
        return calculateHeading(bowLat, bowLon, sternLat, sternLon);
    }
    
    /**
     * 计算航向
     * @param bowLat 船头纬度（度）
     * @param bowLon 船头经度（度）
     * @param sternLat 船尾纬度（度）
     * @param sternLon 船尾经度（度）
     * @return 航向角（度，0=北，顺时针增加）
     */
    public double calculateHeading(double bowLat, double bowLon, double sternLat, double sternLon) {
        // 计算中点纬度（用于经度转换）
        double midLat = (bowLat + sternLat) / 2;
        double cosLat = Math.cos(Math.toRadians(midLat));
        
        // 将经纬度差转换为平面坐标差（米）
        // dx: 东向为正
        // dy: 北向为正
        double dx = (bowLon - sternLon) * METERS_PER_DEGREE_LAT * cosLat;
        double dy = (bowLat - sternLat) * METERS_PER_DEGREE_LAT;
        
        // 计算实际测量的基线长度
        measuredBaseline = Math.sqrt(dx * dx + dy * dy);
        
        // 验证基线长度
        double expectedBaseline = antennaBaseline;
        double tolerance = expectedBaseline * baselineTolerancePercent;
        baselineValid = Math.abs(measuredBaseline - expectedBaseline) <= tolerance;
        
        // 计算航向角（从船尾指向船头的方向）
        // atan2(dx, dy) 返回的是从北向顺时针的角度
        double heading = Math.toDegrees(Math.atan2(dx, dy));
        
        // 归一化到0-360度
        if (heading < 0) {
            heading += 360;
        }
        
        // 应用航向偏差校正
        heading = normalizeAngle(heading + headingOffset);
        
        lastHeading = heading;
        return heading;
    }
    
    /**
     * 计算完整姿态（航向、俯仰、横滚）
     * 注意：俯仰和横滚需要高程数据，精度较低
     * 
     * @param bowPosition 船头RTK位置
     * @param sternPosition 船尾RTK位置
     * @return [航向, 俯仰, 横滚]（度）
     */
    public double[] calculateAttitude(RTKPosition bowPosition, RTKPosition sternPosition) {
        if (bowPosition == null || sternPosition == null) {
            return new double[]{lastHeading, lastPitch, lastRoll};
        }
        
        // 计算航向
        double heading = calculateHeading(bowPosition, sternPosition);
        
        // 计算俯仰角（船头相对船尾的高度差）
        double heightDiff = bowPosition.getAltitude() - sternPosition.getAltitude();
        double pitch = Math.toDegrees(Math.atan2(heightDiff, measuredBaseline));
        
        // 横滚角需要额外的传感器，这里设为0
        double roll = 0;
        
        lastHeading = heading;
        lastPitch = pitch;
        lastRoll = roll;
        
        return new double[]{heading, pitch, roll};
    }
    
    /**
     * 计算航向变化率
     * @param currentHeading 当前航向（度）
     * @param previousHeading 上一时刻航向（度）
     * @param deltaTimeSeconds 时间间隔（秒）
     * @return 航向变化率（度/秒）
     */
    public double calculateHeadingRate(double currentHeading, double previousHeading, double deltaTimeSeconds) {
        if (deltaTimeSeconds <= 0) {
            return 0;
        }
        
        // 计算航向差（考虑跨越0/360度的情况）
        double diff = currentHeading - previousHeading;
        if (diff > 180) {
            diff -= 360;
        } else if (diff < -180) {
            diff += 360;
        }
        
        return diff / deltaTimeSeconds;
    }
    
    /**
     * 估算航向精度
     * 基于RTK定位精度和基线长度估算
     * 
     * @param rtkAccuracyMeters RTK定位精度（米）
     * @return 航向精度估算（度）
     */
    public double estimateHeadingAccuracy(double rtkAccuracyMeters) {
        if (antennaBaseline <= 0) {
            return Double.MAX_VALUE;
        }
        
        // 航向精度 ≈ arctan(定位误差 / 基线长度) * sqrt(2)
        // sqrt(2)是因为两个天线都有误差
        double accuracy = Math.toDegrees(Math.atan(rtkAccuracyMeters * Math.sqrt(2) / antennaBaseline));
        return accuracy;
    }
    
    /**
     * 角度归一化到0-360度
     */
    private double normalizeAngle(double angle) {
        while (angle < 0) {
            angle += 360;
        }
        while (angle >= 360) {
            angle -= 360;
        }
        return angle;
    }
    
    /**
     * 角度归一化到-180到180度
     */
    public static double normalizeAngle180(double angle) {
        while (angle > 180) {
            angle -= 360;
        }
        while (angle <= -180) {
            angle += 360;
        }
        return angle;
    }
    
    // Getters and Setters
    public double getAntennaBaseline() {
        return antennaBaseline;
    }
    
    public void setAntennaBaseline(double antennaBaseline) {
        this.antennaBaseline = antennaBaseline;
    }
    
    public double getHeadingOffset() {
        return headingOffset;
    }
    
    public void setHeadingOffset(double headingOffset) {
        this.headingOffset = headingOffset;
    }
    
    public double getBowAntennaOffset() {
        return bowAntennaOffset;
    }
    
    public void setBowAntennaOffset(double bowAntennaOffset) {
        this.bowAntennaOffset = bowAntennaOffset;
    }
    
    public double getSternAntennaOffset() {
        return sternAntennaOffset;
    }
    
    public void setSternAntennaOffset(double sternAntennaOffset) {
        this.sternAntennaOffset = sternAntennaOffset;
    }
    
    public double getLastHeading() {
        return lastHeading;
    }
    
    public double getLastPitch() {
        return lastPitch;
    }
    
    public double getLastRoll() {
        return lastRoll;
    }
    
    public double getMeasuredBaseline() {
        return measuredBaseline;
    }
    
    public boolean isBaselineValid() {
        return baselineValid;
    }
    
    public void setBaselineTolerancePercent(double tolerancePercent) {
        this.baselineTolerancePercent = tolerancePercent;
    }
}
