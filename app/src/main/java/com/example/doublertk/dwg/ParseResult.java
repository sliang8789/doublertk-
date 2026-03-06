package com.example.doublertk.dwg;

import java.util.List;

/**
 * 解析结果类
 */
public class ParseResult {
    private boolean success;
    private String errorMessage;
    private List<LayerInfo> layers;
    private List<EntityInfo> entities;
    
    public ParseResult() {
        this.success = false;
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    

    
    public List<LayerInfo> getLayers() {
        return layers;
    }
    
    public void setLayers(List<LayerInfo> layers) {
        this.layers = layers;
    }
    
    public List<EntityInfo> getEntities() {
        return entities;
    }
    
    public void setEntities(List<EntityInfo> entities) {
        this.entities = entities;
    }
}








