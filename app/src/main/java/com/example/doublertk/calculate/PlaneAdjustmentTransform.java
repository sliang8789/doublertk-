package com.example.doublertk.calculate;

/**
 * 平面平差变换参数类
 * 
 * 包含平面平差的六个参数：
 * 1. northOrigin - 北原点 (m)
 * 2. eastOrigin - 东原点 (m)  
 * 3. northTranslation - 北平移 (m)
 * 4. eastTranslation - 东平移 (m)
 * 5. rotationScale - 旋转尺度
 * 6. scale - 比例尺
 */
public class PlaneAdjustmentTransform {
    private final double northOrigin;      // 北原点 (m)
    private final double eastOrigin;       // 东原点 (m)
    private final double northTranslation; // 北平移 (m)
    private final double eastTranslation;  // 东平移 (m)
    private final double rotationScale;    // 旋转尺度
    private final double scale;            // 比例尺
    private final double rmse;             // 均方根误差
    
    public PlaneAdjustmentTransform(double northOrigin, double eastOrigin, 
                                   double northTranslation, double eastTranslation,
                                   double rotationScale, double scale, double rmse) {
        this.northOrigin = northOrigin;
        this.eastOrigin = eastOrigin;
        this.northTranslation = northTranslation;
        this.eastTranslation = eastTranslation;
        this.rotationScale = rotationScale;
        this.scale = scale;
        this.rmse = rmse;
    }
    
    // Getter方法
    public double getNorthOrigin() { return northOrigin; }
    public double getEastOrigin() { return eastOrigin; }
    public double getNorthTranslation() { return northTranslation; }
    public double getEastTranslation() { return eastTranslation; }
    public double getRotationScale() { return rotationScale; }
    public double getScale() { return scale; }
    public double getRmse() { return rmse; }
    
    /**
     * 应用平面平差变换
     * 
     * @param x 输入的北坐标
     * @param y 输入的东坐标
     * @return 变换后的坐标 [北坐标, 东坐标]
     */
    public double[] transform(double x, double y) {
        // 基于平面平差模型的坐标变换
        // 这里使用简化的变换模型，实际应用中可能需要更复杂的公式
        double transformedX = northTranslation + scale * x + rotationScale * (x - northOrigin);
        double transformedY = eastTranslation + scale * y + rotationScale * (y - eastOrigin);
        
        return new double[]{transformedX, transformedY};
    }
    
    @Override
    public String toString() {
        return String.format(
            "PlaneAdjustmentTransform{" +
            "northOrigin=%.6f, eastOrigin=%.6f, " +
            "northTranslation=%.6f, eastTranslation=%.6f, " +
            "rotationScale=%.6f, scale=%.6f, rmse=%.6f}",
            northOrigin, eastOrigin, northTranslation, eastTranslation,
            rotationScale, scale, rmse
        );
    }
}