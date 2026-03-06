package com.example.doublertk.heading;

/**
 * 航向滤波器
 * 对双天线计算的航向进行滤波处理，提高稳定性
 * 
 * 功能:
 * 1. 低通滤波：平滑航向抖动
 * 2. 异常值检测：剔除跳变数据
 * 3. 航向变化率限制：防止不合理的快速变化
 */
public class HeadingFilter {
    
    // 滤波参数
    private double alpha = 0.3;              // 低通滤波系数 (0-1, 越小越平滑)
    private double maxHeadingRate = 30.0;    // 最大航向变化率 (度/秒)
    private double outlierThreshold = 15.0;  // 异常值阈值 (度)
    
    // 状态
    private double filteredHeading = 0;
    private double previousHeading = 0;
    private long previousTimestamp = 0;
    private boolean initialized = false;
    
    // 统计
    private int totalSamples = 0;
    private int outlierCount = 0;
    
    public HeadingFilter() {
    }
    
    public HeadingFilter(double alpha) {
        this.alpha = Math.max(0.01, Math.min(1.0, alpha));
    }
    
    /**
     * 滤波处理
     * @param rawHeading 原始航向（度，0-360）
     * @return 滤波后的航向（度，0-360）
     */
    public double filter(double rawHeading) {
        return filter(rawHeading, System.currentTimeMillis());
    }
    
    /**
     * 滤波处理（带时间戳）
     * @param rawHeading 原始航向（度，0-360）
     * @param timestamp 时间戳（毫秒）
     * @return 滤波后的航向（度，0-360）
     */
    public double filter(double rawHeading, long timestamp) {
        totalSamples++;
        
        // 归一化输入
        rawHeading = normalizeAngle(rawHeading);
        
        // 首次初始化
        if (!initialized) {
            filteredHeading = rawHeading;
            previousHeading = rawHeading;
            previousTimestamp = timestamp;
            initialized = true;
            return filteredHeading;
        }
        
        // 计算时间间隔
        double deltaTime = (timestamp - previousTimestamp) / 1000.0; // 秒
        if (deltaTime <= 0) {
            deltaTime = 0.1; // 默认100ms
        }
        
        // 计算航向变化（考虑跨越0/360度）
        double headingChange = calculateAngleDifference(rawHeading, previousHeading);
        
        // 异常值检测
        if (Math.abs(headingChange) > outlierThreshold) {
            // 检查是否是合理的快速转向
            double headingRate = Math.abs(headingChange) / deltaTime;
            if (headingRate > maxHeadingRate * 2) {
                // 可能是异常值，使用预测值
                outlierCount++;
                rawHeading = predictHeading(deltaTime);
            }
        }
        
        // 航向变化率限制
        double maxChange = maxHeadingRate * deltaTime;
        if (Math.abs(headingChange) > maxChange) {
            // 限制变化幅度
            double sign = headingChange > 0 ? 1 : -1;
            rawHeading = normalizeAngle(previousHeading + sign * maxChange);
        }
        
        // 低通滤波（考虑角度跨越问题）
        filteredHeading = filterAngle(filteredHeading, rawHeading, alpha);
        
        // 更新状态
        previousHeading = rawHeading;
        previousTimestamp = timestamp;
        
        return filteredHeading;
    }
    
    /**
     * 对角度进行低通滤波
     * 处理角度跨越0/360度的情况
     */
    private double filterAngle(double current, double target, double alpha) {
        // 计算最短路径的角度差
        double diff = calculateAngleDifference(target, current);
        
        // 应用滤波
        double result = current + alpha * diff;
        
        return normalizeAngle(result);
    }
    
    /**
     * 计算两个角度之间的差值（-180到180度）
     */
    private double calculateAngleDifference(double angle1, double angle2) {
        double diff = angle1 - angle2;
        
        // 归一化到-180到180度
        while (diff > 180) {
            diff -= 360;
        }
        while (diff < -180) {
            diff += 360;
        }
        
        return diff;
    }
    
    /**
     * 预测航向（基于上一时刻的变化趋势）
     */
    private double predictHeading(double deltaTime) {
        // 简单预测：保持上一时刻的航向
        return previousHeading;
    }
    
    /**
     * 归一化角度到0-360度
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
     * 重置滤波器状态
     */
    public void reset() {
        filteredHeading = 0;
        previousHeading = 0;
        previousTimestamp = 0;
        initialized = false;
        totalSamples = 0;
        outlierCount = 0;
    }
    
    /**
     * 设置初始航向
     */
    public void setInitialHeading(double heading) {
        filteredHeading = normalizeAngle(heading);
        previousHeading = filteredHeading;
        previousTimestamp = System.currentTimeMillis();
        initialized = true;
    }
    
    // Getters and Setters
    public double getAlpha() {
        return alpha;
    }
    
    public void setAlpha(double alpha) {
        this.alpha = Math.max(0.01, Math.min(1.0, alpha));
    }
    
    public double getMaxHeadingRate() {
        return maxHeadingRate;
    }
    
    public void setMaxHeadingRate(double maxHeadingRate) {
        this.maxHeadingRate = maxHeadingRate;
    }
    
    public double getOutlierThreshold() {
        return outlierThreshold;
    }
    
    public void setOutlierThreshold(double outlierThreshold) {
        this.outlierThreshold = outlierThreshold;
    }
    
    public double getFilteredHeading() {
        return filteredHeading;
    }
    
    public int getTotalSamples() {
        return totalSamples;
    }
    
    public int getOutlierCount() {
        return outlierCount;
    }
    
    public double getOutlierRate() {
        return totalSamples > 0 ? (double) outlierCount / totalSamples : 0;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
}
