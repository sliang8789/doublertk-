package com.example.doublertk.calculate;

public final class WGS84ToUTM {

    private static final double A = 6378137.0;                 // WGS84 赤道半径
    private static final double INV_F = 298.257223563;         // 扁率倒数
    private static final double F = 1.0 / INV_F;               // 扁率
    private static final double B = A * (1.0 - F);            // 极半径
    private static final double E2 = (A * A - B * B) / (A * A); // 第一偏心率平方
    private static final double EP2 = E2 / (1.0 - E2);         // 第二偏心率平方
    private static final double K0 = 0.9996;                   // UTM 比例因子
    private static final double FALSE_EASTING = 500000.0;      // 假东偏移

    public static final class UTM {
        public final int zone;          // 分带号 1..60
        public final char hemisphere;   // 半球 'N' 或 'S'
        public final double easting;    // 东坐标（米）
        public final double northing;   // 北坐标（米）
        public UTM(int zone, char hemisphere, double easting, double northing) {
            this.zone = zone;
            this.hemisphere = hemisphere;
            this.easting = easting;
            this.northing = northing;
        }
        @Override
        public String toString() {
            return "带号 " + zone + hemisphere + "  东=" + easting + "  北=" + northing;
        }
    }

    /**
     * 将经纬度转换为 UTM 坐标
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @return UTM 坐标对象
     */
    public static UTM lonLatToUTM(double lonDeg, double latDeg) {
        int zone = computeUTMZone(lonDeg, latDeg);
        double lon0Deg = zone * 6.0 - 183.0;        // 中央经线
        char hemi = latDeg >= 0 ? 'N' : 'S';        // 判断南北半球

        double phi = Math.toRadians(latDeg);        // 纬度转弧度
        double lam = Math.toRadians(lonDeg);        // 经度转弧度
        double lam0 = Math.toRadians(lon0Deg);      // 中央经线转弧度

        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double tanPhi = Math.tan(phi);

        double N = A / Math.sqrt(1.0 - E2 * sinPhi * sinPhi); // 卯酉圈曲率半径
        double T = tanPhi * tanPhi;
        double C = EP2 * cosPhi * cosPhi;
        double A1 = (lam - lam0) * cosPhi;

        double e4 = E2 * E2;
        double e6 = e4 * E2;

        // 计算子午线弧长
        double M = A * ((1.0 - E2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi
                - (3.0 * E2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * Math.sin(2.0 * phi)
                + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * Math.sin(4.0 * phi)
                - (35.0 * e6 / 3072.0) * Math.sin(6.0 * phi));

        double A1_2 = A1 * A1;
        double A1_3 = A1_2 * A1;
        double A1_4 = A1_2 * A1_2;
        double A1_5 = A1_4 * A1;
        double A1_6 = A1_3 * A1_3;

        // 计算东坐标
        double easting = K0 * N * (A1 + (1.0 - T + C) * A1_3 / 6.0
                + (5.0 - 18.0 * T + T * T + 72.0 * C - 58.0 * EP2) * A1_5 / 120.0) + FALSE_EASTING;

        // 计算北坐标
        double northing = K0 * (M + N * tanPhi * (A1_2 / 2.0
                + (5.0 - T + 9.0 * C + 4.0 * C * C) * A1_4 / 24.0
                + (61.0 - 58.0 * T + T * T + 600.0 * C - 330.0 * EP2) * A1_6 / 720.0));

        if (hemi == 'S') {
            northing += 10000000.0; // 南半球假北偏移
        }

        return new UTM(zone, hemi, easting, northing);
    }

    /**
     * 使用CentralMeridianCalculation自动计算中央子午线进行UTM坐标转换
     *
     * @param lonDeg RTK经度（度）
     * @param latDeg RTK纬度（度）
     * @return UTM 坐标对象
     */
    public static UTM lonLatToUTMWithAutoCalculatedCentralMeridian(double lonDeg, double latDeg) {
        CentralMeridianCalculation.UTMResult centralMeridianResult = 
            CentralMeridianCalculation.calculateForUTM(lonDeg, latDeg);
        return lonLatToUTM(lonDeg, latDeg, centralMeridianResult.centralMeridianDegrees, centralMeridianResult.zoneNumber);
    }

    /**
     * 使用指定中央子午线（度）计算 UTM 坐标。若提供 zoneOpt 则用于返回值展示；
     * 计算采用 WGS84 椭球与 UTM 标准参数（K0=0.9996，FALSE_EASTING=500000）。
     */
    public static UTM lonLatToUTM(double lonDeg, double latDeg, double centralMeridianDeg, Integer zoneOpt) {
        double lon0Deg = centralMeridianDeg;
        int zone = (zoneOpt != null) ? zoneOpt : (int)Math.round((centralMeridianDeg + 183.0) / 6.0);
        char hemi = latDeg >= 0 ? 'N' : 'S';

        double phi = Math.toRadians(latDeg);
        double lam = Math.toRadians(lonDeg);
        double lam0 = Math.toRadians(lon0Deg);

        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double tanPhi = Math.tan(phi);

        double N = A / Math.sqrt(1.0 - E2 * sinPhi * sinPhi);
        double T = tanPhi * tanPhi;
        double C = EP2 * cosPhi * cosPhi;
        double A1 = (lam - lam0) * cosPhi;

        double e4 = E2 * E2;
        double e6 = e4 * E2;

        double M = A * ((1.0 - E2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi
                - (3.0 * E2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * Math.sin(2.0 * phi)
                + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * Math.sin(4.0 * phi)
                - (35.0 * e6 / 3072.0) * Math.sin(6.0 * phi));

        double A1_2 = A1 * A1;
        double A1_3 = A1_2 * A1;
        double A1_4 = A1_2 * A1_2;
        double A1_5 = A1_4 * A1;
        double A1_6 = A1_3 * A1_3;

        double easting = K0 * N * (A1 + (1.0 - T + C) * A1_3 / 6.0
                + (5.0 - 18.0 * T + T * T + 72.0 * C - 58.0 * EP2) * A1_5 / 120.0) + FALSE_EASTING;

        double northing = K0 * (M + N * tanPhi * (A1_2 / 2.0
                + (5.0 - T + 9.0 * C + 4.0 * C * C) * A1_4 / 24.0
                + (61.0 - 58.0 * T + T * T + 600.0 * C - 330.0 * EP2) * A1_6 / 720.0));

        if (hemi == 'S') {
            northing += 10000000.0;
        }

        return new UTM(zone, hemi, easting, northing);
    }

    /**
     * 计算 UTM 分带号，含挪威和斯瓦尔巴德特殊带规则
     * @param lonDeg 经度（度）
     * @param latDeg 纬度（度）
     * @return 分带号
     */
    private static int computeUTMZone(double lonDeg, double latDeg) {
        int zone = (int)Math.floor((lonDeg + 180.0) / 6.0) + 1;

        // 挪威特殊规则: 56°N~64°N 且 3°E~12°E 使用 32 带
        if (latDeg >= 56.0 && latDeg < 64.0 && lonDeg >= 3.0 && lonDeg < 12.0) {
            zone = 32;
        }

        // 斯瓦尔巴德特殊规则: 72°N~84°N
        if (latDeg >= 72.0 && latDeg < 84.0) {
            if      (lonDeg >= 0.0  && lonDeg < 9.0)  zone = 31;
            else if (lonDeg >= 9.0  && lonDeg < 21.0) zone = 33;
            else if (lonDeg >= 21.0 && lonDeg < 33.0) zone = 35;
            else if (lonDeg >= 33.0 && lonDeg < 42.0) zone = 37;
        }

        // 确保分带号在 1..60 范围内
        if (zone < 1) zone = 1;
        if (zone > 60) zone = 60;
        return zone;
    }
}