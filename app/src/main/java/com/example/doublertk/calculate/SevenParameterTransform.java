package com.example.doublertk.calculate;

/**
 * 七参数三维坐标转换
 * 1. 平移量 Tx (X 方向)
 * 2. 平移量 Ty (Y 方向)
 * 3. 平移量 Tz (Z 方向)
 * 4. 缩放因子 m (无量纲，通常表示为 1+m 形式)
 * 5. 旋转参数 ωx (绕X轴旋转，单位：弧度)
 * 6. 旋转参数 ωy (绕Y轴旋转，单位：弧度)
 * 7. 旋转参数 ωz (绕Z轴旋转，单位：弧度)
 *
 * 变换公式（基于旋转矩阵）：
 * [X₂]   [Tx]       [1   -ωz   ωy ] [X₁]
 * [Y₂] = [Ty] + (1+m)[ωz   1   -ωx] [Y₁]
 * [Z₂]   [Tz]       [-ωy  ωx   1 ] [Z₁]
 *
 * 线性化形式（用于最小二乘求解）：
 * X₂ = Tx + (1+m)X₁ - ωz·Y₁ + ωy·Z₁
 * Y₂ = Ty + ωz·X₁ + (1+m)Y₁ - ωx·Z₁
 * Z₂ = Tz - ωy·X₁ + ωx·Y₁ + (1+m)Z₁
 */
public class SevenParameterTransform {

    // 弧秒到弧度的转换因子
    private static final double ARCSEC_TO_RAD = Math.PI / (180.0 * 3600.0);

    private double tx;      // 平移量 Tx (X方向，单位：米)
    private double ty;      // 平移量 Ty (Y方向，单位：米)
    private double tz;      // 平移量 Tz (Z方向，单位：米)
    private double m;       // 缩放因子 m (无量纲，实际缩放为1+m)
    private double wx;      // 旋转参数 ωx (绕X轴，单位：弧度)
    private double wy;      // 旋转参数 ωy (绕Y轴，单位：弧度)
    private double wz;      // 旋转参数 ωz (绕Z轴，单位：弧度)

    public SevenParameterTransform() {
        this.m = 0.0; // 默认无缩放
    }

