package com.example.doublertk.calculate;

import android.util.Log;

/**
 * 基于CGCS2000椭球的高斯-克吕格投影计算工具类。
 * 将经度和纬度（单位：度）转换为东坐标和北坐标（单位：米）。
 * 使用 GaussProjection.java 中的简化算法，精度足够（误差 < 0.1 m）。
 * 优化后适用于Android应用。
 */
public final class GaussKrugerCGCS2000 {

    // 椭球参数（CGCS2000专用）
    private static final double SEMI_MAJOR = 6378137.0;                    // 长半轴 (a)
    private static final double INV_F = 298.257222101;                     // 扁率倒数 (1/f)
    private static final double F = 1.0 / INV_F;                           // 扁率
    private static final double SEMI_MINOR = SEMI_MAJOR * (1.0 - F);      // 短半轴 (b)
    private static final double E2 = (SEMI_MAJOR * SEMI_MAJOR - SEMI_MINOR * SEMI_MINOR)
            / (SEMI_MAJOR * SEMI_MAJOR);                                   // 第一偏心率平方
    private static final double EE = E2 / (1.0 - E2);                      // 第二偏心率平方

    private static final double SCALE_K0 = 1.0;         // 比例尺因子
    private static final double FALSE_EASTING = 500000; // 东伪偏移（米）
    private static final double FALSE_NORTHING = 0.0;   // 北伪偏移（米）

    /**
     * 表示投影坐标（东坐标和北坐标），单位为米。
     */
    public static final class XY {
        public final double easting;   // 东坐标（E，米）
        public final double northing;  // 北坐标（N，米）

        public XY(double easting, double northing) {
            this.easting = easting;
            this.northing = northing;
        }

        @Override
        public String toString() {
            return "N=" + northing + ", E=" + easting;
        }
    }

    /**
     * 将经度和纬度（单位：度）转换为高斯-克吕格投影坐标（单位：米）。
     * 中央子午线将根据经度自动计算（3度带）。
     * 
     * @deprecated 建议使用 {@link #lonLatToXY(double, double, double, double, double)} 
     *             或 {@link #lonLatToXYWithAutoCalculatedCentralMeridian(double, double)} 以明确指定中央子午线
     *
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @return XY 对象，包含东坐标和北坐标
     */
    @Deprecated
    public static XY lonLatToXY(double lonDeg, double latDeg) {
        // 不使用硬编码，而是自动计算中央子午线
        return lonLatToXYWithAutoCalculatedCentralMeridian(lonDeg, latDeg);
    }
    
    /**
     * 【推荐使用】将经度和纬度转换为高斯-克吕格投影坐标，中央子午线自动计算（3度带）。
     * 使用 GaussProjection.java 中的简化算法。
     * 
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @return XY 对象，包含东坐标和北坐标
     */
    public static XY lonLatToXYWithAutoCalculation(double lonDeg, double latDeg) {
        try {
            // 使用CentralMeridianCalculation自动计算中央子午线（3度带）
            CentralMeridianCalculation.GaussKrugerResult result = 
                CentralMeridianCalculation.calculateForGaussKruger3Degree(lonDeg, latDeg);
            double centralMeridianDeg = result.centralMeridianDegrees;
            
            // 使用通用的 lonLatToXY 方法，传入 CGCS2000 参数
            return lonLatToXY(lonDeg, latDeg, centralMeridianDeg, SEMI_MAJOR, INV_F);
        } catch (Exception e) {
            Log.e("GaussKrugerCGCS2000", "投影计算错误: " + e.getMessage());
            return new XY(0.0, 0.0); // 出错时返回默认坐标
        }
    }

