package com.example.doublertk.dwg;

import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DWG/DXF文件解析器
 * 说明：
 * - 本项目当前未引入 Kabeja 相关 jar，因此不能直接引用 org.kabeja.*（否则编译失败）。
 * - 这里提供一个“无外部依赖”的轻量实现：解析图层/实体（非常简化），并从 DXF 文本中提取 LINE 覆盖层。
 */
public class DwgDxfParser {
    private static final String TAG = "DwgDxfParser";

    /**
     * 解析 DXF 文件（无外部依赖版本）
     */
    public static ParseResult parseDxfFile(String filePath) {
        try (InputStream in = new FileInputStream(filePath)) {
            return parseDxfStream(in);
        } catch (Exception e) {
            ParseResult error = new ParseResult();
            error.setSuccess(false);
            error.setErrorMessage("解析失败: " + e.getMessage());
            Log.e(TAG, "DXF文件解析失败", e);
            return error;
        }
    }

    /**
     * 解析输入流（无外部依赖版本）
     */
    public static ParseResult parseDxfStream(InputStream inputStream) {
        ParseResult result = new ParseResult();

        try {
            String content = readStreamContent(inputStream);
            if (content == null || content.isEmpty() || !content.contains("SECTION")) {
                result.setSuccess(false);
                result.setErrorMessage("不是有效的DXF文件或文件为空");
                return result;
            }

            // 结果返回（简化：从文本粗略提取）
            result.setSuccess(true);
            result.setLayers(parseLayers(content));
            result.setEntities(parseEntities(content));

            Log.d(TAG, "DXF(轻量)解析成功, 图层:" +
                    (result.getLayers() == null ? 0 : result.getLayers().size()) +
                    " 实体:" +
                    (result.getEntities() == null ? 0 : result.getEntities().size()));

        } catch (Exception ex) {
            Log.e(TAG, "DXF解析失败", ex);
            result.setSuccess(false);
            result.setErrorMessage("解析失败: " + ex.getMessage());
        }

        return result;
    }

    /**
     * 从 DXF 文本提取网格覆盖层（点与连线）。
     *
     * - 坐标系：x→north, y→east（与旧逻辑保持一致，不做转换）
     * - 当前实现主要支持 LINE 实体：每条线产生2个点+1条连线
     */
    public static DxfOverlayResult parseDxfToOverlay(InputStream inputStream) throws Exception {
        String content = readStreamContent(inputStream);
        return parseOverlayFromDxfText(content);
    }

    private static DxfOverlayResult parseOverlayFromDxfText(String content) {
        List<float[]> pts = new ArrayList<>();
        List<int[]> lks = new ArrayList<>();
        if (content == null || content.isEmpty()) return new DxfOverlayResult(pts, lks);

        // 抓取 ENTITIES 区段，减少误匹配
        String entitiesSection = content;
        Matcher sec = Pattern.compile("SECTION\\s*\\r?\\n\\s*2\\s*\\r?\\n\\s*ENTITIES(.*?)ENDSEC", Pattern.DOTALL)
                .matcher(content);
        if (sec.find()) {
            entitiesSection = sec.group(1);
        }

        // LINE 实体：组码 10/20 (start x/y), 11/21 (end x/y)
        Pattern linePat = Pattern.compile("\\s*0\\s*\\r?\\n\\s*LINE\\s*\\r?\\n(.*?)(?=\\s*0\\s*\\r?\\n|$)", Pattern.DOTALL);
        Matcher m = linePat.matcher(entitiesSection);
        while (m.find()) {
            String entityData = m.group(1);
            Double x1 = extractDoubleGroup(entityData, "10");
            Double y1 = extractDoubleGroup(entityData, "20");
            Double x2 = extractDoubleGroup(entityData, "11");
            Double y2 = extractDoubleGroup(entityData, "21");
            if (x1 == null || y1 == null || x2 == null || y2 == null) continue;

            int a = pts.size();
            pts.add(new float[]{x1.floatValue(), y1.floatValue(), 0f});
            int b = pts.size();
            pts.add(new float[]{x2.floatValue(), y2.floatValue(), 0f});
            lks.add(new int[]{a, b});
        }

        return new DxfOverlayResult(pts, lks);
    }

    private static Double extractDoubleGroup(String data, String groupCode) {
        if (data == null) return null;
        Pattern p = Pattern.compile("\\s*" + Pattern.quote(groupCode) + "\\s*\\r?\\n\\s*([-+]?\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(data);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group(1));
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 读取输入流内容
     */
    private static String readStreamContent(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            content.append(new String(buffer, 0, bytesRead));
        }

        return content.toString();
    }

    /**
     * 解析图层信息
     */
    private static List<LayerInfo> parseLayers(String content) {
        List<LayerInfo> layers = new ArrayList<>();

        // 简化的图层解析逻辑
        if (content.contains("LAYER")) {
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().equals("LAYER")) {
                    LayerInfo layerInfo = new LayerInfo();
                    // 查找图层名称
                    for (int j = i + 1; j < lines.length && j < i + 10; j++) {
                        if (lines[j].trim().equals("2") && j + 1 < lines.length) {
                            layerInfo.setName(lines[j + 1].trim());
                            break;
                        }
                    }
                    if (layerInfo.getName() == null) {
                        layerInfo.setName("Layer_" + layers.size());
                    }
                    layerInfo.setColor(7); // 默认白色
                    layerInfo.setLineType("CONTINUOUS");
                    layerInfo.setVisible(true);
                    layers.add(layerInfo);
                }
            }
        }

        return layers;
    }

    /**
     * 解析实体信息
     */
    private static List<EntityInfo> parseEntities(String content) {
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

    /**
     * 创建示例图层
     */
    private static List<LayerInfo> createSampleLayers() {
        List<LayerInfo> layers = new ArrayList<>();

        LayerInfo layer1 = new LayerInfo();
        layer1.setName("0");
        layer1.setColor(7);
        layer1.setLineType("CONTINUOUS");
        layer1.setVisible(true);
        layers.add(layer1);

        LayerInfo layer2 = new LayerInfo();
        layer2.setName("DIMENSIONS");
        layer2.setColor(1);
        layer2.setLineType("CONTINUOUS");
        layer2.setVisible(true);
        layers.add(layer2);

        return layers;
    }

    /**
     * 创建示例实体
     */
    private static List<EntityInfo> createSampleEntities() {
        List<EntityInfo> entities = new ArrayList<>();

        EntityInfo entity1 = new EntityInfo();
        entity1.setType("LINE");
        entity1.setLayerName("0");
        entities.add(entity1);

        EntityInfo entity2 = new EntityInfo();
        entity2.setType("CIRCLE");
        entity2.setLayerName("0");
        entities.add(entity2);

        return entities;
    }

    /**
     * 检查文件是否为支持的格式
     */
    public static boolean isSupportedFile(String fileName) {
        if (fileName == null) return false;

        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".dxf") || lowerName.endsWith(".dwg");
    }
}

