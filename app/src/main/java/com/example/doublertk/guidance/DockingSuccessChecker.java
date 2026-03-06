package com.example.doublertk.guidance;

/**
 * 停靠成功检测器
 * 判断船舶是否成功停靠到目标泊位
 * 
 * 判定条件:
 * 1. RTK必须是固定解
 * 2. 船头RTK到北桩距离 < 位置阈值
 * 3. 船尾RTK到南桩距离 < 位置阈值
 * 4. 航向误差 < 航向阈值
 * 5. 以上条件需要稳定保持一定帧数
 */
public class DockingSuccessChecker {
    
    // 阈值配置
    private double positionThreshold = 0.3;   // 位置误差阈值 (m)
    private double headingThreshold = 5.0;    // 航向误差阈值 (度)
    private int stableCountRequired = 10;     // 需要稳定的帧数
    
    // 状态
    private int stableCount = 0;
    private boolean lastCheckResult = false;
    private long lastCheckTime = 0;
    
    // 统计
    private int totalChecks = 0;
    private int successChecks = 0;
    
    public DockingSuccessChecker() {
    }
    
    public DockingSuccessChecker(double positionThreshold, double headingThreshold, int stableCountRequired) {
        this.positionThreshold = positionThreshold;
        this.headingThreshold = headingThreshold;
        this.stableCountRequired = stableCountRequired;
    }
    
    /**
     * 检查是否停靠成功
     * @param bowError 船头到北桩距离 (m)
     * @param sternError 船尾到南桩距离 (m)
     * @param headingError 航向误差绝对值 (度)
     * @param rtkFixed 是否为RTK固定解
     * @return 是否停靠成功
     */
    public boolean check(double bowError, double sternError, double headingError, boolean rtkFixed) {
        totalChecks++;
        lastCheckTime = System.currentTimeMillis();
        
        // 条件1：RTK必须是固定解
        if (!rtkFixed) {
            stableCount = 0;
            lastCheckResult = false;
            return false;
        }
        
        // 条件2：船头RTK到北桩距离 < 阈值
        if (bowError > positionThreshold) {
            stableCount = 0;
            lastCheckResult = false;
            return false;
        }
        
        // 条件3：船尾RTK到南桩距离 < 阈值
        if (sternError > positionThreshold) {
            stableCount = 0;
            lastCheckResult = false;
            return false;
        }
        
        // 条件4：航向误差 < 阈值
        if (headingError > headingThreshold) {
            stableCount = 0;
            lastCheckResult = false;
            return false;
        }
        
        // 条件5：需要稳定一段时间（防止抖动误判）
        stableCount++;
        successChecks++;
        
        if (stableCount >= stableCountRequired) {
            lastCheckResult = true;
            return true;
        }
        
        lastCheckResult = false;
        return false;
    }
    
    /**
     * 检查是否停靠成功（简化版本）
     * @param totalError 总位置误差 (m)
     * @param headingError 航向误差绝对值 (度)
     * @param rtkFixed 是否为RTK固定解
     * @return 是否停靠成功
     */
    public boolean checkSimple(double totalError, double headingError, boolean rtkFixed) {
        return check(totalError, totalError, headingError, rtkFixed);
    }
    
    /**
     * 重置检测器状态
     */
    public void reset() {
        stableCount = 0;
        lastCheckResult = false;
        totalChecks = 0;
        successChecks = 0;
    }
    
    /**
     * 获取当前稳定计数
     */
    public int getStableCount() {
        return stableCount;
    }
    
    /**
     * 获取稳定进度百分比
     */
    public double getStableProgress() {
        return Math.min(1.0, (double) stableCount / stableCountRequired);
    }
    
    /**
     * 获取上次检查结果
     */
    public boolean getLastCheckResult() {
        return lastCheckResult;
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        return totalChecks > 0 ? (double) successChecks / totalChecks : 0;
    }
    
    // Getters and Setters
    public double getPositionThreshold() {
        return positionThreshold;
    }
    
    public void setPositionThreshold(double positionThreshold) {
        this.positionThreshold = positionThreshold;
    }
    
    public double getHeadingThreshold() {
        return headingThreshold;
    }
    
    public void setHeadingThreshold(double headingThreshold) {
        this.headingThreshold = headingThreshold;
    }
    
    public int getStableCountRequired() {
        return stableCountRequired;
    }
    
    public void setStableCountRequired(int stableCountRequired) {
        this.stableCountRequired = stableCountRequired;
    }
    
    public int getTotalChecks() {
        return totalChecks;
    }
    
    public int getSuccessChecks() {
        return successChecks;
    }
}
