package com.example.doublertk.dwg;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单的DXF文件解析器
 */
public class SimpleDxfParser {
    private static final String TAG = "SimpleDxfParser";
    
    /**
     * 解析DXF文件内容
     */
    public static ParseResult parse(InputStream inputStream) {
        ParseResult result = new ParseResult();
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder contentBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
            String content = contentBuilder.toString();
            
            if (isDxfFile(content)) {
                result.setSuccess(true);
                result.setLayers(extractLayers(content));
                result.setEntities(extractEntities(content));
                Log.d(TAG, "DXF文件解析成功");
            } else {
                result.setSuccess(false);
                result.setErrorMessage("不是有效的DXF文件");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "读取文件失败", e);
            result.setSuccess(false);
            result.setErrorMessage("读取文件失败: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "解析失败", e);
            result.setSuccess(false);
            result.setErrorMessage("解析失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 检查是否为DXF文件
     */
    private static boolean isDxfFile(String content) {
        return content.contains("SECTION") && 
               (content.contains("ENTITIES") || content.contains("HEADER"));
    }
    
    /**
     * 提取图层信息
     */
    private static List<LayerInfo> extractLayers(String content) {
        List<LayerInfo> layers = new ArrayList<>();
        
        // 查找TABLES部分中的LAYER表
        Pattern tablePattern = Pattern.compile("SECTION\\s*\\r?\\n\\s*2\\s*\\r?\\n\\s*TABLES.*?ENDSEC", Pattern.DOTALL);
        Matcher tableMatcher = tablePattern.matcher(content);
        
        if (tableMatcher.find()) {
            String tablesSection = tableMatcher.group();
            
            // 在TABLES部分查找LAYER条目
            Pattern layerPattern = Pattern.compile("LAYER\\s*\\r?\\n.*?(?=LAYER|ENDTAB)", Pattern.DOTALL);
            Matcher layerMatcher = layerPattern.matcher(tablesSection);
            
            while (layerMatcher.find()) {
                String layerData = layerMatcher.group();
                LayerInfo layer = parseLayerData(layerData);
                if (layer != null) {
                    layers.add(layer);
                }
            }
        }
        
        // 如果没有找到图层，添加默认图层
        if (layers.isEmpty()) {
            LayerInfo defaultLayer = new LayerInfo();
            defaultLayer.setName("0");
            defaultLayer.setColor(7);
            defaultLayer.setLineType("CONTINUOUS");
            defaultLayer.setVisible(true);
            layers.add(defaultLayer);
        }
        
        return layers;
    }
    
    /**
     * 解析单个图层数据
     */
    private static LayerInfo parseLayerData(String layerData) {
        LayerInfo layer = new LayerInfo();
        
        // 提取图层名称 (组码2)
        Pattern namePattern = Pattern.compile("\\s*2\\s*\\r?\\n\\s*([^\\r\\n]+)");
        Matcher nameMatcher = namePattern.matcher(layerData);
        if (nameMatcher.find()) {
            layer.setName(nameMatcher.group(1).trim());
        } else {
            return null;
        }
        
        // 提取颜色 (组码62)
        Pattern colorPattern = Pattern.compile("\\s*62\\s*\\r?\\n\\s*(\\d+)");
        Matcher colorMatcher = colorPattern.matcher(layerData);
        if (colorMatcher.find()) {
            layer.setColor(Integer.parseInt(colorMatcher.group(1)));
        } else {
            layer.setColor(7); // 默认白色
        }
        
        // 提取线型 (组码6)
        Pattern lineTypePattern = Pattern.compile("\\s*6\\s*\\r?\\n\\s*([^\\r\\n]+)");
        Matcher lineTypeMatcher = lineTypePattern.matcher(layerData);
        if (lineTypeMatcher.find()) {
            layer.setLineType(lineTypeMatcher.group(1).trim());
        } else {
            layer.setLineType("CONTINUOUS");
        }
        
        // 检查是否冻结 (组码70)
        Pattern flagPattern = Pattern.compile("\\s*70\\s*\\r?\\n\\s*(\\d+)");
        Matcher flagMatcher = flagPattern.matcher(layerData);
        if (flagMatcher.find()) {
            int flags = Integer.parseInt(flagMatcher.group(1));
            layer.setVisible((flags & 1) == 0); // 如果第0位为1，则图层被冻结
        } else {
            layer.setVisible(true);
        }
        
        return layer;
    }
    
    /**
     * 提取实体信息
     */
    private static List<EntityInfo> extractEntities(String content) {
        List<EntityInfo> entities = new ArrayList<>();
        
        // 查找ENTITIES部分
        Pattern entitiesPattern = Pattern.compile("SECTION\\s*\\r?\\n\\s*2\\s*\\r?\\n\\s*ENTITIES(.*?)ENDSEC", Pattern.DOTALL);
        Matcher entitiesMatcher = entitiesPattern.matcher(content);
        
        if (entitiesMatcher.find()) {
            String entitiesSection = entitiesMatcher.group(1);
            
            // 查找各种实体类型
            String[] entityTypes = {"LINE", "CIRCLE", "ARC", "POLYLINE", "LWPOLYLINE", "TEXT", "MTEXT", "INSERT", "POINT"};
            
            for (String entityType : entityTypes) {
                Pattern entityPattern = Pattern.compile("\\s*0\\s*\\r?\\n\\s*" + entityType + "\\s*\\r?\\n(.*?)(?=\\s*0\\s*\\r?\\n|$)", Pattern.DOTALL);
                Matcher entityMatcher = entityPattern.matcher(entitiesSection);
                
                while (entityMatcher.find()) {
                    String entityData = entityMatcher.group(1);
                    EntityInfo entity = parseEntityData(entityType, entityData);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
            }
        }
        
        return entities;
    }
    
    /**
     * 解析单个实体数据
     */
    private static EntityInfo parseEntityData(String entityType, String entityData) {
        EntityInfo entity = new EntityInfo();
        entity.setType(entityType);
        
        // 提取图层名称 (组码8)
        Pattern layerPattern = Pattern.compile("\\s*8\\s*\\r?\\n\\s*([^\\r\\n]+)");
        Matcher layerMatcher = layerPattern.matcher(entityData);
        if (layerMatcher.find()) {
            entity.setLayerName(layerMatcher.group(1).trim());
        } else {
            entity.setLayerName("0");
        }
        
        // 根据实体类型提取坐标信息
        String bounds = extractBounds(entityType, entityData);
        entity.setBounds(bounds);
        
        return entity;
    }
    
    /**
     * 提取实体边界信息
     */
    private static String extractBounds(String entityType, String entityData) {
        StringBuilder bounds = new StringBuilder();
        
        switch (entityType) {
            case "LINE":
                // 提取起点和终点
                String startX = extractGroupCode(entityData, "10");
                String startY = extractGroupCode(entityData, "20");
                String endX = extractGroupCode(entityData, "11");
                String endY = extractGroupCode(entityData, "21");
                if (startX != null && startY != null && endX != null && endY != null) {
                    bounds.append(String.format("起点:(%.2f,%.2f) 终点:(%.2f,%.2f)", 
                        Double.parseDouble(startX), Double.parseDouble(startY),
                        Double.parseDouble(endX), Double.parseDouble(endY)));
                }
                break;
                
            case "CIRCLE":
                // 提取圆心和半径
                String centerX = extractGroupCode(entityData, "10");
                String centerY = extractGroupCode(entityData, "20");
                String radius = extractGroupCode(entityData, "40");
                if (centerX != null && centerY != null && radius != null) {
                    bounds.append(String.format("圆心:(%.2f,%.2f) 半径:%.2f", 
                        Double.parseDouble(centerX), Double.parseDouble(centerY),
                        Double.parseDouble(radius)));
                }
                break;
                
            case "POINT":
                // 提取点坐标
                String pointX = extractGroupCode(entityData, "10");
                String pointY = extractGroupCode(entityData, "20");
                if (pointX != null && pointY != null) {
                    bounds.append(String.format("坐标:(%.2f,%.2f)", 
                        Double.parseDouble(pointX), Double.parseDouble(pointY)));
                }
                break;
                
            default:
                bounds.append("复杂几何体");
                break;
        }
        
        return bounds.toString();
    }
    
    /**
     * 提取指定组码的值
     */
    private static String extractGroupCode(String data, String groupCode) {
        Pattern pattern = Pattern.compile("\\s*" + groupCode + "\\s*\\r?\\n\\s*([^\\r\\n]+)");
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}

