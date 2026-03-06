package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 船舶信息实体类
 * 存储船舶的基本信息和天线配置
 */
@Entity(tableName = "ship_info")
public class ShipInfo {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;                    // 船舶名称
    private String shipId;                  // 船舶编号
    private double length;                  // 船长 (米)
    private double width;                   // 船宽 (米)
    private double draft;                   // 吃水深度 (米)
    private double displacement;            // 排水量 (吨)
    
    // 天线配置
    private double antennaBaseline;         // 天线基线长度 (米)
    private double bowAntennaOffsetX;       // 船头天线X偏移 (米, 相对船舶中心)
    private double bowAntennaOffsetY;       // 船头天线Y偏移 (米, 相对船舶中心)
    private double sternAntennaOffsetX;     // 船尾天线X偏移 (米)
    private double sternAntennaOffsetY;     // 船尾天线Y偏移 (米)
    private double antennaHeight;           // 天线高度 (米, 相对水面)
    
    // 航向校正
    private double headingOffset;           // 航向偏差校正 (度)
    
    // 设备连接信息
    private String bowRtkHost;              // 船头RTK设备地址
    private int bowRtkPort;                 // 船头RTK设备端口
    private String sternRtkHost;            // 船尾RTK设备地址
    private int sternRtkPort;               // 船尾RTK设备端口
    
    // 状态
    private boolean isDefault;              // 是否为默认船舶
    private long createTime;                // 创建时间
    private long updateTime;                // 更新时间
    
    public ShipInfo() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
        this.antennaBaseline = 10.0;
        this.bowRtkPort = 9001;
        this.sternRtkPort = 9002;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getShipId() { return shipId; }
    public void setShipId(String shipId) { this.shipId = shipId; }
    
    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }
    
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    
    public double getDraft() { return draft; }
    public void setDraft(double draft) { this.draft = draft; }
    
    public double getDisplacement() { return displacement; }
    public void setDisplacement(double displacement) { this.displacement = displacement; }
    
    public double getAntennaBaseline() { return antennaBaseline; }
    public void setAntennaBaseline(double antennaBaseline) { this.antennaBaseline = antennaBaseline; }
    
    public double getBowAntennaOffsetX() { return bowAntennaOffsetX; }
    public void setBowAntennaOffsetX(double bowAntennaOffsetX) { this.bowAntennaOffsetX = bowAntennaOffsetX; }
    
    public double getBowAntennaOffsetY() { return bowAntennaOffsetY; }
    public void setBowAntennaOffsetY(double bowAntennaOffsetY) { this.bowAntennaOffsetY = bowAntennaOffsetY; }
    
    public double getSternAntennaOffsetX() { return sternAntennaOffsetX; }
    public void setSternAntennaOffsetX(double sternAntennaOffsetX) { this.sternAntennaOffsetX = sternAntennaOffsetX; }
    
    public double getSternAntennaOffsetY() { return sternAntennaOffsetY; }
    public void setSternAntennaOffsetY(double sternAntennaOffsetY) { this.sternAntennaOffsetY = sternAntennaOffsetY; }
    
    public double getAntennaHeight() { return antennaHeight; }
    public void setAntennaHeight(double antennaHeight) { this.antennaHeight = antennaHeight; }
    
    public double getHeadingOffset() { return headingOffset; }
    public void setHeadingOffset(double headingOffset) { this.headingOffset = headingOffset; }
    
    public String getBowRtkHost() { return bowRtkHost; }
    public void setBowRtkHost(String bowRtkHost) { this.bowRtkHost = bowRtkHost; }
    
    public int getBowRtkPort() { return bowRtkPort; }
    public void setBowRtkPort(int bowRtkPort) { this.bowRtkPort = bowRtkPort; }
    
    public String getSternRtkHost() { return sternRtkHost; }
    public void setSternRtkHost(String sternRtkHost) { this.sternRtkHost = sternRtkHost; }
    
    public int getSternRtkPort() { return sternRtkPort; }
    public void setSternRtkPort(int sternRtkPort) { this.sternRtkPort = sternRtkPort; }
    
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    
    public long getUpdateTime() { return updateTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }
}