    /**
     * 【推荐使用】使用CentralMeridianCalculation自动计算中央子午线进行投影转换（3度带）
     *
     * @param lonDeg RTK经度（度）
     * @param latDeg RTK纬度（度）
     * @return XY 对象，包含东坐标和北坐标
     */
    public static XY lonLatToXYWithAutoCalculatedCentralMeridian(double lonDeg, double latDeg) {
        CentralMeridianCalculation.GaussKrugerResult centralMeridianResult = 
            CentralMeridianCalculation.calculateForGaussKruger3Degree(lonDeg, latDeg);
        return lonLatToXY(lonDeg, latDeg, centralMeridianResult.centralMeridianDegrees, SEMI_MAJOR, INV_F);
    }

    /**
     * 【专用于CGCS2000椭球】使用内置的CGCS2000椭球参数进行高斯-克吕格投影
     *
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @param centralMeridianDeg 中央子午线（度）
     * @return XY 对象，包含东坐标和北坐标
     */
    public static XY projectCGCS2000(double lonDeg, double latDeg, double centralMeridianDeg) {
        // CGCS2000椭球参数：a = 6378137.0, 1/f = 298.257222101
        Log.d("GaussKrugerCGCS2000", "========== 高斯投影计算开始 ==========");
        Log.d("GaussKrugerCGCS2000", String.format("输入参数: 经度=%.9f°, 纬度=%.9f°, 中央子午线=%.9f°", 
                lonDeg, latDeg, centralMeridianDeg));
        Log.d("GaussKrugerCGCS2000", String.format("椭球参数: a=%.6f, 1/f=%.9f", SEMI_MAJOR, INV_F));
        
        XY result = lonLatToXY(lonDeg, latDeg, centralMeridianDeg, SEMI_MAJOR, INV_F);
        
        Log.d("GaussKrugerCGCS2000", String.format("投影结果: 北坐标=%.6f m, 东坐标=%.6f m", 
                result.northing, result.easting));
        Log.d("GaussKrugerCGCS2000", "========== 高斯投影计算结束 ==========");
        
        return result;
    }

