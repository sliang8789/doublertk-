package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 坐标系实体，保存椭球 + 投影参数。
 */
@Entity(tableName = "coordinate_systems",
        indices = {@Index(value = {"name"}, unique = true)})
public class CoordinateSystem {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;            // 自定义名称
    private String ellipsoid;       // CGCS2000 / WGS84 / 北京54 / 西安80
    private String projection;      // GAUSS3 / GAUSS6 / UTM
    private Double centralMeridian; // 高斯投影用，可空
    private Integer zone;           // UTM 用，可空

    // --- 新增多种参数存储 ---
    private String projectionParams;   // 投影模型参数(JSON)
    private String datumType;          // 基准转换类型
    private String datumParams;        // 基准转换参数(JSON)
    private String planeParams;        // 平面校正参数(JSON)
    private String heightParams;       // 高程拟合参数(JSON)
    private String calculationHistory; // 计算历史记录(JSON)

    private long createdAt;         // 时间戳

    // 无参构造函数供 Room 使用
    public CoordinateSystem() {
        this.createdAt = System.currentTimeMillis();
    }

    @androidx.room.Ignore
    public CoordinateSystem(String name, String ellipsoid, String projection,
                            Double centralMeridian, Integer zone,
                            String projectionParams, String datumType, String datumParams,
                            String planeParams, String heightParams) {
        this.name = name;
        this.ellipsoid = ellipsoid;
        this.projection = projection;
        this.centralMeridian = centralMeridian;
        this.zone = zone;
        this.projectionParams = projectionParams;
        this.datumType = datumType;
        this.datumParams = datumParams;
        this.planeParams = planeParams;
        this.heightParams = heightParams;
        this.calculationHistory = null; // 初始为空
        this.createdAt = System.currentTimeMillis();
    }

    // Getter / Setter
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEllipsoid() { return ellipsoid; }
    public void setEllipsoid(String ellipsoid) { this.ellipsoid = ellipsoid; }

    public String getProjection() { return projection; }
    public void setProjection(String projection) { this.projection = projection; }

    public Double getCentralMeridian() { return centralMeridian; }
    public void setCentralMeridian(Double centralMeridian) { this.centralMeridian = centralMeridian; }

    public Integer getZone() { return zone; }
    public void setZone(Integer zone) { this.zone = zone; }

    public String getProjectionParams() { return projectionParams; }
    public void setProjectionParams(String projectionParams) { this.projectionParams = projectionParams; }

    public String getDatumType() { return datumType; }
    public void setDatumType(String datumType) { this.datumType = datumType; }

    public String getDatumParams() { return datumParams; }
    public void setDatumParams(String datumParams) { this.datumParams = datumParams; }

    public String getPlaneParams() { return planeParams; }
    public void setPlaneParams(String planeParams) { this.planeParams = planeParams; }

    public String getHeightParams() { return heightParams; }
    public void setHeightParams(String heightParams) { this.heightParams = heightParams; }

    public String getCalculationHistory() { return calculationHistory; }
    public void setCalculationHistory(String calculationHistory) { this.calculationHistory = calculationHistory; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // 添加缺失的getter方法
    public String getEllipsoidName() { return ellipsoid; }
    public String getProjectionName() { return projection; }

    // 这些方法需要根据实际存储的参数来计算或返回默认值
    public double getSemiMajorAxis() {
        // 根据椭球类型返回对应的长半轴
        switch (ellipsoid) {
            case "CGCS2000":
                return 6378137.0;
            case "WGS84":
                return 6378137.0;
            case "北京54":
                return 6378245.0;
            case "西安80":
                return 6378140.0;
            default:
                return 6378137.0;
        }
    }

    public double getInverseFlattening() {
        // 根据椭球类型返回对应的扁率倒数
        switch (ellipsoid) {
            case "CGCS2000":
                return 298.257222101;
            case "WGS84":
                return 298.257223563;
            case "北京54":
                return 298.3;
            case "西安80":
                return 298.257;
            default:
                return 298.257222101;
        }
    }

    public double getScaleFactor() {
        // 默认比例因子，实际应该从projectionParams中解析
        return 1.0;
    }

    public double getFalseEasting() {
        // 默认东偏移，实际应该从projectionParams中解析
        return 500000.0;
    }

    public double getFalseNorthing() {
        // 默认北偏移，实际应该从projectionParams中解析
        return 0.0;
    }

    public boolean isUtmProjection() {
        return "UTM".equals(projection);
    }

    public int getUtmZone() {
        return zone != null ? zone : 0;
    }

    public String getHemisphere() {
        // 这里需要根据实际存储的数据来判断，暂时返回默认值
        return "N";
    }

    public int getDatumTransform() {
        // 从datumType解析转换类型
        if (datumType == null) return 0;
        switch (datumType) {
            case "SEVEN":
            case "七参数": 
                return 1;
            default: 
                return 0;
        }
    }

    public int getPlaneCorrection() {
        // 从planeParams解析平面校正类型，暂时返回默认值
        if (planeParams == null) return 0;
        // 这里需要根据实际的JSON参数来判断
        return 0;
    }

    public int getHeightFit() {
        // 从heightParams解析高程拟合类型，暂时返回默认值
        if (heightParams == null) return 0;
        // 这里需要根据实际的JSON参数来判断
        return 0;
    }

    public int getEllipsoidType() {
        switch (ellipsoid) {
            case "CGCS2000": return CoordinateSystemManager.ELLIPSOID_CGCS2000;
            case "WGS84": return CoordinateSystemManager.ELLIPSOID_WGS84;
            case "北京54": return CoordinateSystemManager.ELLIPSOID_BEIJING54;
            case "西安80": return CoordinateSystemManager.ELLIPSOID_XIAN80;
            default: return CoordinateSystemManager.ELLIPSOID_CGCS2000;
        }
    }

    public int getProjectionType() {
        if (projection == null) return CoordinateSystemManager.PROJECTION_GAUSS;
        switch (projection) {
            case "GAUSS3":
            case "GAUSS6":
            case "GAUSS":  
                return CoordinateSystemManager.PROJECTION_GAUSS; // 统一使用3度带
            case "UTM":    
                return CoordinateSystemManager.PROJECTION_UTM;
            default:        
                return CoordinateSystemManager.PROJECTION_GAUSS;
        }
    }
}