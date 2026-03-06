package com.example.doublertk.filter;

import com.example.doublertk.rtk.RTKPosition;

/**
 * 位置滤波器
 * 封装卡尔曼滤波器，专门用于RTK位置数据的滤波处理
 * 
 * 功能:
 * 1. 将WGS84经纬度转换为局部平面坐标进行滤波
 * 2. 根据RTK定位质量动态调整滤波参数
 * 3. 输出滤波后的位置和速度
 */
public class PositionFilter {
    
    // 地球参数
    private static final double METERS_PER_DEGREE_LAT = 111319.9;
    
    // 卡尔曼滤波器
    private KalmanFilter kalmanFilter;
    
    // 参考点（用于经纬度到平面坐标转换）
    private double refLatitude = 0;
    private double refLongitude = 0;
    private boolean refInitialized = false;
    
    // 滤波后的位置
    private RTKPosition filteredPosition;
    
    // 配置参数
    private double rtkFixedNoise = 0.02;    // RTK固定解噪声 (m)
    private double rtkFloatNoise = 0.5;     // RTK浮点解噪声 (m)
    private double dgpsNoise = 2.0;         // DGPS噪声 (m)
    private double gpsNoise = 5.0;          // 单点GPS噪声 (m)
    
    // 统计
    private int processedCount = 0;
    private long lastProcessTime = 0;
    
    public PositionFilter() {
        kalmanFilter = new KalmanFilter();
        filteredPosition = new RTKPosition();
    }
    
    public PositionFilter(double processNoiseStd) {
        kalmanFilter = new KalmanFilter(processNoiseStd, rtkFixedNoise);
        filteredPosition = new RTKPosition();
    }
    
    /**
     * 处理RTK位置数据
     * @param position 原始RTK位置
     * @return 滤波后的位置
     */
    public RTKPosition process(RTKPosition position) {
        if (position == null || !position.isValid()) {
            return filteredPosition;
        }
        
        // 初始化参考点
        if (!refInitialized) {
            refLatitude = position.getLatitude();
            refLongitude = position.getLongitude();
            refInitialized = true;
        }
        
        // 将经纬度转换为局部平面坐标
        double[] localXY = toLocalXY(position.getLatitude(), position.getLongitude());
        double x = localXY[0];
        double y = localXY[1];
        
        // 根据定位质量调整观测噪声
        double noise = getMeasurementNoise(position.getFixQuality());
        kalmanFilter.setMeasurementNoiseStd(noise);
        
        // 执行卡尔曼滤波
        kalmanFilter.process(x, y, position.getTimestamp());
        
        // 获取滤波后的坐标
        double filteredX = kalmanFilter.getX();
        double filteredY = kalmanFilter.getY();
        
        // 转换回经纬度
        double[] latLon = toLatLon(filteredX, filteredY);
        
        // 构建滤波后的位置
        filteredPosition = new RTKPosition();
        filteredPosition.setLatitude(latLon[0]);
        filteredPosition.setLongitude(latLon[1]);
        filteredPosition.setAltitude(position.getAltitude()); // 高程暂不滤波
        filteredPosition.setFixQuality(position.getFixQuality());
        filteredPosition.setSatellites(position.getSatellites());
        filteredPosition.setHdop(position.getHdop());
        filteredPosition.setTimestamp(position.getTimestamp());
        
        // 计算滤波后的速度和航向
        filteredPosition.setSpeed(kalmanFilter.getSpeed());
        filteredPosition.setCourse(kalmanFilter.getCourse());
        
        processedCount++;
        lastProcessTime = System.currentTimeMillis();
        
        return filteredPosition;
    }
    
    /**
     * 处理经纬度数据
     * @param latitude 纬度
     * @param longitude 经度
     * @param fixQuality 定位质量
     * @param timestamp 时间戳
     * @return 滤波后的经纬度 [lat, lon]
     */
    public double[] process(double latitude, double longitude, int fixQuality, long timestamp) {
        // 初始化参考点
        if (!refInitialized) {
            refLatitude = latitude;
            refLongitude = longitude;
            refInitialized = true;
        }
        
        // 转换为局部坐标
        double[] localXY = toLocalXY(latitude, longitude);
        
        // 调整噪声
        double noise = getMeasurementNoise(fixQuality);
        kalmanFilter.setMeasurementNoiseStd(noise);
        
        // 滤波
        kalmanFilter.process(localXY[0], localXY[1], timestamp);
        
        // 转换回经纬度
        return toLatLon(kalmanFilter.getX(), kalmanFilter.getY());
    }
    
    /**
     * 将经纬度转换为局部平面坐标
     * @param latitude 纬度
     * @param longitude 经度
     * @return [x, y] 局部坐标（米）
     */
    private double[] toLocalXY(double latitude, double longitude) {
        double cosLat = Math.cos(Math.toRadians(refLatitude));
        double x = (longitude - refLongitude) * METERS_PER_DEGREE_LAT * cosLat;
        double y = (latitude - refLatitude) * METERS_PER_DEGREE_LAT;
        return new double[]{x, y};
    }
    
    /**
     * 将局部平面坐标转换为经纬度
     * @param x X坐标（米）
     * @param y Y坐标（米）
     * @return [latitude, longitude]
     */
    private double[] toLatLon(double x, double y) {
        double cosLat = Math.cos(Math.toRadians(refLatitude));
        double latitude = refLatitude + y / METERS_PER_DEGREE_LAT;
        double longitude = refLongitude + x / (METERS_PER_DEGREE_LAT * cosLat);
        return new double[]{latitude, longitude};
    }
    
    /**
     * 根据定位质量获取观测噪声
     */
    private double getMeasurementNoise(int fixQuality) {
        switch (fixQuality) {
            case RTKPosition.FIX_RTK_FIXED:
                return rtkFixedNoise;
            case RTKPosition.FIX_RTK_FLOAT:
                return rtkFloatNoise;
            case RTKPosition.FIX_DGPS:
                return dgpsNoise;
            default:
                return gpsNoise;
        }
    }
    
    /**
     * 重置滤波器
     */
    public void reset() {
        kalmanFilter.reset();
        refInitialized = false;
        processedCount = 0;
        filteredPosition = new RTKPosition();
    }
    
    /**
     * 设置参考点
     */
    public void setReferencePoint(double latitude, double longitude) {
        this.refLatitude = latitude;
        this.refLongitude = longitude;
        this.refInitialized = true;
        kalmanFilter.reset();
    }
    
    // Getters
    public RTKPosition getFilteredPosition() {
        return filteredPosition;
    }
    
    public double getFilteredLatitude() {
        return filteredPosition.getLatitude();
    }
    
    public double getFilteredLongitude() {
        return filteredPosition.getLongitude();
    }
    
    public double getFilteredSpeed() {
        return kalmanFilter.getSpeed();
    }
    
    public double getFilteredCourse() {
        return kalmanFilter.getCourse();
    }
    
    public double getPositionUncertainty() {
        return kalmanFilter.getPositionVariance();
    }
    
    public int getProcessedCount() {
        return processedCount;
    }
    
    public boolean isInitialized() {
        return kalmanFilter.isInitialized();
    }
    
    // Setters
    public void setRtkFixedNoise(double noise) {
        this.rtkFixedNoise = noise;
    }
    
    public void setRtkFloatNoise(double noise) {
        this.rtkFloatNoise = noise;
    }
    
    public void setProcessNoiseStd(double std) {
        kalmanFilter.setProcessNoiseStd(std);
    }
}
