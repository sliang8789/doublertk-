package com.example.doublertk.calculate;

/**
 * 二维三参数平面坐标转换（常见于测绘平面坐标系之间转换）
 * 1. 平移量  dx (X 方向)
 * 2. 平移量  dy (Y 方向)
 * 3. 旋转角  theta  (单位: 弧度，顺时针为正)
 *
 * 变换公式：
 * X' =  cosθ · X - sinθ · Y + dx
 * Y' =  sinθ · X + cosθ · Y + dy
 */
public class ThreeParameterTransform {

    private double dx;      // 平移量 (X)
    private double dy;      // 平移量 (Y)

    private double dz;

    private double rmse;
    private double theta;   // 旋转角 (rad)

    public ThreeParameterTransform() {
    }

    public ThreeParameterTransform(double dx, double dy, double theta) {
        this.dx = dx;
        this.dy = dy;
        this.theta = theta;
    }

    public double getDx() {
        return dx;
    }

    public void setDx(double dx) {
        this.dx = dx;
    }

    public double getDy() {
        return dy;
    }

    public void setDy(double dy) {
        this.dy = dy;
    }

    public double getDz() {
        return dz;
    }

    public void setDz(double dz) {
        this.dz = dz;
    }

    public void setRmse(double rmse) {
        this.rmse = rmse;
    }

    public double getTheta() {
        return theta;
    }

    public void setTheta(double theta) {
        this.theta = theta;
    }

    /**
     * 对单点进行坐标转换
     *
     * @param x 原始 X
     * @param y 原始 Y
     * @return 长度为 2 的数组 [X', Y']
     */
    public double[] transform(double x, double y) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double xPrime = cos * x - sin * y + dx;
        double yPrime = sin * x + cos * y + dy;
        return new double[]{xPrime, yPrime};
    }

    /**
     * 便捷静态方法，无需显式构造对象
     */
    public static double[] transform(double x, double y, double dx, double dy, double theta) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double xPrime = cos * x - sin * y + dx;
        double yPrime = sin * x + cos * y + dy;
        return new double[]{xPrime, yPrime};
    }

    @Override
    public String toString() {
        return String.format("Three-Param Transform: dx=%.6f  dy=%.6f  theta(rad)=%.10f", dx, dy, theta);
    }
}