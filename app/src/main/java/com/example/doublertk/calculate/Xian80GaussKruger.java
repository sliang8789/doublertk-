package com.example.doublertk.calculate;

/**
 * 西安80椭球 + 高斯-克吕格投影
 *
 * 功能：将经纬度（度）转换为平面坐标（x北 y东）。
 * 去除3°/6°分带逻辑，直接指定中央经线；可选500km假东。
 */
public final class Xian80GaussKruger {

    // 椭球参数（IAG75）：a与1/f
    private static final double SEMI_MAJOR_AXIS = 6378140.0; // a 半长轴
    private static final double INVERSE_FLATTENING = 298.257; // 1/f 反扁率 (IAG75)
    private static final double FLATTENING = 1.0 / INVERSE_FLATTENING;
    private static final double FIRST_ECCENTRICITY_SQUARED = 2.0 * FLATTENING - FLATTENING * FLATTENING; // e^2 第一偏心率平方
    private static final double SECOND_ECCENTRICITY_SQUARED = FIRST_ECCENTRICITY_SQUARED / (1.0 - FIRST_ECCENTRICITY_SQUARED); // e'^2 第二偏心率平方

    public static final class Result {
        public final double northing; // x (m)
        public final double easting;  // y (m)
        public final double centralMeridianDegrees; // 中央经线（度）

        public Result(double northing, double easting, double centralMeridianDegrees) {
            this.northing = northing;
            this.easting = easting;
            this.centralMeridianDegrees = centralMeridianDegrees;
        }
    }

    /**
     * 使用CentralMeridianCalculation自动计算中央经线进行投影
     * @param longitudeDegrees RTK经度（度）
     * @param latitudeDegrees RTK纬度（度）
     * @param addFalseEasting500k 是否添加500km假东到y
     * @return 投影结果，包含计算出的中央经线
     */
    public static Result projectWithAutoCalculatedCentralMeridian(double longitudeDegrees, double latitudeDegrees,
                                                                 boolean addFalseEasting500k) {
        CentralMeridianCalculation.GaussKrugerResult centralMeridianResult = 
            CentralMeridianCalculation.calculateForGaussKruger3Degree(longitudeDegrees, latitudeDegrees);
        return projectWithCentralMeridian(longitudeDegrees, latitudeDegrees, 
                                         centralMeridianResult.centralMeridianDegrees, addFalseEasting500k);
    }

    /**
     * 指定中央经线进行投影
     * @param longitudeDegrees 经度（度）
     * @param latitudeDegrees  纬度（度）
     * @param centralMeridianDegrees 中央经线（度）
     * @param addFalseEasting500k 是否添加500km假东到y
     */
    public static Result projectWithCentralMeridian(double longitudeDegrees, double latitudeDegrees,
                                                    double centralMeridianDegrees,
                                                    boolean addFalseEasting500k) {
        double a = SEMI_MAJOR_AXIS;
        double e2 = FIRST_ECCENTRICITY_SQUARED;
        double ep2 = SECOND_ECCENTRICITY_SQUARED;

        double B = Math.toRadians(latitudeDegrees);
        double L = Math.toRadians(longitudeDegrees);
        double L0 = Math.toRadians(centralMeridianDegrees);
        double l = L - L0;

        double sinB = Math.sin(B);
        double cosB = Math.cos(B);
        double tanB = Math.tan(B);

        double N = a / Math.sqrt(1.0 - e2 * sinB * sinB);
        double eta2 = ep2 * cosB * cosB;

        // 子午线弧长（到6阶项）
        double e4 = e2 * e2;
        double e6 = e4 * e2;
        double A0 = 1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0;
        double A2 = 3.0 / 8.0 * (e2 + e4 / 4.0 + 15.0 * e6 / 128.0);
        double A4 = 15.0 / 256.0 * (e4 + 3.0 * e6 / 4.0);
        double A6 = 35.0 * e6 / 3072.0;
        double M = a * (A0 * B - A2 * Math.sin(2.0 * B) + A4 * Math.sin(4.0 * B) - A6 * Math.sin(6.0 * B));

        double cos2 = cosB * cosB;
        double t2 = tanB * tanB;

        double l2 = l * l;
        double l3 = l2 * l;
        double l4 = l2 * l2;
        double l5 = l4 * l;
        double l6 = l3 * l3;

        double northing = M
                + N * tanB * (
                (cos2 * l2) / 2.0
                        + Math.pow(cosB, 4) * (5.0 - t2 + 9.0 * eta2 + 4.0 * eta2 * eta2) * l4 / 24.0
                        + Math.pow(cosB, 6) * (61.0 - 58.0 * t2 + t2 * t2 + 270.0 * eta2 - 330.0 * t2 * eta2) * l6 / 720.0
        );

        double easting = N * cosB * (
                l
                        + cos2 * (1.0 - t2 + eta2) * l3 / 6.0
                        + Math.pow(cosB, 4) * (5.0 - 18.0 * t2 + t2 * t2 + 14.0 * eta2 - 58.0 * t2 * eta2) * l5 / 120.0
        );

        if (addFalseEasting500k) {
            easting += 500000.0;
        }

        return new Result(northing, easting, centralMeridianDegrees);
    }
}


