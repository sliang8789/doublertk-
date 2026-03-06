package com.example.doublertk.dwg;

/**
 * 实体信息类
 */
public class EntityInfo {
    private String type;
    private String layerName;
    private String bounds;
    
    public EntityInfo() {}
    
    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getLayerName() {
        return layerName;
    }
    
    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }
    
    public String getBounds() {
        return bounds;
    }
    
    public void setBounds(String bounds) {
        this.bounds = bounds;
    }
    
    @Override
    public String toString() {
        return "EntityInfo{" +
                "type='" + type + '\'' +
                ", layerName='" + layerName + '\'' +
                ", bounds=" + bounds +
                '}';
    }
}








