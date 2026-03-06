package com.example.doublertk.rtk;

/**
 * RTK定位数据模型
 * 存储单个RTK天线的定位信息
 */
public class RTKPosition {
    
    // 定位质量常量
    public static final int FIX_INVALID = 0;      // 无效定位
    public static final int FIX_GPS = 1;          // 单点定位
    public static final int FIX_DGPS = 2;         // 差分GPS
    public static final int FIX_PPS = 3;          // PPS定位
    public static final int FIX_RTK_FIXED = 4;    // RTK固定解
    public static final int FIX_RTK_FLOAT = 5;    // RTK浮点解
    public static final int FIX_ESTIMATED = 6;    // 估算定位
    
    private double latitude;        // 纬度 (度)
    private double longitude;       // 经度 (度)
    private double altitude;        // 海拔高度 (米)
    private int fixQuality;         // 定位质量 (0-6)
    private int satellites;         // 卫星数量
    private double hdop;            // 水平精度因子
    private double pdop;            // 位置精度因子
    private double vdop;            // 垂直精度因子
    private double accuracy;        // 定位精度 (米)
    private long timestamp;         // 时间戳 (毫秒)
    private double speed;           // 速度 (m/s)
    private double course;          // 航向 (度)
    private double geoidHeight;     // 大地水准面高度 (米)
    private double diffAge;         // 差分龄期 (秒)
    private String diffStation;     // 差分基站ID
    
    public RTKPosition() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public RTKPosition(double latitude, double longitude, double altitude,
                       int fixQuality, int satellites, double hdop) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.fixQuality = fixQuality;
        this.satellites = satellites;
        this.hdop = hdop;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 判断是否为RTK固定解
     */
    public boolean isRtkFixed() {
        return fixQuality == FIX_RTK_FIXED;
    }
    
    /**
     * 判断是否为RTK浮点解或固定解
     */
    public boolean isRtkSolution() {
        return fixQuality == FIX_RTK_FIXED || fixQuality == FIX_RTK_FLOAT;
    }
    
    /**
     * 判断定位是否有效
     */
    public boolean isValid() {
        return fixQuality > FIX_INVALID && satellites >= 4;
    }
    
    /**
     * 获取定位质量描述
     */
    public String getFixQualityText() {
        switch (fixQuality) {
            case FIX_INVALID: return "无效";
            case FIX_GPS: return "单点";
            case FIX_DGPS: return "差分";
            case FIX_PPS: return "PPS";
            case FIX_RTK_FIXED: return "RTK固定";
            case FIX_RTK_FLOAT: return "RTK浮点";
            case FIX_ESTIMATED: return "估算";
            default: return "未知";
        }
    }
    
    // Getters and Setters
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    
    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }
    
    public int getFixQuality() { return fixQuality; }
    public void setFixQuality(int fixQuality) { this.fixQuality = fixQuality; }
    
    public int getSatellites() { return satellites; }
    public void setSatellites(int satellites) { this.satellites = satellites; }
    
    public double getHdop() { return hdop; }
    public void setHdop(double hdop) { this.hdop = hdop; }
    
    public double getPdop() { return pdop; }
    public void setPdop(double pdop) { this.pdop = pdop; }
    
    public double getVdop() { return vdop; }
    public void setVdop(double vdop) { this.vdop = vdop; }
    
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    
    public double getCourse() { return course; }
    public void setCourse(double course) { this.course = course; }
    
    public double getGeoidHeight() { return geoidHeight; }
    public void setGeoidHeight(double geoidHeight) { this.geoidHeight = geoidHeight; }
    
    public double getDiffAge() { return diffAge; }
    public void setDiffAge(double diffAge) { this.diffAge = diffAge; }
    
    public String getDiffStation() { return diffStation; }
    public void setDiffStation(String diffStation) { this.diffStation = diffStation; }
    
    @Override
    public String toString() {
        return String.format("RTKPosition{lat=%.8f, lon=%.8f, alt=%.2f, fix=%s, sats=%d, hdop=%.1f}",
                latitude, longitude, altitude, getFixQualityText(), satellites, hdop);
    }
}
