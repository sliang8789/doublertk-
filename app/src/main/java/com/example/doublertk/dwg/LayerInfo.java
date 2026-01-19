package com.example.doublertk.dwg;

/**
 * 图层信息类
 */
public class LayerInfo {
    private String name;
    private int color;
    private String lineType;
    private boolean visible;
    
    public LayerInfo() {}
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getColor() {
        return color;
    }
    
    public void setColor(int color) {
        this.color = color;
    }
    
    public String getLineType() {
        return lineType;
    }
    
    public void setLineType(String lineType) {
        this.lineType = lineType;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    @Override
    public String toString() {
        return "LayerInfo{" +
                "name='" + name + '\'' +
                ", color=" + color +
                ", lineType='" + lineType + '\'' +
                ", visible=" + visible +
                '}';
    }
}