    public SevenParameterTransform(double tx, double ty, double tz, double m,
                                   double wx, double wy, double wz) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.m = m;
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
    }

    /**
     * 兼容性构造函数（使用弧秒单位的旋转参数）
     */
    public SevenParameterTransform(double tx, double ty, double tz, double m,
                                   double wxArcsec, double wyArcsec, double wzArcsec,
                                   boolean useArcsec) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.m = m;
        if (useArcsec) {
            this.wx = wxArcsec * ARCSEC_TO_RAD;
            this.wy = wyArcsec * ARCSEC_TO_RAD;
            this.wz = wzArcsec * ARCSEC_TO_RAD;
        } else {
            this.wx = wxArcsec;
            this.wy = wyArcsec;
            this.wz = wzArcsec;
        }
    }

    // Getters and Setters
    public double getTx() { return tx; }
    public void setTx(double tx) { this.tx = tx; }

    public double getTy() { return ty; }
    public void setTy(double ty) { this.ty = ty; }

    public double getTz() { return tz; }
    public void setTz(double tz) { this.tz = tz; }

    public double getM() { return m; }
    public void setM(double m) { this.m = m; }

    public double getWx() { return wx; }
    public void setWx(double wx) { this.wx = wx; }

    public double getWy() { return wy; }
    public void setWy(double wy) { this.wy = wy; }

    public double getWz() { return wz; }
    public void setWz(double wz) { this.wz = wz; }

    /**
     * 获取弧秒单位的旋转参数
     */
    public double getWxArcsec() { return wx / ARCSEC_TO_RAD; }
    public double getWyArcsec() { return wy / ARCSEC_TO_RAD; }
    public double getWzArcsec() { return wz / ARCSEC_TO_RAD; }

    /**
     * 设置弧秒单位的旋转参数
     */
    public void setWxArcsec(double wxArcsec) { this.wx = wxArcsec * ARCSEC_TO_RAD; }
    public void setWyArcsec(double wyArcsec) { this.wy = wyArcsec * ARCSEC_TO_RAD; }
    public void setWzArcsec(double wzArcsec) { this.wz = wzArcsec * ARCSEC_TO_RAD; }

    // 兼容性方法，用于与现有代码兼容
    public double getCx() { return tx; }
    public void setCx(double cx) { this.tx = cx; }

    public double getCy() { return ty; }
    public void setCy(double cy) { this.ty = cy; }

    public double getCz() { return tz; }
    public void setCz(double cz) { this.tz = cz; }

    public double getS() { return m * 1e6; } // 转换为百万分之一单位
    public void setS(double s) { this.m = s * 1e-6; }

    public double getRx() { return wx / ARCSEC_TO_RAD; } // 转换为弧秒
    public void setRx(double rx) { this.wx = rx * ARCSEC_TO_RAD; }

    public double getRy() { return wy / ARCSEC_TO_RAD; } // 转换为弧秒
    public void setRy(double ry) { this.wy = ry * ARCSEC_TO_RAD; }

    public double getRz() { return wz / ARCSEC_TO_RAD; } // 转换为弧秒
    public void setRz(double rz) { this.wz = rz * ARCSEC_TO_RAD; }

    public double getDx() { return tx; }
    public void setDx(double dx) { this.tx = dx; }

    public double getDy() { return ty; }
    public void setDy(double dy) { this.ty = dy; }

    public double getDz() { return tz; }
    public void setDz(double dz) { this.tz = dz; }

    /**
     * 对单点进行三维坐标转换
     * 根据图片公式实现：
     * X₂ = Tx + (1+m)X₁ - ωz·Y₁ + ωy·Z₁
     * Y₂ = Ty + ωz·X₁ + (1+m)Y₁ - ωx·Z₁
     * Z₂ = Tz - ωy·X₁ + ωx·Y₁ + (1+m)Z₁
     *
     * @param x 原始 X坐标
     * @param y 原始 Y坐标
     * @param z 原始 Z坐标
     * @return 长度为 3 的数组 [X', Y', Z']
     */
    public double[] transform(double x, double y, double z) {
        // 缩放因子 (1 + m)
        double scale = 1.0 + m;

        // 应用七参数变换公式（根据图片中的线性化公式）
        double xPrime = tx + scale * x - wz * y + wy * z;
        double yPrime = ty + wz * x + scale * y - wx * z;
        double zPrime = tz - wy * x + wx * y + scale * z;

        return new double[]{xPrime, yPrime, zPrime};
    }

    /**
     * 便捷静态方法，无需显式构造对象
     * @param x 原始X坐标
     * @param y 原始Y坐标
     * @param z 原始Z坐标
     * @param tx 平移参数Tx
     * @param ty 平移参数Ty
     * @param tz 平移参数Tz
     * @param m 缩放因子m
     * @param wx 旋转参数ωx (弧度)
     * @param wy 旋转参数ωy (弧度)
     * @param wz 旋转参数ωz (弧度)
     */
    public static double[] transform(double x, double y, double z,
                                     double tx, double ty, double tz, double m,
                                     double wx, double wy, double wz) {
        // 缩放因子 (1 + m)
        double scale = 1.0 + m;

        // 应用七参数变换公式（根据图片中的线性化公式）
        double xPrime = tx + scale * x - wz * y + wy * z;
        double yPrime = ty + wz * x + scale * y - wx * z;
        double zPrime = tz - wy * x + wx * y + scale * z;

        return new double[]{xPrime, yPrime, zPrime};
    }

    /**
     * 兼容性静态方法（使用弧秒单位的旋转参数）
     */
    public static double[] transformWithArcsec(double x, double y, double z,
                                               double tx, double ty, double tz, double sMillionth,
                                               double wxArcsec, double wyArcsec, double wzArcsec) {
        // 转换参数
        double m = sMillionth * 1e-6;  // 百万分之一转换为无量纲
        double wx = wxArcsec * ARCSEC_TO_RAD;  // 弧秒转弧度
        double wy = wyArcsec * ARCSEC_TO_RAD;
        double wz = wzArcsec * ARCSEC_TO_RAD;

        return transform(x, y, z, tx, ty, tz, m, wx, wy, wz);
    }

    @Override
    public String toString() {
        return String.format("Seven-Param Transform: Tx=%.6f Ty=%.6f Tz=%.6f m=%.9f ωx=%.6f\" ωy=%.6f\" ωz=%.6f\"",
                tx, ty, tz, m, getWxArcsec(), getWyArcsec(), getWzArcsec());
    }
}