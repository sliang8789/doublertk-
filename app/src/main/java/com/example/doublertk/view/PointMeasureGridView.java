package com.example.doublertk.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * 点测量专用网格视图（独立于 GridPreviewView）。
 * 模仿手簿样式：
 * - 轻量级无限网格
 * - 支持缩放、平移
 * - 中心十字准星与坐标轴提示
 * - 主/次网格（每5格加粗）
 * - 上方显示模拟地球网格，显示保存的测量点
 */
public class PointMeasureGridView extends View {

    private static final float MIN_SCALE = 0.4f;
    private static final float MAX_SCALE = 4.0f;

    // 以"米"为单位的单元格尺寸（X=东向、Y=北向）。默认 1m 方格
    private float cellSizeMeterX = 1f;
    private float cellSizeMeterY = 1f;

    // 缩放和平移（像素）
    private float baseCellPx = 80f; // 当 scaleFactor=1 时，1m 对应的像素
    private float scaleFactor = 1f;
    private float translateX = 0f;
    private float translateY = 0f;
    private final ScaleGestureDetector scaleDetector;

    // 画笔
    private final Paint minorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tiledGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 平铺网格着色器（用于"无限网格"效果）
    private android.graphics.BitmapShader gridShader = null;
    private android.graphics.Bitmap gridTile = null;
    private int cachedTileW = -1, cachedTileH = -1;

    // 当前点与原点（世界坐标：北/东，米）
    private boolean hasOrigin = false;
    private float originNorth = 0f, originEast = 0f;
    private boolean hasCurrent = false;
    private float currentNorth = 0f, currentEast = 0f;
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    // 平面网格上的所有测量点
    private final List<PlanePoint> planePoints = new ArrayList<>();
    private final Paint planePointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint originPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 原点画笔
    private final Paint originLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 原点标签

    // 地球网格相关（保留用于兼容性）
    private final Paint earthGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint earthPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint earthLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint earthLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<EarthPoint> earthPoints = new ArrayList<>();
    private float earthGridSize = 0f; // 地球网格大小
    private float earthGridOffsetY = 0f; // 地球网格Y偏移

    // 手势拖拽
    private float lastX = 0f, lastY = 0f;
    private boolean dragging = false;

    // 地球上的测量点数据结构（保留用于兼容性）
    public static class EarthPoint {
        public final String name;        // 点名
        public final double latitude;    // 纬度 B
        public final double longitude;   // 经度 L
        public final double altitude;    // 高程 H

        public EarthPoint(String name, double latitude, double longitude, double altitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
        }
    }
    
    // 平面网格上的测量点数据结构
    public static class PlanePoint {
        public final String name;        // 点名
        public final float north;        // 北向坐标（米）
        public final float east;         // 东向坐标（米）

        public PlanePoint(String name, float north, float east) {
            this.name = name;
            this.north = north;
            this.east = east;
        }
    }

    public PointMeasureGridView(Context context) {
        super(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        initPaints();
    }

    public PointMeasureGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        initPaints();
    }

