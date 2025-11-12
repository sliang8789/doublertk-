package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/**
 * 已知点实体，与坐标系建立外键关联。
 * 增加测量点坐标（测得的 B/L/H），以便独立存储。
 * 增加精度字段用于存储计算出的水平精度和高程精度。
 */
@Entity(tableName = "known_points",
        foreignKeys = @ForeignKey(entity = CoordinateSystem.class,
                parentColumns = "id",
                childColumns = "csId",
                onDelete = CASCADE),
        indices = {
                @Index("csId"),
                @Index("name"),
                @Index(name = "index_known_points_csId_name", value = {"csId", "name"}, unique = true)
        })
public class KnownPoint {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long csId;     // 所属坐标系 id
    private String name;   // 点名

    // 已知(投影)坐标
    private Double x;      // N 或 X
    private Double y;      // E 或 Y
    private Double z;      // H

    // 测量坐标（经纬度或投影取决于需求）
    private Double measuredB; // B / 纬度
    private Double measuredL; // L / 经度
    private Double measuredH; // 高程

    // 精度字段
    private Double horizontalAccuracy; // 水平精度 (米)
    private Double elevationAccuracy;  // 高程精度 (米)

    private long createdAt;

    public KnownPoint() {
        this.createdAt = System.currentTimeMillis();
    }

    @androidx.room.Ignore
    public KnownPoint(long csId, String name,
                      Double x, Double y, Double z,
                      Double measuredB, Double measuredL, Double measuredH) {
        this.csId = csId;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.measuredB = measuredB;
        this.measuredL = measuredL;
        this.measuredH = measuredH;
        this.createdAt = System.currentTimeMillis();
    }

    @androidx.room.Ignore
    public KnownPoint(long csId, String name,
                      Double x, Double y, Double z,
                      Double measuredB, Double measuredL, Double measuredH,
                      Double horizontalAccuracy, Double elevationAccuracy) {
        this.csId = csId;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.measuredB = measuredB;
        this.measuredL = measuredL;
        this.measuredH = measuredH;
        this.horizontalAccuracy = horizontalAccuracy;
        this.elevationAccuracy = elevationAccuracy;
        this.createdAt = System.currentTimeMillis();
    }

    // Getter / Setter
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCsId() { return csId; }
    public void setCsId(long csId) { this.csId = csId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }

    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }

    public Double getZ() { return z; }
    public void setZ(Double z) { this.z = z; }

    public Double getMeasuredB() { return measuredB; }
    public void setMeasuredB(Double measuredB) { this.measuredB = measuredB; }

    public Double getMeasuredL() { return measuredL; }
    public void setMeasuredL(Double measuredL) { this.measuredL = measuredL; }

    public Double getMeasuredH() { return measuredH; }
    public void setMeasuredH(Double measuredH) { this.measuredH = measuredH; }

    public Double getHorizontalAccuracy() { return horizontalAccuracy; }
    public void setHorizontalAccuracy(Double horizontalAccuracy) { this.horizontalAccuracy = horizontalAccuracy; }

    public Double getElevationAccuracy() { return elevationAccuracy; }
    public void setElevationAccuracy(Double elevationAccuracy) { this.elevationAccuracy = elevationAccuracy; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
