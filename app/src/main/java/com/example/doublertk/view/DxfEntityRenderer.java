package com.example.doublertk.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.example.doublertk.dwg.EntityInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DXF实体渲染视图
 * 用于在屏幕上绘制DXF文件中的实体（LINE、CIRCLE、POINT等）
 */
public class DxfEntityRenderer extends View {
    
    private List<EntityInfo> entities = new ArrayList<>();
    private Paint linePaint;
    private Paint circlePaint;
    private Paint pointPaint;
    private Paint textPaint;
    
    // 坐标转换参数（使用double提高精度）
    private double minX = Double.MAX_VALUE;
    private double maxX = Double.MIN_VALUE;
    private double minY = Double.MAX_VALUE;
    private double maxY = Double.MIN_VALUE;
    private double scale = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    
    // 用户交互：缩放和平移
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float userScaleFactor = 1.0f;
    private float userTranslateX = 0;
    private float userTranslateY = 0;
    private float lastFocusX = 0;
    private float lastFocusY = 0;
    
    public DxfEntityRenderer(Context context) {
        super(context);
        init();
    }
    
    public DxfEntityRenderer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        // 设置白色背景
        setBackgroundColor(Color.WHITE);
        
        // 所有实体使用黑色绘制
        linePaint = new Paint();
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(3);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);
        
        circlePaint = new Paint();
        circlePaint.setColor(Color.BLACK);
        circlePaint.setStrokeWidth(3);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setAntiAlias(true);
        
        pointPaint = new Paint();
        pointPaint.setColor(Color.BLACK);
        pointPaint.setStrokeWidth(10);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(32);
        textPaint.setAntiAlias(true);
        
        // 初始化手势检测器
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
    }
    
    /**
     * 设置要渲染的实体列表
     */
    public void setEntities(List<EntityInfo> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
        calculateBounds();
        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = scaleDetector.onTouchEvent(event);
        handled = gestureDetector.onTouchEvent(event) || handled;
        return handled || super.onTouchEvent(event);
    }
    
    /**
     * 计算所有实体的边界，用于缩放和平移
     */
    private void calculateBounds() {
        minX = Double.MAX_VALUE;
        maxX = Double.MIN_VALUE;
        minY = Double.MAX_VALUE;
        maxY = Double.MIN_VALUE;
        
        // 首次遍历：找出坐标的大致范围
        List<Double> allX = new ArrayList<>();
        List<Double> allY = new ArrayList<>();
        
        for (EntityInfo entity : entities) {
            if (entity == null || entity.getBounds() == null) continue;
            
            String type = entity.getType();
            String bounds = entity.getBounds();
            
            if ("LINE".equals(type)) {
                double[] coords = parseLineCoords(bounds);
                if (coords != null) {
                    allX.add(coords[0]);
                    allX.add(coords[2]);
                    allY.add(coords[1]);
                    allY.add(coords[3]);
                }
            } else if ("CIRCLE".equals(type)) {
                double[] coords = parseCircleCoords(bounds);
                if (coords != null) {
                    allX.add(coords[0]);
                    allY.add(coords[1]);
                }
            } else if ("POINT".equals(type)) {
                double[] coords = parsePointCoords(bounds);
                if (coords != null) {
                    allX.add(coords[0]);
                    allY.add(coords[1]);
                }
            } else if ("LWPOLYLINE".equals(type) || "POLYLINE".equals(type)) {
                if (bounds.startsWith("顶点:")) {
                    String verticesStr = bounds.substring(3);
                    // 去掉闭合标记
                    if (verticesStr.endsWith("|CLOSED")) {
                        verticesStr = verticesStr.substring(0, verticesStr.length() - 7);
                    }
                    String[] vertices = verticesStr.split(";");
                    for (String vertex : vertices) {
                        double[] coords = parseVertex(vertex);
                        if (coords != null) {
                            allX.add(coords[0]);
                            allY.add(coords[1]);
                        }
                    }
                }
            } else if ("TEXT".equals(type) || "MTEXT".equals(type)) {
                try {
                    Pattern pattern = Pattern.compile("文本:\\(([-\\d.]+),([-\\d.]+)\\)");
                    Matcher matcher = pattern.matcher(bounds);
                    if (matcher.find()) {
                        allX.add(Double.parseDouble(matcher.group(1)));
                        allY.add(Double.parseDouble(matcher.group(2)));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
        
        // 过滤异常值：找出中位数范围
        if (!allX.isEmpty() && !allY.isEmpty()) {
            Collections.sort(allX);
            Collections.sort(allY);
            
            // 使用中间90%的数据，去掉极端值
            int startIdx = (int)(allX.size() * 0.05);
            int endIdx = (int)(allX.size() * 0.95);
            
            for (int i = startIdx; i < endIdx && i < allX.size(); i++) {
                double x = allX.get(i);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
            }
            
            for (int i = startIdx; i < endIdx && i < allY.size(); i++) {
                double y = allY.get(i);
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        
        // 如果没有有效的边界，使用默认值
        if (minX == Float.MAX_VALUE) {
            minX = 0;
            maxX = 100;
            minY = 0;
            maxY = 100;
        }
        
        android.util.Log.d("DxfRenderer", String.format("Filtered bounds: X[%.2f, %.2f], Y[%.2f, %.2f]", minX, maxX, minY, maxY));
    }
    
    private void updateBounds(double x, double y) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (entities.isEmpty()) {
            // 绘制提示文字
            canvas.drawText("暂无实体数据", getWidth() / 2 - 100, getHeight() / 2, textPaint);
            return;
        }
        
        // 计算基础缩放比例
        calculateTransform();
        
        // 保存画布状态
        canvas.save();
        
        // 应用用户的缩放和平移
        canvas.translate(userTranslateX, userTranslateY);
        canvas.scale(userScaleFactor, userScaleFactor, getWidth() / 2f, getHeight() / 2f);
        
        // 绘制所有实体
        for (EntityInfo entity : entities) {
            if (entity == null) continue;
            drawEntity(canvas, entity);
        }
        
        // 恢复画布状态
        canvas.restore();
        
        // 绘制统计信息（不受缩放影响）
        drawStats(canvas);
    }
    
    /**
     * 计算坐标转换参数
     */
    private void calculateTransform() {
        double width = getWidth();
        double height = getHeight();
        
        if (width == 0 || height == 0) return;
        
        double dataWidth = maxX - minX;
        double dataHeight = maxY - minY;
        
        if (dataWidth == 0 || dataHeight == 0) return;
        
        // 计算缩放比例，保持宽高比
        double scaleX = width * 0.9 / dataWidth;
        double scaleY = height * 0.9 / dataHeight;
        scale = Math.min(scaleX, scaleY);
        
        // 计算偏移量，使图形居中
        // 使用相对坐标系统，先减去最小值
        offsetX = (width - dataWidth * scale) / 2;
        offsetY = (height - dataHeight * scale) / 2;
    }
    
    /**
     * 将DXF坐标转换为屏幕X坐标
     * 先转换为相对坐标（减去minX），再缩放和偏移
     */
    private float transformX(double x) {
        return (float)((x - minX) * scale + offsetX);
    }
    
    /**
     * 将DXF坐标转换为屏幕Y坐标（Y轴翻转）
     * 先转换为相对坐标（减去minY），再缩放和翻转
     */
    private float transformY(double y) {
        return (float)(getHeight() - ((y - minY) * scale + offsetY));
    }
    
    /**
     * 绘制单个实体
     */
    private void drawEntity(Canvas canvas, EntityInfo entity) {
        String type = entity.getType();
        String bounds = entity.getBounds();
        
        android.util.Log.d("DxfRenderer", "Drawing entity - type: " + type + ", bounds: " + bounds);
        
        if (bounds == null || bounds.isEmpty()) {
            android.util.Log.w("DxfRenderer", "Entity has no bounds: " + type);
            return;
        }
        
        switch (type) {
            case "LINE":
                drawLine(canvas, bounds);
                break;
            case "CIRCLE":
                drawCircle(canvas, bounds);
                break;
            case "POINT":
                drawPoint(canvas, bounds);
                break;
            case "ARC":
                drawArc(canvas, bounds);
                break;
            case "LWPOLYLINE":
            case "POLYLINE":
                drawPolyline(canvas, bounds);
                break;
            case "TEXT":
            case "MTEXT":
                drawText(canvas, bounds);
                break;
            default:
                android.util.Log.w("DxfRenderer", "Unknown entity type: " + type);
                break;
        }
    }
    
    /**
     * 绘制直线
     */
    private void drawLine(Canvas canvas, String bounds) {
        double[] coords = parseLineCoords(bounds);
        if (coords == null) {
            android.util.Log.w("DxfRenderer", "Failed to parse line coords: " + bounds);
            return;
        }
        
        float x1 = transformX(coords[0]);
        float y1 = transformY(coords[1]);
        float x2 = transformX(coords[2]);
        float y2 = transformY(coords[3]);
        
        canvas.drawLine(x1, y1, x2, y2, linePaint);
    }
    
    /**
     * 绘制圆
     */
    private void drawCircle(Canvas canvas, String bounds) {
        double[] coords = parseCircleCoords(bounds);
        if (coords == null) return;
        
        float cx = transformX(coords[0]);
        float cy = transformY(coords[1]);
        float r = (float)(coords[2] * scale);
        
        canvas.drawCircle(cx, cy, r, circlePaint);
    }
    
    /**
     * 绘制点
     */
    private void drawPoint(Canvas canvas, String bounds) {
        double[] coords = parsePointCoords(bounds);
        if (coords == null) return;
        
        float x = transformX(coords[0]);
        float y = transformY(coords[1]);
        
        canvas.drawCircle(x, y, 4, pointPaint);
    }
    
    /**
     * 绘制圆弧（简化实现）
     */
    private void drawArc(Canvas canvas, String bounds) {
        // TODO: 实现圆弧绘制
    }
    
    /**
     * 绘制多段线
     * 格式: "顶点:(x1,y1);(x2,y2);(x3,y3)..." 或 "顶点:(x1,y1);(x2,y2)|CLOSED"
     */
    private void drawPolyline(Canvas canvas, String bounds) {
        if (!bounds.startsWith("顶点:")) {
            android.util.Log.w("DxfRenderer", "Invalid polyline bounds: " + bounds);
            return;
        }
        
        String verticesStr = bounds.substring(3); // 去掉"顶点:"
        
        // 检查是否闭合
        boolean isClosed = verticesStr.endsWith("|CLOSED");
        if (isClosed) {
            verticesStr = verticesStr.substring(0, verticesStr.length() - 7); // 去掉"|CLOSED"
        }
        
        String[] vertices = verticesStr.split(";");
        
        if (vertices.length < 2) {
            android.util.Log.w("DxfRenderer", "Polyline has less than 2 vertices");
            return;
        }
        
        // 解析第一个顶点
        double[] firstCoords = parseVertex(vertices[0]);
        if (firstCoords == null) return;
        
        float firstX = transformX(firstCoords[0]);
        float firstY = transformY(firstCoords[1]);
        float prevX = firstX;
        float prevY = firstY;
        
        // 绘制连接线
        for (int i = 1; i < vertices.length; i++) {
            double[] coords = parseVertex(vertices[i]);
            if (coords == null) continue;
            
            float x = transformX(coords[0]);
            float y = transformY(coords[1]);
            
            canvas.drawLine(prevX, prevY, x, y, linePaint);
            
            prevX = x;
            prevY = y;
        }
        
        // 如果是闭合多段线，连接最后一个顶点到第一个顶点
        if (isClosed) {
            canvas.drawLine(prevX, prevY, firstX, firstY, linePaint);
        }
        
        android.util.Log.d("DxfRenderer", "Drew polyline with " + vertices.length + " vertices (closed=" + isClosed + ")");
    }
    
    /**
     * 绘制文本
     * 格式: "文本:(x,y) "内容""
     */
    private void drawText(Canvas canvas, String bounds) {
        try {
            Pattern pattern = Pattern.compile("文本:\\(([-\\d.]+),([-\\d.]+)\\)(?:\\s*\"(.*)\")?");
            Matcher matcher = pattern.matcher(bounds);
            if (matcher.find()) {
                double x = Double.parseDouble(matcher.group(1));
                double y = Double.parseDouble(matcher.group(2));
                String text = matcher.groupCount() >= 3 ? matcher.group(3) : "";
                
                float screenX = transformX(x);
                float screenY = transformY(y);
                
                if (text != null && !text.isEmpty()) {
                    canvas.drawText(text, screenX, screenY, textPaint);
                } else {
                    // 如果没有文本内容，绘制一个小标记
                    canvas.drawCircle(screenX, screenY, 3, pointPaint);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("DxfRenderer", "Failed to parse text: " + bounds);
        }
    }
    
    /**
     * 解析顶点坐标
     * 格式: "(x,y)"
     */
    private double[] parseVertex(String vertex) {
        try {
            Pattern pattern = Pattern.compile("\\(([-\\d.]+),([-\\d.]+)\\)");
            Matcher matcher = pattern.matcher(vertex);
            if (matcher.find()) {
                return new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2))
                };
            }
        } catch (Exception e) {
            android.util.Log.w("DxfRenderer", "Failed to parse vertex: " + vertex);
        }
        return null;
    }
    
    /**
     * 绘制统计信息
     */
    private void drawStats(Canvas canvas) {
        int lineCount = 0;
        int circleCount = 0;
        int pointCount = 0;
        int otherCount = 0;
        
        for (EntityInfo entity : entities) {
            if (entity == null) continue;
            String type = entity.getType();
            if ("LINE".equals(type)) lineCount++;
            else if ("CIRCLE".equals(type)) circleCount++;
            else if ("POINT".equals(type)) pointCount++;
            else otherCount++;
        }
        
        Paint statPaint = new Paint();
        statPaint.setColor(Color.BLACK);
        statPaint.setTextSize(32);
        statPaint.setAntiAlias(true);
        statPaint.setStyle(Paint.Style.FILL);
        
        String stats = String.format("实体总数: %d (线:%d 圆:%d 点:%d 其他:%d)", 
            entities.size(), lineCount, circleCount, pointCount, otherCount);
        canvas.drawText(stats, 20, 50, statPaint);
    }
    
    /**
     * 解析直线坐标
     * 格式: "起点:(x1,y1) 终点:(x2,y2)"
     */
    private double[] parseLineCoords(String bounds) {
        try {
            Pattern pattern = Pattern.compile("起点:\\(([-\\d.]+),([-\\d.]+)\\)\\s*终点:\\(([-\\d.]+),([-\\d.]+)\\)");
            Matcher matcher = pattern.matcher(bounds);
            if (matcher.find()) {
                return new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)),
                    Double.parseDouble(matcher.group(4))
                };
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }
    
    /**
     * 解析圆坐标
     * 格式: "圆心:(x,y) 半径:r"
     */
    private double[] parseCircleCoords(String bounds) {
        try {
            Pattern pattern = Pattern.compile("圆心:\\(([-\\d.]+),([-\\d.]+)\\)\\s*半径:([-\\d.]+)");
            Matcher matcher = pattern.matcher(bounds);
            if (matcher.find()) {
                return new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3))
                };
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }
    
    /**
     * 解析点坐标
     * 格式: "坐标:(x,y)"
     */
    private double[] parsePointCoords(String bounds) {
        try {
            Pattern pattern = Pattern.compile("坐标:\\(([-\\d.]+),([-\\d.]+)\\)");
            Matcher matcher = pattern.matcher(bounds);
            if (matcher.find()) {
                return new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2))
                };
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }
    
    /**
     * 缩放手势监听器
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            userScaleFactor *= detector.getScaleFactor();
            // 限制缩放范围
            userScaleFactor = Math.max(0.1f, Math.min(userScaleFactor, 10.0f));
            
            // 记录焦点位置
            lastFocusX = detector.getFocusX();
            lastFocusY = detector.getFocusY();
            
            invalidate();
            return true;
        }
    }
    
    /**
     * 拖动手势监听器
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
        
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            userTranslateX -= distanceX;
            userTranslateY -= distanceY;
            invalidate();
            return true;
        }
        
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // 双击重置缩放
            userScaleFactor = 1.0f;
            userTranslateX = 0;
            userTranslateY = 0;
            invalidate();
            return true;
        }
    }
}