    public PointMeasureGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        initPaints();
    }

    private void initPaints() {
        setBackgroundColor(0xFFF6F6F6);

        minorPaint.setColor(0xFFDDDDDD);
        minorPaint.setStrokeWidth(1f);

        majorPaint.setColor(0xFFB0B0B0);
        majorPaint.setStrokeWidth(2f);

        axisPaint.setColor(0xFF1976D2); // 蓝色轴线
        axisPaint.setStrokeWidth(2.5f);

        crossPaint.setColor(0xFF1677FF);
        crossPaint.setStrokeWidth(3f);

        labelPaint.setColor(Color.DKGRAY);
        labelPaint.setTextSize(20f);

        pointPaint.setColor(0xFFD32F2F);
        pointPaint.setStyle(Paint.Style.FILL);
        
        // 平面网格测量点画笔
        planePointPaint.setColor(0xFF1677FF);  // 蓝色点
        planePointPaint.setStyle(Paint.Style.FILL);
        
        planeLabelPaint.setColor(0xFF212121);
        planeLabelPaint.setTextSize(14f);
        planeLabelPaint.setTextAlign(Paint.Align.CENTER);
        
        planeLabelBgPaint.setColor(0xE6FFFFFF);
        planeLabelBgPaint.setStyle(Paint.Style.FILL);
        
        // 原点画笔（第一个测量点，作为坐标轴交点）
        originPointPaint.setColor(0xFF4CAF50);  // 绿色点，表示原点
        originPointPaint.setStyle(Paint.Style.FILL);
        
        originLabelPaint.setColor(0xFF4CAF50);
        originLabelPaint.setTextSize(14f);
        originLabelPaint.setTextAlign(Paint.Align.CENTER);
        originLabelPaint.setFakeBoldText(true);  // 加粗原点标签

        // 地球网格画笔（保留用于兼容性）
        earthGridPaint.setColor(0xFF4CAF50);
        earthGridPaint.setStrokeWidth(1.5f);
        earthGridPaint.setStyle(Paint.Style.STROKE);

        // 地球上的点画笔
        earthPointPaint.setColor(0xFFE91E63);
        earthPointPaint.setStyle(Paint.Style.FILL);

        // 地球上的标签画笔
        earthLabelPaint.setColor(0xFF212121);
        earthLabelPaint.setTextSize(16f);
        earthLabelPaint.setTextAlign(Paint.Align.CENTER);

        // 地球标签背景画笔
        earthLabelBgPaint.setColor(0xE6FFFFFF);
        earthLabelBgPaint.setStyle(Paint.Style.FILL);
    }

    /** 设置每格对应的米数（X=东向、Y=北向）。*/
    public void setCellSizeMeters(float meterX, float meterY) {
        if (meterX > 0) cellSizeMeterX = meterX;
        if (meterY > 0) cellSizeMeterY = meterY;
        invalidate();
    }

    /** 设置缩放的基准像素，即当 scale=1 时 1m 对应多少像素。*/
    public void setBaseCellPixels(float pxPerMeter) {
        if (pxPerMeter > 5f) baseCellPx = pxPerMeter;
        invalidate();
    }

    /**
     * 添加平面网格上的测量点
     */
    public void addPlanePoint(String name, float north, float east) {
        planePoints.add(new PlanePoint(name, north, east));
        android.util.Log.d("PointMeasureGrid", String.format(
            "添加平面点: %s (N=%.6f, E=%.6f), 总点数=%d", 
            name, north, east, planePoints.size()
        ));
        invalidate();
    }
    
    /**
     * 清除所有平面网格上的测量点
     */
    public void clearPlanePoints() {
        int oldSize = planePoints.size();
        planePoints.clear();
        android.util.Log.d("PointMeasureGrid", String.format("清除平面点: 删除了%d个点", oldSize));
        invalidate();
    }

    /**
     * 添加地球上的测量点（保留用于兼容性，不再显示）
     */
    public void addEarthPoint(String name, double latitude, double longitude, double altitude) {
        earthPoints.add(new EarthPoint(name, latitude, longitude, altitude));
        invalidate();
    }

    /**
     * 清除所有地球上的测量点
     */
    public void clearEarthPoints() {
        earthPoints.clear();
        planePoints.clear();
        invalidate();
    }

    /**
     * 设置地球网格大小（保留用于兼容性）
     */
    public void setEarthGridSize(float size) {
        this.earthGridSize = size;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 平移到中心 + 用户平移
        canvas.save();
        canvas.translate(w / 2f + translateX, h / 2f + translateY);

        // 计算步长（像素）
        float stepX = baseCellPx * scaleFactor * cellSizeMeterX; // 东向一格像素
        float stepY = baseCellPx * scaleFactor * cellSizeMeterY; // 北向一格像素
        stepX = clamp(stepX, 10f, 600f);
        stepY = clamp(stepY, 10f, 600f);

        // 使用平铺着色器绘制"无限网格"
        ensureGridShader(stepX, stepY);
        if (gridShader != null) {
            android.graphics.Matrix m = new android.graphics.Matrix();
            gridShader.setLocalMatrix(m);
            tiledGridPaint.setShader(gridShader);
            float maxSide = Math.max(w, h);
            float cover = maxSide * 100f + Math.max(stepX, stepY) * 10f;
            canvas.drawRect(-cover, -cover, cover, cover, tiledGridPaint);
        }

        // 轴线（东/X 向右，北/Y 向上）
        canvas.drawLine(-w, 0, w, 0, axisPaint);   // 东向轴（水平）
        canvas.drawLine(0, -h, 0, h, axisPaint);   // 北向轴（垂直）
        
        // 绘制方位标注（上北下南，左西右东）
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("北(N)", 0, -h * 0.45f, labelPaint);      // 上方：北
        canvas.drawText("南(S)", 0, h * 0.45f + 20f, labelPaint); // 下方：南
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("东(E)", w * 0.45f, -6f, labelPaint);     // 右侧：东
        canvas.drawText("西(W)", -w * 0.45f - 50f, -6f, labelPaint); // 左侧：西

        // 中心十字准星
        float cross = 14f;
        canvas.drawLine(-cross, 0, cross, 0, crossPaint);
        canvas.drawLine(0, -cross, 0, cross, crossPaint);

        // 绘制所有平面测量点
        if (hasOrigin && !planePoints.isEmpty()) {
            // 先绘制所有点的圆圈
            for (int i = 0; i < planePoints.size(); i++) {
                PlanePoint point = planePoints.get(i);
                float dNorth = point.north - originNorth;
                float dEast = point.east - originEast;
                float px = (dEast / cellSizeMeterX) * stepX;
                float py = (-dNorth / cellSizeMeterY) * stepY; // 北向为上

                // 判断是否是原点（第一个点）
                boolean isOrigin = (Math.abs(dNorth) < 0.001f && Math.abs(dEast) < 0.001f);
                
                float r = Math.max(5f, Math.min(stepX, stepY) * 0.10f);
                if (isOrigin) {
                    // 原点用更大的绿色圆圈
                    r = r * 1.3f;
                    canvas.drawCircle(px, py, r, originPointPaint);
                } else {
                    // 其他点用蓝色圆圈
                    canvas.drawCircle(px, py, r, planePointPaint);
                }
            }
            
            // 再绘制所有点的标签（避免被圆圈遮挡）
            for (int i = 0; i < planePoints.size(); i++) {
                PlanePoint point = planePoints.get(i);
                float dNorth = point.north - originNorth;
                float dEast = point.east - originEast;
                float px = (dEast / cellSizeMeterX) * stepX;
                float py = (-dNorth / cellSizeMeterY) * stepY;

                boolean isOrigin = (Math.abs(dNorth) < 0.001f && Math.abs(dEast) < 0.001f);
                float r = Math.max(5f, Math.min(stepX, stepY) * 0.10f);
                if (isOrigin) r = r * 1.3f;

                // 检查是否与之前的点重叠，如果重叠则调整标签位置
                float labelOffsetX = 0;
                float labelOffsetY = -r - 8;
                int overlapCount = 0;
                
                for (int j = 0; j < i; j++) {
                    PlanePoint prevPoint = planePoints.get(j);
                    float prevDN = prevPoint.north - originNorth;
                    float prevDE = prevPoint.east - originEast;
                    float prevPx = (prevDE / cellSizeMeterX) * stepX;
                    float prevPy = (-prevDN / cellSizeMeterY) * stepY;
                    
                    // 如果两点距离小于15像素，认为重叠
                    float dist = (float) Math.sqrt((px - prevPx) * (px - prevPx) + (py - prevPy) * (py - prevPy));
                    if (dist < 15f) {
                        overlapCount++;
                        // 根据重叠次数调整标签位置（螺旋排列）
                        float angle = (float) (overlapCount * Math.PI / 3); // 每次旋转60度
                        labelOffsetX = (float) Math.cos(angle) * 30f;
                        labelOffsetY = (float) Math.sin(angle) * 30f - r - 8;
                    }
                }

                // 绘制点名标签
                String label = point.name;
                Paint currentLabelPaint = isOrigin ? originLabelPaint : planeLabelPaint;
                android.graphics.Rect bounds = new android.graphics.Rect();
                currentLabelPaint.getTextBounds(label, 0, label.length(), bounds);

                float labelX = px + labelOffsetX;
                float labelY = py + labelOffsetY;

                // 绘制标签背景
                float padding = 3f;
                float left = labelX - bounds.width() / 2f - padding;
                float right = labelX + bounds.width() / 2f + padding;
                float top = labelY - bounds.height() - padding;
                float bottom = labelY + padding;
                canvas.drawRect(left, top, right, bottom, planeLabelBgPaint);

                // 绘制标签文字（原点用绿色加粗，其他用黑色）
                canvas.drawText(label, labelX, labelY, currentLabelPaint);
                
                // 如果有偏移，绘制连线指向点
                if (overlapCount > 0) {
                    Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    linePaint.setColor(0x80000000);
                    linePaint.setStrokeWidth(1f);
                    canvas.drawLine(px, py, labelX, labelY + bounds.height() / 2f, linePaint);
                }
            }
        }
        
        // 绘制当前测点（相对原点的ΔN/ΔE）
        if (hasOrigin && hasCurrent) {
            float dNorth = currentNorth - originNorth;
            float dEast = currentEast - originEast;
            float px = (dEast / cellSizeMeterX) * stepX;
            float py = (-dNorth / cellSizeMeterY) * stepY; // 北向为上

            float r = Math.max(6f, Math.min(stepX, stepY) * 0.12f);
            canvas.drawCircle(px, py, r, pointPaint);

            // 文本背景
            String info = String.format(java.util.Locale.US, "ΔN=%.3f m  ΔE=%.3f m", dNorth, dEast);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0xE6FFFFFF);
            bg.setStyle(Paint.Style.FILL);

            android.graphics.Rect bounds = new android.graphics.Rect();
            labelPaint.getTextBounds(info, 0, info.length(), bounds);
            float tx = px + r + 8f;
            float ty = py - r - 8f;
            float left = tx - 6f, top = ty - bounds.height() - 6f, right = tx + bounds.width() + 6f, bottom = ty + 6f;
            canvas.drawRect(left, top, right, bottom, bg);
            canvas.drawText(info, tx, ty, labelPaint);
        }

        canvas.restore();
    }


    /**
     * 创建/更新底层网格着色器：以 5 格为一个平铺单元，在单元内绘制
     * 4 条次网格线与 1 条主网格线，重复铺满实现"无限网格"。
     */
    private void ensureGridShader(float stepX, float stepY) {
        int tileW = Math.max(2, Math.round(stepX * 5f));
        int tileH = Math.max(2, Math.round(stepY * 5f));
        if (gridShader != null && tileW == cachedTileW && tileH == cachedTileH) return;
        cachedTileW = tileW;
        cachedTileH = tileH;

        if (gridTile != null) { gridTile.recycle(); gridTile = null; }
        gridTile = android.graphics.Bitmap.createBitmap(tileW, tileH, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(gridTile);
        c.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

        Paint pMinor = new Paint(Paint.ANTI_ALIAS_FLAG);
        pMinor.setColor(minorPaint.getColor());
        pMinor.setStrokeWidth(1f);
        Paint pMajor = new Paint(Paint.ANTI_ALIAS_FLAG);
        pMajor.setColor(majorPaint.getColor());
        pMajor.setStrokeWidth(2f);

        // 竖线：每 stepX 一条，其中边界为主网格线
        for (int i = 0; i <= 5; i++) {
            float x = Math.round(i * stepX);
            c.drawLine(x, 0, x, tileH, (i == 0 || i == 5) ? pMajor : pMinor);
        }
        // 横线：每 stepY 一条，其中边界为主网格线
        for (int j = 0; j <= 4; j++) {
            float y = Math.round(j * stepY);
            c.drawLine(0, y, tileW, y, (j == 0 || j == 4) ? pMajor : pMinor);
        }

        gridShader = new android.graphics.BitmapShader(
                gridTile,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT);
    }

    /** 设置原点（北/东，米），随即以该点为相对零点显示 */
    public void setOriginNE(float north, float east) {
        this.originNorth = north;
        this.originEast = east;
        this.hasOrigin = true;
        invalidate();
    }

    /** 清除原点设置 */
    public void clearOrigin() {
        this.hasOrigin = false;
        invalidate();
    }

    /** 设置当前测点坐标（北/东，米）。若尚未设置原点，先不绘制相对值，只缓存 */
    public void setCurrentPointNE(float north, float east) {
        this.currentNorth = north;
        this.currentEast = east;
        this.hasCurrent = true;
        invalidate();
    }

    /** 是否已有原点 */
    public boolean hasOrigin() { return hasOrigin; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    translateX += dx;
                    translateY += dy;
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            if (scaleFactor < MIN_SCALE) scaleFactor = MIN_SCALE;
            if (scaleFactor > MAX_SCALE) scaleFactor = MAX_SCALE;
            invalidate();
            return true;
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}