    /**
     * 【推荐使用】将经纬度转换为高斯-克吕格投影坐标（单位：米），使用指定的中央子午线和椭球参数。
     * 使用 GaussProjection.java 中的简化算法。
     *
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @param centralMeridianDeg 中央子午线（度），从用户配置或CoordinateSystem中获取
     * @param semiMajor 长半轴 a，从椭球体配置中获取
     * @param invF 扁率倒数 1/f，从椭球体配置中获取
     * @return XY 对象，包含东坐标和北坐标
     */
    public static XY lonLatToXY(double lonDeg, double latDeg,
                                double centralMeridianDeg,
                                double semiMajor,
                                double invF) {
        try {
            // 椭球参数计算
            double f = 1.0 / invF;
            double e2 = 2 * f - f * f;  // 第一偏心率平方

            double lat = Math.toRadians(latDeg);
            double lon = Math.toRadians(lonDeg);
            double l0 = Math.toRadians(centralMeridianDeg);

            double sinB = Math.sin(lat);
            double cosB = Math.cos(lat);
            double tanB = sinB / cosB;

            double N = semiMajor / Math.sqrt(1 - e2 * sinB * sinB);   // 卯酉圈曲率半径
            double t = tanB * tanB;
            double c = e2 * cosB * cosB / (1 - e2);
            double A = (lon - l0) * cosB;  // 使用简化形式

            // 子午线弧长 M（使用简化公式，基于 GaussProjection.java）
            double M = semiMajor * ((1 - e2/4 - 3*e2*e2/64 - 5*e2*e2*e2/256) * lat
                    - (3*e2/8 + 3*e2*e2/32 + 45*e2*e2*e2/1024) * Math.sin(2*lat)
                    + (15*e2*e2/256 + 45*e2*e2*e2/1024) * Math.sin(4*lat)
                    - (35*e2*e2*e2/3072) * Math.sin(6*lat));

            // 北坐标 X（基于 GaussProjection.java 算法）
            double X = M + N * tanB * (
                    A*A/2 +
                    (5 - t + 9*c + 4*c*c) * Math.pow(A,4)/24 +
                    (61 - 58*t + t*t + 270*c - 330*t*c) * Math.pow(A,6)/720
            );

            // 东坐标 Y（基于 GaussProjection.java 算法）
            double Y = N * (
                    A +
                    (1 - t + c) * Math.pow(A,3)/6 +
                    (5 - 18*t + t*t + 14*c - 58*t*c) * Math.pow(A,5)/120
            ) + FALSE_EASTING;  // 3°带东偏500km

            // 注意：GaussProjection 返回 [X, Y]，其中 X 是北坐标，Y 是东坐标
            // 而 XY 类中 easting 是东坐标，northing 是北坐标
            double finalEasting = Y;  // Y 是东坐标
            double finalNorthing = X; // X 是北坐标
            
            // 📍 打印详细计算过程日志
            Log.d("GaussKrugerCGCS2000", "========== 投影计算详细过程（GaussProjection算法）==========");
            Log.d("GaussKrugerCGCS2000", String.format("输入: 经度=%.9f°, 纬度=%.9f°, 中央子午线=%.9f°", lonDeg, latDeg, centralMeridianDeg));
            Log.d("GaussKrugerCGCS2000", String.format("椭球参数: a=%.6f, 1/f=%.9f, e²=%.12f", semiMajor, invF, e2));
            Log.d("GaussKrugerCGCS2000", String.format("经差A(弧度): %.12f, A(度): %.9f", A, Math.toDegrees(A)));
            Log.d("GaussKrugerCGCS2000", String.format("卯酉圈曲率半径N: %.6f m", N));
            Log.d("GaussKrugerCGCS2000", String.format("子午线弧长M: %.6f m", M));
            Log.d("GaussKrugerCGCS2000", String.format("投影后北坐标(X): %.6f m", X));
            Log.d("GaussKrugerCGCS2000", String.format("投影后东坐标(Y): %.6f m", Y));
            Log.d("GaussKrugerCGCS2000", String.format("最终北坐标(Northing): %.6f m", finalNorthing));
            Log.d("GaussKrugerCGCS2000", String.format("最终东坐标(Easting): %.6f m (包含伪偏移%.1f)", finalEasting, FALSE_EASTING));
            Log.d("GaussKrugerCGCS2000", "=========================================");
            
            return new XY(finalEasting, finalNorthing);
        } catch (Exception e) {
            Log.e("GaussKrugerCGCS2000", "==================== 投影计算错误 ====================");
            Log.e("GaussKrugerCGCS2000", "错误信息: " + e.getMessage());
            Log.e("GaussKrugerCGCS2000", "堆栈跟踪: ", e);
            return new XY(0.0, 0.0);
        }
    }

    /**
     * 转换坐标并记录日志，方便Android调试。
     * 中央子午线将自动计算（3度带）。
     *
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @return XY 对象，包含东坐标和北坐标
     */
    public static XY convertAndLog(double lonDeg, double latDeg) {
        // 使用自动计算中央子午线的方法，避免硬编码
        XY result = lonLatToXYWithAutoCalculatedCentralMeridian(lonDeg, latDeg);
        Log.d("GaussKrugerCGCS2000", "输入: 经度=" + lonDeg + "°, 纬度=" + latDeg + "°");
        Log.d("GaussKrugerCGCS2000", "转换后的坐标: " + result.toString());
        return result;
    }
    
    /**
     * 转换坐标并记录日志，使用指定的中央子午线（从用户配置中获取）。
     *
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @param centralMeridianDeg 中央子午线（度）
     * @return XY 对象，包含东坐标和北坐标
     */
    public static XY convertAndLog(double lonDeg, double latDeg, double centralMeridianDeg) {
        XY result = lonLatToXY(lonDeg, latDeg, centralMeridianDeg, SEMI_MAJOR, INV_F);
        Log.d("GaussKrugerCGCS2000", "输入: 经度=" + lonDeg + "°, 纬度=" + latDeg + "°, 中央子午线=" + centralMeridianDeg + "°");
        Log.d("GaussKrugerCGCS2000", "转换后的坐标: " + result.toString());
        return result;
    }
}


