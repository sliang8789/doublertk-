package com.example.doublertk.guidance;

import com.example.doublertk.rtk.RTKPosition;

/**
 * 停靠引导系统
 * 计算船舶当前位置与目标泊位的偏差，生成引导指令
 * 
 * 功能:
 * 1. 计算船头RTK到北桩的距离误差
 * 2. 计算船尾RTK到南桩的距离误差
 * 3. 计算航向误差
 * 4. 生成引导向量（平移和旋转）
 * 5. 判断停靠状态
 */
public class DockingGuidance {
    
    // 停靠状态
    public enum DockingState {
        IDLE,           // 空闲，未选择目标
        APPROACHING,    // 接近中
        ALIGNING,       // 对准中
        DOCKED,         // 已停靠
        FAILED          // 停靠失败
    }
    
    // 引导结果
    public static class GuidanceResult {
        public double bowError;         // 船头到北桩距离 (m)
        public double sternError;       // 船尾到南桩距离 (m)
        public double headingError;     // 航向误差 (度)
        public double lateralError;     // 横向偏差 (m)
        public double longitudinalError; // 纵向偏差 (m)
        public double totalError;       // 总误差 (m)
        
        public double guidanceX;        // 引导向量X (m, 正=右)
        public double guidanceY;        // 引导向量Y (m, 正=前)
        public double guidanceHeading;  // 引导航向调整 (度, 正=顺时针)
        
        public DockingState state;      // 停靠状态
        public String[] hints;          // 引导提示文字
        
        public GuidanceResult() {
            hints = new String[0];
            state = DockingState.IDLE;
        }
    }
    
    // 目标泊位（桩点对坐标，米）
    private double targetNorthX, targetNorthY;  // 北桩坐标
    private double targetSouthX, targetSouthY;  // 南桩坐标
    private double targetHeading;               // 目标航向
    private boolean hasTarget = false;
    
    // 当前船舶状态
    private double shipCenterX, shipCenterY;    // 船舶中心坐标
    private double shipHeading;                 // 船舶航向
    private double bowX, bowY;                  // 船头RTK坐标
    private double sternX, sternY;              // 船尾RTK坐标
    
    // 天线基线长度
    private double antennaBaseline = 10.0;
    
    // 阈值配置
    private double approachThreshold = 20.0;    // 接近阈值 (m)
    private double alignThreshold = 5.0;        // 对准阈值 (m)
    private double dockedPositionThreshold = 0.3; // 停靠位置阈值 (m)
    private double dockedHeadingThreshold = 5.0;  // 停靠航向阈值 (度)
    
    // 引导提示阈值
    private double hintPositionThreshold = 0.5;   // 位置提示阈值 (m)
    private double hintHeadingThreshold = 2.0;    // 航向提示阈值 (度)
    
    // 停靠成功检测器
    private DockingSuccessChecker successChecker;
    
    public DockingGuidance() {
        successChecker = new DockingSuccessChecker();
    }
    
    /**
     * 设置目标泊位（桩点对）
     * @param northX 北桩X坐标 (m)
     * @param northY 北桩Y坐标 (m)
     * @param southX 南桩X坐标 (m)
     * @param southY 南桩Y坐标 (m)
     */
    public void setTarget(double northX, double northY, double southX, double southY) {
        this.targetNorthX = northX;
        this.targetNorthY = northY;
        this.targetSouthX = southX;
        this.targetSouthY = southY;
        
        // 计算目标航向（从南桩指向北桩）
        double dx = northX - southX;
        double dy = northY - southY;
        this.targetHeading = Math.toDegrees(Math.atan2(dx, dy));
        if (targetHeading < 0) targetHeading += 360;
        
        this.hasTarget = true;
        successChecker.reset();
    }
    
    /**
     * 清除目标
     */
    public void clearTarget() {
        this.hasTarget = false;
        successChecker.reset();
    }
    
    /**
     * 更新船舶状态
     * @param bowPosition 船头RTK位置
     * @param sternPosition 船尾RTK位置
     * @param heading 航向 (度)
     */
    public void updateShipState(RTKPosition bowPosition, RTKPosition sternPosition, double heading) {
        if (bowPosition != null) {
            // 需要将经纬度转换为局部坐标，这里假设已经转换
            // 实际使用时需要配合CoordinateTransformService
        }
        this.shipHeading = heading;
    }
    
    /**
     * 更新船舶状态（使用局部坐标）
     * @param bowX 船头X坐标 (m)
     * @param bowY 船头Y坐标 (m)
     * @param sternX 船尾X坐标 (m)
     * @param sternY 船尾Y坐标 (m)
     * @param heading 航向 (度)
     */
    public void updateShipState(double bowX, double bowY, double sternX, double sternY, double heading) {
        this.bowX = bowX;
        this.bowY = bowY;
        this.sternX = sternX;
        this.sternY = sternY;
        this.shipCenterX = (bowX + sternX) / 2;
        this.shipCenterY = (bowY + sternY) / 2;
        this.shipHeading = heading;
    }
    
