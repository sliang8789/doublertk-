package com.example.doublertk.guidance;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径规划器
 * 计算从当前位置到目标泊位的引导路径
 * 
 * 路径策略:
 * 1. 先移动到预对准点（目标泊位延长线上的一点）
 * 2. 调整航向对准泊位方向
 * 3. 沿泊位方向直线进靠
 */
public class PathPlanner {
    
    // 路径点类型
    public enum WaypointType {
        START,          // 起点
        PRE_ALIGN,      // 预对准点
        TARGET          // 目标点
    }
    
    // 路径点
    public static class Waypoint {
        public double x;
        public double y;
        public double heading;      // 到达该点时的目标航向
        public WaypointType type;
        public double speed;        // 建议速度 (m/s)
        
        public Waypoint(double x, double y, double heading, WaypointType type) {
            this.x = x;
            this.y = y;
            this.heading = heading;
            this.type = type;
            this.speed = 0.5;  // 默认速度
        }
        
        public PointF toPointF() {
            return new PointF((float) x, (float) y);
        }
    }
    
    // 规划参数
    private double preAlignDistance = 20.0;     // 预对准点距离目标的距离 (m)
    private double minPreAlignDistance = 5.0;   // 最小预对准距离 (m)
    private double approachSpeed = 0.3;         // 进靠速度 (m/s)
    private double transitSpeed = 0.8;          // 航行速度 (m/s)
    
    // 目标泊位
    private double targetNorthX, targetNorthY;
    private double targetSouthX, targetSouthY;
    private double targetCenterX, targetCenterY;
    private double targetHeading;
    private boolean hasTarget = false;
    
    // 当前路径
    private List<Waypoint> currentPath;
    private int currentWaypointIndex = 0;
    
    public PathPlanner() {
        currentPath = new ArrayList<>();
    }
    
    /**
     * 设置目标泊位
     */
    public void setTarget(double northX, double northY, double southX, double southY) {
        this.targetNorthX = northX;
        this.targetNorthY = northY;
        this.targetSouthX = southX;
        this.targetSouthY = southY;
        this.targetCenterX = (northX + southX) / 2;
        this.targetCenterY = (northY + southY) / 2;
        
        // 计算目标航向（从南桩指向北桩）
        double dx = northX - southX;
        double dy = northY - southY;
        this.targetHeading = Math.toDegrees(Math.atan2(dx, dy));
        if (targetHeading < 0) targetHeading += 360;
        
        this.hasTarget = true;
    }
    
    /**
     * 规划路径
     * @param startX 起点X坐标
     * @param startY 起点Y坐标
     * @param startHeading 起始航向
     * @return 路径点列表
     */
    public List<Waypoint> planPath(double startX, double startY, double startHeading) {
        currentPath.clear();
        currentWaypointIndex = 0;
        
        if (!hasTarget) {
            return currentPath;
        }
        
        // 添加起点
        currentPath.add(new Waypoint(startX, startY, startHeading, WaypointType.START));
        
        // 计算预对准点
        // 预对准点位于目标中心沿目标航向反方向延伸的位置
        double targetRad = Math.toRadians(targetHeading);
        double preAlignX = targetCenterX - Math.sin(targetRad) * preAlignDistance;
        double preAlignY = targetCenterY - Math.cos(targetRad) * preAlignDistance;
        
        // 检查是否需要预对准点
        double distToTarget = distance(startX, startY, targetCenterX, targetCenterY);
        double distToPreAlign = distance(startX, startY, preAlignX, preAlignY);
        
        // 如果当前位置距离目标较近，或者已经在进靠路线上，可以跳过预对准点
        boolean needPreAlign = distToTarget > preAlignDistance * 1.5 && 
                               distToPreAlign < distToTarget;
        
        if (needPreAlign) {
            Waypoint preAlign = new Waypoint(preAlignX, preAlignY, targetHeading, WaypointType.PRE_ALIGN);
            preAlign.speed = transitSpeed;
            currentPath.add(preAlign);
        }
        
        // 添加目标点
        Waypoint target = new Waypoint(targetCenterX, targetCenterY, targetHeading, WaypointType.TARGET);
        target.speed = approachSpeed;
        currentPath.add(target);
        
        return currentPath;
    }
    
    /**
     * 获取当前应该前往的路径点
     * @param currentX 当前X坐标
     * @param currentY 当前Y坐标
     * @return 当前目标路径点
     */
    public Waypoint getCurrentWaypoint(double currentX, double currentY) {
        if (currentPath.isEmpty()) {
            return null;
        }
        
        // 检查是否已到达当前路径点
        while (currentWaypointIndex < currentPath.size() - 1) {
            Waypoint wp = currentPath.get(currentWaypointIndex);
            double dist = distance(currentX, currentY, wp.x, wp.y);
            
            // 到达阈值
            double arrivalThreshold = (wp.type == WaypointType.TARGET) ? 0.5 : 3.0;
            
            if (dist < arrivalThreshold) {
                currentWaypointIndex++;
            } else {
                break;
            }
        }
        
        if (currentWaypointIndex >= currentPath.size()) {
            currentWaypointIndex = currentPath.size() - 1;
        }
        
        return currentPath.get(currentWaypointIndex);
    }
    
    /**
     * 获取到当前路径点的引导向量
     * @param currentX 当前X坐标
     * @param currentY 当前Y坐标
     * @param currentHeading 当前航向
     * @return [dx, dy, dHeading] 引导向量
     */
    public double[] getGuidanceVector(double currentX, double currentY, double currentHeading) {
        Waypoint wp = getCurrentWaypoint(currentX, currentY);
        if (wp == null) {
            return new double[]{0, 0, 0};
        }
        
        double dx = wp.x - currentX;
        double dy = wp.y - currentY;
        double dHeading = normalizeAngle180(wp.heading - currentHeading);
        
        return new double[]{dx, dy, dHeading};
    }
    
    /**
     * 获取路径进度
     * @return 进度百分比 (0-1)
     */
    public double getProgress() {
        if (currentPath.size() <= 1) {
            return 0;
        }
        return (double) currentWaypointIndex / (currentPath.size() - 1);
    }
    
    /**
     * 将路径转换为PointF数组（用于绘制）
     */
    public PointF[] getPathPoints() {
        PointF[] points = new PointF[currentPath.size()];
        for (int i = 0; i < currentPath.size(); i++) {
            points[i] = currentPath.get(i).toPointF();
        }
        return points;
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
    
    /**
     * 重置路径规划器
     */
    public void reset() {
        currentPath.clear();
        currentWaypointIndex = 0;
        hasTarget = false;
    }
    
    // Getters and Setters
    public List<Waypoint> getCurrentPath() {
        return currentPath;
    }
    
    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }
    
    public boolean hasTarget() {
        return hasTarget;
    }
    
    public double getPreAlignDistance() {
        return preAlignDistance;
    }
    
    public void setPreAlignDistance(double distance) {
        this.preAlignDistance = distance;
    }
    
    public double getApproachSpeed() {
        return approachSpeed;
    }
    
    public void setApproachSpeed(double speed) {
        this.approachSpeed = speed;
    }
    
    public double getTransitSpeed() {
        return transitSpeed;
    }
    
    public void setTransitSpeed(double speed) {
        this.transitSpeed = speed;
    }
}
