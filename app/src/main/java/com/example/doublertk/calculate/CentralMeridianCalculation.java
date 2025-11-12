package com.example.doublertk.calculate;

/**
 * 中央子午线计算工具类
 * 
 * 功能：根据RTK经纬度坐标计算适合的中央子午线
 * 支持不同的投影方式和分带方式
 */
public final class CentralMeridianCalculation {

    /**
     * 中央子午线计算结果基类
     */
    public static abstract class BaseResult {
        public final double centralMeridianDegrees;  // 中央子午线（度）
        public final String calculationMethod;       // 计算方法描述
        
        protected BaseResult(double centralMeridianDegrees, String calculationMethod) {
            this.centralMeridianDegrees = centralMeridianDegrees;
            this.calculationMethod = calculationMethod;
        }
        
        /**
         * 获取带号（如果适用）
         * @return 带号，高斯投影返回0，UTM返回实际带号
         */
        public abstract int getZoneNumber();
    }

    /**
     * 高斯投影中央子午线计算结果类（不含带号）
     */
    public static final class GaussKrugerResult extends BaseResult {
        
        public GaussKrugerResult(double centralMeridianDegrees, String calculationMethod) {
            super(centralMeridianDegrees, calculationMethod);
        }
        
        @Override
        public int getZoneNumber() {
            return 0; // 高斯投影不使用带号
        }
        
        @Override
        public String toString() {
            return String.format("中央子午线: %.6f°, 方法: %s", 
                centralMeridianDegrees, calculationMethod);
        }
    }
    
    /**
     * UTM投影中央子午线计算结果类（含带号）
     */
    public static final class UTMResult extends BaseResult {
        public final int zoneNumber;                // UTM分带号
        
        public UTMResult(double centralMeridianDegrees, String calculationMethod, int zoneNumber) {
            super(centralMeridianDegrees, calculationMethod);
            this.zoneNumber = zoneNumber;
        }
        
        @Override
        public int getZoneNumber() {
            return zoneNumber;
        }
        
        @Override
        public String toString() {
            return String.format("中央子午线: %.6f°, 方法: %s, UTM带号: %d", 
                centralMeridianDegrees, calculationMethod, zoneNumber);
        }
    }
    
    /**
     * @deprecated 使用 GaussKrugerResult 或 UTMResult 代替
     */
    @Deprecated
    public static final class Result {
        public final double centralMeridianDegrees;
        public final String calculationMethod;
        public final int zoneNumber;
        
        public Result(double centralMeridianDegrees, String calculationMethod, int zoneNumber) {
            this.centralMeridianDegrees = centralMeridianDegrees;
            this.calculationMethod = calculationMethod;
            this.zoneNumber = zoneNumber;
        }
        
        @Override
        public String toString() {
            if (zoneNumber > 0) {
                return String.format("中央子午线: %.6f°, 方法: %s, 带号: %d", 
                    centralMeridianDegrees, calculationMethod, zoneNumber);
            } else {
                return String.format("中央子午线: %.6f°, 方法: %s", 
                    centralMeridianDegrees, calculationMethod);
            }
        }
    }
    
    /**
     * 计算UTM投影的中央子午线
     * 使用6度带，每带宽度6度，中央子午线 = 带号 * 6 - 183
     * 
     * @param longitudeDegrees RTK经度（度）
     * @param latitudeDegrees RTK纬度（度）
     * @return UTM计算结果（包含带号）
     */
    public static UTMResult calculateForUTM(double longitudeDegrees, double latitudeDegrees) {
        // UTM分带计算（标准6度带）
        int zone = (int) Math.floor((longitudeDegrees + 180.0) / 6.0) + 1;
        
        // 处理挪威和斯瓦尔巴德的特殊规则
        if (latitudeDegrees >= 56.0 && latitudeDegrees < 64.0 && 
            longitudeDegrees >= 3.0 && longitudeDegrees < 12.0) {
            zone = 32;
        }
        
        if (latitudeDegrees >= 72.0 && latitudeDegrees < 84.0) {
            if (longitudeDegrees >= 0.0 && longitudeDegrees < 9.0) zone = 31;
            else if (longitudeDegrees >= 9.0 && longitudeDegrees < 21.0) zone = 33;
            else if (longitudeDegrees >= 21.0 && longitudeDegrees < 33.0) zone = 35;
            else if (longitudeDegrees >= 33.0 && longitudeDegrees < 42.0) zone = 37;
        }
        
        // 确保分带号在有效范围内
        zone = Math.max(1, Math.min(60, zone));
        
        double centralMeridian = zone * 6.0 - 183.0;
        
        return new UTMResult(centralMeridian, "UTM 6度带", zone);
    }
    
    /**
     * 计算高斯-克吕格投影的中央子午线（3度带）
     * 标准计算方法：
     * 1. 当地经度除以3，得到商N（四舍五入）
     * 2. 中央子午线 L = 3 × N
     * 
     * 注意：高斯投影不使用带号
     * 
     * @param longitudeDegrees RTK经度（度）
     * @param latitudeDegrees RTK纬度（度）
     * @return 高斯投影计算结果（不含带号）
     */
    public static GaussKrugerResult calculateForGaussKruger3Degree(double longitudeDegrees, double latitudeDegrees) {
        // 3度带标准计算方法：当地经度除以3，四舍五入取整
        int n = (int) Math.round(longitudeDegrees / 3.0);
        
        // 确保n在合理范围内（1-60）
        n = Math.max(1, Math.min(60, n));
        
        // 中央子午线 = 3 × N
        double centralMeridian = n * 3.0;
        
        // 高斯投影不使用带号
        return new GaussKrugerResult(centralMeridian, "高斯-克吕格 3度带");
    }
}