    /**
     * 计算引导结果
     * @param rtkFixed 是否为RTK固定解
     * @return 引导结果
     */
    public GuidanceResult compute(boolean rtkFixed) {
        GuidanceResult result = new GuidanceResult();
        
        if (!hasTarget) {
            result.state = DockingState.IDLE;
            result.hints = new String[]{"请选择目标泊位"};
            return result;
        }
        
        // 计算船头到北桩的距离
        result.bowError = distance(bowX, bowY, targetNorthX, targetNorthY);
        
        // 计算船尾到南桩的距离
        result.sternError = distance(sternX, sternY, targetSouthX, targetSouthY);
        
        // 计算航向误差
        result.headingError = normalizeAngle180(targetHeading - shipHeading);
        
        // 计算目标中心
        double targetCenterX = (targetNorthX + targetSouthX) / 2;
        double targetCenterY = (targetNorthY + targetSouthY) / 2;
        
        // 计算横向和纵向偏差（相对于目标航向）
        double dx = shipCenterX - targetCenterX;
        double dy = shipCenterY - targetCenterY;
        double targetRad = Math.toRadians(targetHeading);
        
        // 旋转到目标坐标系
        result.lateralError = dx * Math.cos(targetRad) - dy * Math.sin(targetRad);
        result.longitudinalError = dx * Math.sin(targetRad) + dy * Math.cos(targetRad);
        
        // 总误差
        result.totalError = (result.bowError + result.sternError) / 2;
        
        // 计算引导向量（船体坐标系）
        double shipRad = Math.toRadians(shipHeading);
        double toTargetX = targetCenterX - shipCenterX;
        double toTargetY = targetCenterY - shipCenterY;
        
        // 转换到船体坐标系（前/后/左/右）
        result.guidanceY = toTargetX * Math.sin(shipRad) + toTargetY * Math.cos(shipRad);  // 前进方向
        result.guidanceX = toTargetX * Math.cos(shipRad) - toTargetY * Math.sin(shipRad);  // 右侧方向
        result.guidanceHeading = result.headingError;
        
        // 判断停靠状态
        if (result.totalError > approachThreshold) {
            result.state = DockingState.APPROACHING;
        } else if (result.totalError > alignThreshold) {
            result.state = DockingState.ALIGNING;
        } else {
            // 检查是否停靠成功
            boolean docked = successChecker.check(
                result.bowError, result.sternError, 
                Math.abs(result.headingError), rtkFixed
            );
            result.state = docked ? DockingState.DOCKED : DockingState.ALIGNING;
        }
        
        // 生成引导提示
        result.hints = generateHints(result);
        
        return result;
    }
    
    /**
     * 生成引导提示文字
     */
    private String[] generateHints(GuidanceResult result) {
        java.util.List<String> hints = new java.util.ArrayList<>();
        
        if (result.state == DockingState.DOCKED) {
            hints.add("✓ 停靠成功");
            return hints.toArray(new String[0]);
        }
        
        // 横向调整提示
        if (Math.abs(result.guidanceX) > hintPositionThreshold) {
            if (result.guidanceX > 0) {
                hints.add(String.format("→ 向右调整 %.1fm", result.guidanceX));
            } else {
                hints.add(String.format("← 向左调整 %.1fm", -result.guidanceX));
            }
        }
        
        // 纵向调整提示
        if (Math.abs(result.guidanceY) > hintPositionThreshold) {
            if (result.guidanceY > 0) {
                hints.add(String.format("↑ 向前推进 %.1fm", result.guidanceY));
            } else {
                hints.add(String.format("↓ 向后退 %.1fm", -result.guidanceY));
            }
        }
        
        // 航向调整提示
        if (Math.abs(result.guidanceHeading) > hintHeadingThreshold) {
            if (result.guidanceHeading > 0) {
                hints.add(String.format("↻ 顺时针转 %.1f°", result.guidanceHeading));
            } else {
                hints.add(String.format("↺ 逆时针转 %.1f°", -result.guidanceHeading));
            }
        }
        
        // 如果没有需要调整的，提示保持
        if (hints.isEmpty()) {
            hints.add("保持当前姿态");
        }
        
        return hints.toArray(new String[0]);
    }
    
    /**
     * 计算两点距离
     */
    private double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 角度归一化到-180到180度
     */
    private double normalizeAngle180(double angle) {
        while (angle > 180) angle -= 360;
        while (angle <= -180) angle += 360;
        return angle;
    }
    
    // Getters and Setters
    public boolean hasTarget() {
        return hasTarget;
    }
    
    public double getTargetHeading() {
        return targetHeading;
    }
    
    public void setAntennaBaseline(double baseline) {
        this.antennaBaseline = baseline;
    }
    
    public void setApproachThreshold(double threshold) {
        this.approachThreshold = threshold;
    }
    
    public void setAlignThreshold(double threshold) {
        this.alignThreshold = threshold;
    }
    
    public void setDockedPositionThreshold(double threshold) {
        this.dockedPositionThreshold = threshold;
        successChecker.setPositionThreshold(threshold);
    }
    
    public void setDockedHeadingThreshold(double threshold) {
        this.dockedHeadingThreshold = threshold;
        successChecker.setHeadingThreshold(threshold);
    }
    
    public DockingSuccessChecker getSuccessChecker() {
        return successChecker;
    }
}
