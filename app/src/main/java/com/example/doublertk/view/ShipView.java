package com.example.doublertk.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 船舶视图 - 在右侧区域绘制船舶模型
 * 船体轮廓：顶部稍尖（船头），中间细长矩形主体，底部V形尖点（船尾）
 */
public class ShipView extends View {

    private Paint shipPaint;
    private Paint outlinePaint;
    private Paint rtkPaint;  // RTK点画笔
    private Paint stakePaint;  // 桩子画笔

    private Paint trackPaint;
    private Paint targetPaint;
    private Paint headingPaint;
    private Paint compassPaint;

    private Paint selectedStakePaint;
    private Paint guidancePaint;

    private Paint reefPaint;
    private Paint reefBorderPaint;
    private Paint boundaryWarnPaint;
    
    // 船舶位置偏移量（相对于中心）
    private float offsetX = 0f;
    private float offsetY = 0f;

    private float headingDeg = 0f;

    private PointF targetPoint;
    private final List<PointF> trackPoints = new ArrayList<>();
    private int maxTrackPoints = 300;
    
    // 缓存的船舶尺寸（用于边界检测）
    private float cachedShipWidth = 0f;
    private float cachedShipHeight = 0f;
    
    // 移动步长
    private static final float MOVE_STEP = 10f;
    
    // 边界安全边距（船舶距离边界的最小距离）
    private static final float BOUNDARY_MARGIN = 10f;  // 上下边界安全距离
    private static final float BOUNDARY_MARGIN_HORIZONTAL = 5f;  // 左右边界安全距离（更小）

    private static final float WORLD_UNITS_PER_METER = 10f;
    private static final float REEF_BAND_M = 6f;
    private static final float POOL_EXTEND_M = 25f;
    private static final float BOUNDARY_WARN_M = 8f;
    private static final float BOUNDARY_CLEAR_M = 10f;

    private boolean boundaryAlarmActive = false;
    private float lastMinDistToBoundaryM = Float.POSITIVE_INFINITY;
    
    // RTK和桩子大小
    private static final float RTK_RADIUS = 6f;  // RTK点半径
    private static final float STAKE_RADIUS = 12f;  // 桩子半径
    
    // RTK距离船头和船尾的偏移比例（相对于船长）
    private static final float RTK_OFFSET_RATIO = 0.08f;  // 距离端点8%的船长
    
    // 桩子对数量
    private static final int STAKE_PAIR_COUNT = 90;  // 随机生成25对桩点
    
    // 桩子位置（在空白区域，左上角和右上角附近）- 保留原有的一对
    private float stake1X = 0f;
    private float stake1Y = 0f;
    private float stake2X = 0f;
    private float stake2Y = 0f;

    private static final int SELECTED_NONE = -2;
    private static final int SELECTED_FIXED_PAIR = -1;
    private int selectedStakePairIndex = SELECTED_NONE;
    
    // 随机桩点对列表（每个对包含两个点：[北侧X, 北侧Y, 南侧X, 南侧Y]）
    private List<float[]> stakePairs = new ArrayList<>();
    private Random random = new Random();
    
    // 缩放管理器（控制桩子、船舶、RTK整体缩放）
    private final ScaleManager scaleManager = new ScaleManager();

    // 双指缩放手势检测器
    private ScaleGestureDetector scaleGestureDetector;

    private boolean isScaling = false;
    
    // 标记桩点是否已生成（确保只生成一次）
    private boolean stakesGenerated = false;

    // 画布平移（相机），用于在船舶触边时保持船舶一直在可视区域内
    private float canvasOffsetX = 0f;
    private float canvasOffsetY = 0f;

    public ShipView(Context context) {
        super(context);
        init(context);
    }

    public ShipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ShipView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // View尺寸改变时，重置生成标记，下次绘制时重新生成固定桩点
        stakesGenerated = false;
        stakePairs.clear();
    }

    private void init(Context context) {
        // 船体填充画笔（半透明蓝色）
        shipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shipPaint.setColor(0x804A90E2); // 蓝色船体，50%透明度
        shipPaint.setStyle(Paint.Style.FILL);

        // 船体轮廓画笔
        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setColor(0xFF2C5F8D); // 深蓝色轮廓
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(3f);
        
        // RTK点画笔（红色点）
        rtkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rtkPaint.setColor(0xFFFF0000); // 红色RTK点
        rtkPaint.setStyle(Paint.Style.FILL);
        
        // 桩子画笔（绿色圆圈）
        stakePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stakePaint.setColor(0xFF00AA00); // 绿色桩子
        stakePaint.setStyle(Paint.Style.STROKE);
        stakePaint.setStrokeWidth(3f);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(0xFF4A90E2);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(4f);

        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setColor(0xFFFF8C00);
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(4f);

        headingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headingPaint.setColor(0xFF2C5F8D);
        headingPaint.setStyle(Paint.Style.STROKE);
        headingPaint.setStrokeWidth(5f);

        compassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        compassPaint.setColor(0xCC000000);
        compassPaint.setStyle(Paint.Style.STROKE);
        compassPaint.setStrokeWidth(3f);

        selectedStakePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedStakePaint.setColor(0xFFFFC107);
        selectedStakePaint.setStyle(Paint.Style.STROKE);
        selectedStakePaint.setStrokeWidth(6f);

        guidancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidancePaint.setColor(0xFFFF8C00);
        guidancePaint.setStyle(Paint.Style.STROKE);
        guidancePaint.setStrokeWidth(4f);
        guidancePaint.setPathEffect(new DashPathEffect(new float[]{16f, 10f}, 0f));

        reefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reefPaint.setColor(0x33D32F2F);
        reefPaint.setStyle(Paint.Style.FILL);

        reefBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reefBorderPaint.setColor(0xFFD32F2F);
        reefBorderPaint.setStyle(Paint.Style.STROKE);
        reefBorderPaint.setStrokeWidth(4f);

        boundaryWarnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boundaryWarnPaint.setColor(0xFFFFA000);
        boundaryWarnPaint.setStyle(Paint.Style.STROKE);
        boundaryWarnPaint.setStrokeWidth(3f);
        boundaryWarnPaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0f));

        // 初始化双指缩放手势检测器
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (factor <= 0f || Float.isNaN(factor) || Float.isInfinite(factor)) {
                    return false;
                }
                scaleManager.applyScaleFactor(factor);
                invalidate();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                isScaling = false;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (!isScaling) {
                handleTapSelectStakePair(event.getX(), event.getY());
            }
        }

        return true;
    }

    private void handleTapSelectStakePair(float screenX, float screenY) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        // 将屏幕坐标转换为世界坐标（考虑画布平移 + 缩放）
        float scaleFactor = scaleManager.getCurrentScale();
        float halfW = width * 0.5f;
        float halfH = height * 0.5f;

        // Stakes are drawn after translate(canvasOffset) and scale(scaleFactor) about center.
        float worldX = (screenX - canvasOffsetX - halfW) / scaleFactor + halfW;
        float worldY = (screenY - canvasOffsetY - halfH) / scaleFactor + halfH;

        float thresholdWorld = 28f / scaleFactor;
        float bestDist = Float.MAX_VALUE;
        int bestIndex = SELECTED_NONE;

        // Fixed pair
        float d1 = distance(worldX, worldY, stake1X, stake1Y);
        float d2 = distance(worldX, worldY, stake2X, stake2Y);
        float bestFixed = Math.min(d1, d2);
        if (bestFixed < bestDist) {
            bestDist = bestFixed;
            bestIndex = SELECTED_FIXED_PAIR;
        }

        // Random pairs
        for (int i = 0; i < stakePairs.size(); i++) {
            float[] pair = stakePairs.get(i);
            if (pair == null || pair.length < 4) continue;
            float dn = distance(worldX, worldY, pair[0], pair[1]);
            float ds = distance(worldX, worldY, pair[2], pair[3]);
            float d = Math.min(dn, ds);
            if (d < bestDist) {
                bestDist = d;
                bestIndex = i;
            }
        }

        if (bestDist <= thresholdWorld) {
            selectedStakePairIndex = bestIndex;
            invalidate();
        }
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        float scaleFactor = scaleManager.getCurrentScale();

        // 船舶占整个View的28%（缩小比例），再乘以缩放因子
        float shipWidth = width * 0.28f;
        float shipHeight = height * 0.28f;

        // 计算船舶中心位置（整个View的正中心 + 偏移量）
        float centerX = width * 0.5f + offsetX;
        float centerY = height * 0.5f + offsetY;

        // ===== 根据当前船舶位置计算屏幕坐标边界，并在超出可见区域时整体平移画布 =====
        // 船舶在世界坐标中的边界
        float shipLeftWorld = centerX - shipWidth * 0.5f;
        float shipRightWorld = centerX + shipWidth * 0.5f;
        float shipTopWorld = centerY - shipHeight * 0.5f;
        float shipBottomWorld = centerY + shipHeight * 0.5f;

        // 将世界坐标转换为屏幕坐标（考虑缩放中心在视图中心 + 画布平移）
        float halfW = width * 0.5f;
        float halfH = height * 0.5f;

        float shipLeftScreen = canvasOffsetX + (shipLeftWorld - halfW) * scaleFactor + halfW;
        float shipRightScreen = canvasOffsetX + (shipRightWorld - halfW) * scaleFactor + halfW;
        float shipTopScreen = canvasOffsetY + (shipTopWorld - halfH) * scaleFactor + halfH;
        float shipBottomScreen = canvasOffsetY + (shipBottomWorld - halfH) * scaleFactor + halfH;

        float dx = 0f;
        float dy = 0f;

        // 左右边界检测（使用水平边界安全距离）
        if (shipLeftScreen < BOUNDARY_MARGIN_HORIZONTAL) {
            dx = BOUNDARY_MARGIN_HORIZONTAL - shipLeftScreen;
        } else if (shipRightScreen > width - BOUNDARY_MARGIN_HORIZONTAL) {
            dx = (width - BOUNDARY_MARGIN_HORIZONTAL) - shipRightScreen;
        }

        // 上下边界检测
        if (shipTopScreen < BOUNDARY_MARGIN) {
            dy = BOUNDARY_MARGIN - shipTopScreen;
        } else if (shipBottomScreen > height - BOUNDARY_MARGIN) {
            dy = (height - BOUNDARY_MARGIN) - shipBottomScreen;
        }

        // 如果需要，则整体平移画布，使船舶整体回到可见区域
        if (dx != 0f || dy != 0f) {
            canvasOffsetX += dx;
            canvasOffsetY += dy;
        }

        // 保存画布状态并应用整体偏移和缩放（桩子、船舶、RTK一起移动）
        canvas.save();
        canvas.translate(canvasOffsetX, canvasOffsetY);
        canvas.scale(scaleFactor, scaleFactor, width * 0.5f, height * 0.5f);

        float halfWWorld = width * 0.5f;
        float halfHWorld = height * 0.5f;

        float visibleLeftWorld = halfWWorld + (0f - canvasOffsetX - halfWWorld) / scaleFactor;
        float visibleRightWorld = halfWWorld + (width - canvasOffsetX - halfWWorld) / scaleFactor;
        float visibleTopWorld = halfHWorld + (0f - canvasOffsetY - halfHWorld) / scaleFactor;
        float visibleBottomWorld = halfHWorld + (height - canvasOffsetY - halfHWorld) / scaleFactor;

        float extendWorld = POOL_EXTEND_M * WORLD_UNITS_PER_METER;
        float boundaryLeft = -extendWorld;
        float boundaryTop = -extendWorld;
        float boundaryRight = width + extendWorld;
        float boundaryBottom = height + extendWorld;

        drawReefAndBoundary(canvas,
                visibleLeftWorld, visibleTopWorld, visibleRightWorld, visibleBottomWorld,
                boundaryLeft, boundaryTop, boundaryRight, boundaryBottom);
        updateBoundaryAlarm(true, centerX, centerY, shipWidth, shipHeight, boundaryLeft, boundaryTop, boundaryRight, boundaryBottom);

        if (!trackPoints.isEmpty()) {
            Path p = new Path();
            for (int i = 0; i < trackPoints.size(); i++) {
                PointF pt = trackPoints.get(i);
                if (pt == null) continue;
                float x = width * 0.5f + pt.x;
                float y = height * 0.5f + pt.y;
                if (i == 0) {
                    p.moveTo(x, y);
                } else {
                    p.lineTo(x, y);
                }
            }
            canvas.drawPath(p, trackPaint);
        }

        if (targetPoint != null) {
            float tx = width * 0.5f + targetPoint.x;
            float ty = height * 0.5f + targetPoint.y;
            float r = 18f;
            canvas.drawCircle(tx, ty, r, targetPaint);
            canvas.drawLine(tx - r, ty, tx + r, ty, targetPaint);
            canvas.drawLine(tx, ty - r, tx, ty + r, targetPaint);
        }

        // 计算RTK位置（船头和船尾，距离端点一小段距离）
        float bowTopY = centerY - shipHeight * 0.5f;
        float sternBottomY = centerY + shipHeight * 0.5f;
        // RTK点距离船头和船尾端点一小段距离（8%的船长）
        float rtkOffset = shipHeight * RTK_OFFSET_RATIO;
        float rtkBowX = centerX;
        float rtkBowY = bowTopY + rtkOffset;  // 从船头顶部向下偏移
        float rtkSternX = centerX;
        float rtkSternY = sternBottomY - rtkOffset;  // 从船尾底部向上偏移
        
        // 计算两个RTK之间的距离
        float rtkDistance = (float) Math.sqrt(
            Math.pow(rtkSternX - rtkBowX, 2) + Math.pow(rtkSternY - rtkBowY, 2)
        );
        
        // 计算原有的一对桩子位置（在空白区域，距离与RTK距离相等）
        calculateStakePositions(width, height, rtkDistance);
        
        // 如果随机桩点对列表为空且未生成过，生成一次固定随机桩点对
        if (!stakesGenerated && stakePairs.isEmpty()) {
            generateRandomStakePairs(width, height, rtkDistance, centerX, centerY, shipWidth, shipHeight);
            stakesGenerated = true;
        }
        
        // ========== 在同一画布上绘制所有桩子（背景层） ==========
        // 绘制所有随机桩点对（25对，南北方向排列，距离与RTK距离相等）
        for (float[] pair : stakePairs) {
            // 绘制北侧桩子
            canvas.drawCircle(pair[0], pair[1], STAKE_RADIUS, stakePaint);
            // 绘制南侧桩子
            canvas.drawCircle(pair[2], pair[3], STAKE_RADIUS, stakePaint);
        }
        
        // 绘制原有的一对桩子（南北方向排列，距离与RTK距离相等）
        canvas.drawCircle(stake1X, stake1Y, STAKE_RADIUS, stakePaint);
        canvas.drawCircle(stake2X, stake2Y, STAKE_RADIUS, stakePaint);

        // 高亮选中的桩点对
        float[] selected = getSelectedStakePairWorldInternal();
        if (selected != null) {
            canvas.drawLine(selected[0], selected[1], selected[2], selected[3], selectedStakePaint);
            canvas.drawCircle(selected[0], selected[1], STAKE_RADIUS + 6f, selectedStakePaint);
            canvas.drawCircle(selected[2], selected[3], STAKE_RADIUS + 6f, selectedStakePaint);
        }

        if (selected != null) {
            PointF[] path = computeGuidancePathWorld(centerX, centerY, shipHeight, selected);
            if (path != null && path.length >= 3) {
                float distToP1 = distance(path[0].x, path[0].y, path[1].x, path[1].y);
                float distToP2 = distance(path[0].x, path[0].y, path[2].x, path[2].y);
                float toTargetTh = 120f;
                boolean goDirectTarget = distToP2 <= toTargetTh || distToP2 <= distToP1;

                Path p = new Path();
                p.moveTo(path[0].x, path[0].y);
                if (goDirectTarget) {
                    p.lineTo(path[2].x, path[2].y);
                    canvas.drawPath(p, guidancePaint);
                    drawArrowHead(canvas, path[0], path[2], guidancePaint);
                } else {
                    p.lineTo(path[1].x, path[1].y);
                    p.lineTo(path[2].x, path[2].y);
                    canvas.drawPath(p, guidancePaint);
                    drawArrowHead(canvas, path[1], path[2], guidancePaint);
                }
            }
        }
        
        // ========== 绘制船舶（前景层） ==========

        canvas.save();
        canvas.rotate(headingDeg, centerX, centerY);

        // 绘制船舶路径
        Path shipPath = createShipPath(centerX, centerY, shipWidth, shipHeight);

        // 先绘制填充
        canvas.drawPath(shipPath, shipPaint);

        // 再绘制轮廓
        canvas.drawPath(shipPath, outlinePaint);

        // 绘制RTK点（船头和船尾）
        canvas.drawCircle(rtkBowX, rtkBowY, RTK_RADIUS, rtkPaint);
        canvas.drawCircle(rtkSternX, rtkSternY, RTK_RADIUS, rtkPaint);

        float arrowLen = shipHeight * 0.22f;
        float arrowHalf = shipWidth * 0.10f;
        float ax0 = centerX;
        float ay0 = centerY - shipHeight * 0.15f;
        float ax1 = centerX;
        float ay1 = ay0 - arrowLen;
        canvas.drawLine(ax0, ay0, ax1, ay1, headingPaint);
        canvas.drawLine(ax1, ay1, ax1 - arrowHalf, ay1 + arrowHalf, headingPaint);
        canvas.drawLine(ax1, ay1, ax1 + arrowHalf, ay1 + arrowHalf, headingPaint);

        canvas.restore();

        // 恢复画布状态
        canvas.restore();

        drawCompass(canvas, width, height);
    }

    private void drawReefAndBoundary(Canvas canvas,
                                     float visibleLeft, float visibleTop, float visibleRight, float visibleBottom,
                                     float boundaryLeft, float boundaryTop, float boundaryRight, float boundaryBottom) {
        float reefBandWorld = REEF_BAND_M * WORLD_UNITS_PER_METER;
        float warnWorld = BOUNDARY_WARN_M * WORLD_UNITS_PER_METER;

        float waveAmp = 8f;
        float waveStep = 48f;
        int waveCountH = Math.max(1, (int) ((boundaryRight - boundaryLeft) / waveStep));
        int waveCountV = Math.max(1, (int) ((boundaryBottom - boundaryTop) / waveStep));

        boolean hasTop = visibleTop < boundaryTop;
        boolean hasLeft = visibleLeft < boundaryLeft;
        boolean hasRight = visibleRight > boundaryRight;

        float topBand = Math.min(reefBandWorld, Math.max(0f, boundaryTop - visibleTop));
        float leftBand = Math.min(reefBandWorld, Math.max(0f, boundaryLeft - visibleLeft));
        float rightBand = Math.min(reefBandWorld, Math.max(0f, visibleRight - boundaryRight));

        float waveOutBias = waveAmp * 1.8f;
        float topWaveBase = boundaryTop - waveOutBias;
        float leftWaveBase = boundaryLeft - waveOutBias;
        float rightWaveBase = boundaryRight + waveOutBias;

        if (hasTop && topBand > 0f) {
            Path topFill = new Path();
            topFill.moveTo(visibleLeft, visibleTop);
            topFill.lineTo(visibleRight, visibleTop);
            topFill.lineTo(visibleRight, boundaryTop);
            for (int i = waveCountH; i >= 0; i--) {
                float x = boundaryLeft + i * ((boundaryRight - boundaryLeft) / waveCountH);
                float y = topWaveBase + (float) Math.sin(i * 0.7f) * waveAmp;
                topFill.lineTo(x, y);
            }
            topFill.lineTo(visibleLeft, boundaryTop);
            topFill.close();
            canvas.drawPath(topFill, reefPaint);
        }

        Path topBorder = new Path();
        topBorder.moveTo(boundaryLeft, topWaveBase + (float) Math.sin(0f) * waveAmp);
        for (int i = 1; i <= waveCountH; i++) {
            float x = boundaryLeft + i * ((boundaryRight - boundaryLeft) / waveCountH);
            float y = topWaveBase + (float) Math.sin(i * 0.7f) * waveAmp;
            topBorder.lineTo(x, y);
        }
        canvas.drawPath(topBorder, reefBorderPaint);

        if (hasLeft && leftBand > 0f) {
            Path leftFill = new Path();
            leftFill.moveTo(visibleLeft, visibleTop);
            leftFill.lineTo(boundaryLeft, visibleTop);
            for (int i = 0; i <= waveCountV; i++) {
                float y = boundaryTop + i * ((boundaryBottom - boundaryTop) / waveCountV);
                float x = leftWaveBase + (float) Math.sin(i * 0.7f + 1.3f) * waveAmp;
                leftFill.lineTo(x, y);
            }
            leftFill.lineTo(boundaryLeft, visibleBottom);
            leftFill.lineTo(visibleLeft, visibleBottom);
            leftFill.close();
            canvas.drawPath(leftFill, reefPaint);
        }

        Path leftBorder = new Path();
        leftBorder.moveTo(leftWaveBase + (float) Math.sin(0f + 1.3f) * waveAmp, boundaryTop);
        for (int i = 1; i <= waveCountV; i++) {
            float y = boundaryTop + i * ((boundaryBottom - boundaryTop) / waveCountV);
            float x = leftWaveBase + (float) Math.sin(i * 0.7f + 1.3f) * waveAmp;
            leftBorder.lineTo(x, y);
        }
        canvas.drawPath(leftBorder, reefBorderPaint);

        if (hasRight && rightBand > 0f) {
            Path rightFill = new Path();
            rightFill.moveTo(boundaryRight, visibleTop);
            rightFill.lineTo(visibleRight, visibleTop);
            rightFill.lineTo(visibleRight, visibleBottom);
            rightFill.lineTo(boundaryRight, visibleBottom);
            for (int i = waveCountV; i >= 0; i--) {
                float y = boundaryTop + i * ((boundaryBottom - boundaryTop) / waveCountV);
                float x = rightWaveBase + (float) Math.sin(i * 0.7f + 2.1f) * waveAmp;
                rightFill.lineTo(x, y);
            }
            rightFill.close();
            canvas.drawPath(rightFill, reefPaint);
        }

        Path rightBorder = new Path();
        rightBorder.moveTo(rightWaveBase + (float) Math.sin(0f + 2.1f) * waveAmp, boundaryTop);
        for (int i = 1; i <= waveCountV; i++) {
            float y = boundaryTop + i * ((boundaryBottom - boundaryTop) / waveCountV);
            float x = rightWaveBase + (float) Math.sin(i * 0.7f + 2.1f) * waveAmp;
            rightBorder.lineTo(x, y);
        }
        canvas.drawPath(rightBorder, reefBorderPaint);

        float warnAmp = 6f;
        float warnOutBias = warnAmp * 1.6f;
        float topWarnBase = boundaryTop + warnWorld - warnOutBias;
        float leftWarnBase = boundaryLeft + warnWorld - warnOutBias;
        float rightWarnBase = boundaryRight - warnWorld + warnOutBias;
        Path topWarn = new Path();
        topWarn.moveTo(boundaryLeft, topWarnBase + (float) Math.sin(0f + 0.4f) * warnAmp);
        for (int i = 1; i <= waveCountH; i++) {
            float x = boundaryLeft + i * ((boundaryRight - boundaryLeft) / waveCountH);
            float y = topWarnBase + (float) Math.sin(i * 0.7f + 0.4f) * warnAmp;
            topWarn.lineTo(x, y);
        }
        canvas.drawPath(topWarn, boundaryWarnPaint);

        Path leftWarn = new Path();
        leftWarn.moveTo(leftWarnBase + (float) Math.sin(0f + 1.7f) * warnAmp, boundaryTop);
        for (int i = 1; i <= waveCountV; i++) {
            float y = boundaryTop + i * ((boundaryBottom - boundaryTop) / waveCountV);
            float x = leftWarnBase + (float) Math.sin(i * 0.7f + 1.7f) * warnAmp;
            leftWarn.lineTo(x, y);
        }
        canvas.drawPath(leftWarn, boundaryWarnPaint);

        Path rightWarn = new Path();
        rightWarn.moveTo(rightWarnBase + (float) Math.sin(0f + 2.5f) * warnAmp, boundaryTop);
        for (int i = 1; i <= waveCountV; i++) {
            float y = boundaryTop + i * ((boundaryBottom - boundaryTop) / waveCountV);
            float x = rightWarnBase + (float) Math.sin(i * 0.7f + 2.5f) * warnAmp;
            rightWarn.lineTo(x, y);
        }
        canvas.drawPath(rightWarn, boundaryWarnPaint);
    }

    private void updateBoundaryAlarm(boolean enabled, float shipCenterX, float shipCenterY, float shipWidth, float shipHeight,
                                     float left, float top, float right, float bottom) {
        if (!enabled) {
            boundaryAlarmActive = false;
            lastMinDistToBoundaryM = Float.POSITIVE_INFINITY;
            return;
        }

        float shipRadius = 0.5f * Math.max(shipWidth, shipHeight);

        float dLeft = (shipCenterX - shipRadius) - left;
        float dRight = right - (shipCenterX + shipRadius);
        float dTop = (shipCenterY - shipRadius) - top;

        float minWorld = Math.min(Math.min(dLeft, dRight), dTop);
        lastMinDistToBoundaryM = minWorld / WORLD_UNITS_PER_METER;

        if (!boundaryAlarmActive) {
            boundaryAlarmActive = lastMinDistToBoundaryM <= BOUNDARY_WARN_M;
        } else {
            boundaryAlarmActive = lastMinDistToBoundaryM <= BOUNDARY_CLEAR_M;
        }
    }

    public boolean isBoundaryAlarmActive() {
        return boundaryAlarmActive;
    }

    public float getMinDistanceToBoundaryMeters() {
        return lastMinDistToBoundaryM;
    }

    public PointF[] getGuidancePathWorld() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return null;
        float[] selected = getSelectedStakePairWorldInternal();
        if (selected == null) return null;
        float shipHeight = height * 0.28f;
        float centerX = width * 0.5f + offsetX;
        float centerY = height * 0.5f + offsetY;
        return computeGuidancePathWorld(centerX, centerY, shipHeight, selected);
    }

    private PointF[] computeGuidancePathWorld(float shipCenterX, float shipCenterY, float shipHeight, float[] stakePair) {
        if (stakePair == null || stakePair.length < 4) return null;

        float northX = stakePair[0];
        float northY = stakePair[1];
        float southX = stakePair[2];
        float southY = stakePair[3];

        float targetCx = (northX + southX) * 0.5f;
        float targetCy = (northY + southY) * 0.5f;

        float vx = northX - southX;
        float vy = northY - southY;
        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len < 1e-3f) return null;
        float ux = vx / len;
        float uy = vy / len;

        float approachDist = Math.max(120f, shipHeight * 1.1f);
        float preX = targetCx - ux * approachDist;
        float preY = targetCy - uy * approachDist;

        return new PointF[]{
                new PointF(shipCenterX, shipCenterY),
                new PointF(preX, preY),
                new PointF(targetCx, targetCy)
        };
    }

    private void drawArrowHead(Canvas canvas, PointF from, PointF to, Paint paint) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float ux = dx / len;
        float uy = dy / len;

        float arrowLen = 26f;
        float arrowHalf = 12f;

        float tipX = to.x;
        float tipY = to.y;
        float baseX = tipX - ux * arrowLen;
        float baseY = tipY - uy * arrowLen;

        float px = -uy;
        float py = ux;

        float leftX = baseX + px * arrowHalf;
        float leftY = baseY + py * arrowHalf;
        float rightX = baseX - px * arrowHalf;
        float rightY = baseY - py * arrowHalf;

        canvas.drawLine(tipX, tipY, leftX, leftY, paint);
        canvas.drawLine(tipX, tipY, rightX, rightY, paint);
    }

    private void drawCompass(Canvas canvas, int width, int height) {
        float cx = width - 70f;
        float cy = 70f;
        float r = 44f;
        canvas.drawCircle(cx, cy, r, compassPaint);

        float a = (float) Math.toRadians(headingDeg);
        float dx = (float) Math.sin(a);
        float dy = (float) -Math.cos(a);
        float x1 = cx + dx * (r - 6f);
        float y1 = cy + dy * (r - 6f);
        canvas.drawLine(cx, cy, x1, y1, compassPaint);
        canvas.drawLine(x1, y1, x1 - dy * 10f - dx * 6f, y1 + dx * 10f - dy * 6f, compassPaint);
        canvas.drawLine(x1, y1, x1 + dy * 10f - dx * 6f, y1 - dx * 10f - dy * 6f, compassPaint);
    }
    
    /**
     * 计算桩子位置，确保两个桩子的距离与双RTK的距离相等
     * 桩子排列在南北方向（垂直方向）
     * @param width View宽度
     * @param height View高度
     * @param rtkDistance RTK距离
     */
    private void calculateStakePositions(float width, float height, float rtkDistance) {
        // 桩子位置放在左上角附近的空白区域，南北方向排列
        // 第一个桩子（北侧）在左上角附近
        stake1X = width * 0.2f;
        stake1Y = height * 0.15f;
        
        // 第二个桩子（南侧）在与第一个桩子垂直方向上，距离为rtkDistance
        stake2X = stake1X;  // X坐标相同，保持垂直
        stake2Y = stake1Y + rtkDistance;  // Y坐标向下，形成南北方向
        
        // 如果第二个桩子超出下边界，调整位置
        if (stake2Y > height * 0.85f) {
            // 调整到底部附近
            stake2Y = height * 0.85f;
            stake1Y = stake2Y - rtkDistance;
        }
    }

    /**
     * 创建船舶路径
     * 形状：顶部稍尖（船头），中间细长矩形主体，底部V形尖点（船尾）
     * @param centerX 船舶中心X坐标
     * @param centerY 船舶中心Y坐标
     * @param width 船舶宽度
     * @param height 船舶高度
     * @return 船舶路径
     */
    private Path createShipPath(float centerX, float centerY, float width, float height) {
        Path path = new Path();

        // 船头和船尾长度（各占船长的12%，不要太长）
        float bowLength = height * 0.12f;
        float sternLength = height * 0.12f;
        // 船身主体长度（占船长的76%）
        float bodyLength = height * 0.76f;

        // 船体宽度（船身主体的宽度）
        float shipWidth = width * 0.25f;

        // 计算各关键点坐标
        // 船头顶部（圆弧的最高点）
        float bowTopY = centerY - height * 0.5f;
        // 船头与船身连接点（圆弧底部）
        float bowBodyY = bowTopY + bowLength;
        
        // 船身与船尾连接点
        float bodySternY = bowBodyY + bodyLength;
        
        // 船尾底部（V形尖点）
        float sternBottomY = centerY + height * 0.5f;

        // 船身左侧和右侧的X坐标
        float leftX = centerX - shipWidth * 0.5f;
        float rightX = centerX + shipWidth * 0.5f;

        // 绘制路径：从左侧船头与船身连接点开始
        path.moveTo(leftX, bowBodyY);

        // 绘制船头顶部（稍微尖一点）- 使用二次贝塞尔曲线
        // 从左侧到顶部尖点
        path.quadTo(
            centerX - shipWidth * 0.2f, bowTopY + bowLength * 0.1f,  // 控制点
            centerX, bowTopY  // 顶部尖点
        );

        // 从顶部尖点到右侧
        path.quadTo(
            centerX + shipWidth * 0.2f, bowTopY + bowLength * 0.1f,  // 控制点
            rightX, bowBodyY  // 右侧终点
        );

        // 绘制船身右侧垂直边（从船头到船尾连接处）
        path.lineTo(rightX, bodySternY);

        // 绘制船尾右侧边（从船身到船尾底部尖点，形成V形右侧）
        path.lineTo(centerX + shipWidth * 0.25f, sternBottomY); // V形右侧

        // 到达船尾底部尖点
        path.lineTo(centerX, sternBottomY); // 船尾底部尖点

        // 绘制船尾左侧边（从尖点到船身，形成V形左侧）
        path.lineTo(centerX - shipWidth * 0.25f, sternBottomY); // V形左侧

        // 绘制船身左侧垂直边（从船尾连接处回到起点）
        path.lineTo(leftX, bodySternY);
        path.lineTo(leftX, bowBodyY); // 回到起点

        // 闭合路径
        path.close();

        return path;
    }
    
    /**
     * 向上移动船舶
     */
    public void moveUp() {
        offsetY -= MOVE_STEP;
        invalidate();
    }
    
    /**
     * 向下移动船舶
     */
    public void moveDown() {
        offsetY += MOVE_STEP;
        invalidate();
    }
    
    /**
     * 向左移动船舶
     */
    public void moveLeft() {
        offsetX -= MOVE_STEP;
        invalidate();
    }
    
    /**
     * 向右移动船舶
     */
    public void moveRight() {
        offsetX += MOVE_STEP;
        invalidate();
    }

    /**
     * 按偏移量移动船舶（用于摇杆控制）
     * @param dx X方向偏移量
     * @param dy Y方向偏移量
     */
    public void moveByOffset(float dx, float dy) {
        offsetX += dx;
        offsetY += dy;
        invalidate();
    }
    
    public void setPose(float x, float y, float headingDeg) {
        this.offsetX = x;
        this.offsetY = y;
        this.headingDeg = headingDeg;
        invalidate();
    }

    public void setHeadingDeg(float headingDeg) {
        this.headingDeg = headingDeg;
        invalidate();
    }

    public void setTarget(float x, float y) {
        this.targetPoint = new PointF(x, y);
        invalidate();
    }

    public void clearTarget() {
        this.targetPoint = null;
        invalidate();
    }

    public void appendTrackPoint(float x, float y) {
        trackPoints.add(new PointF(x, y));
        if (trackPoints.size() > maxTrackPoints) {
            int removeCount = trackPoints.size() - maxTrackPoints;
            for (int i = 0; i < removeCount; i++) {
                if (!trackPoints.isEmpty()) trackPoints.remove(0);
            }
        }
        invalidate();
    }

    public void clearTrack() {
        trackPoints.clear();
        invalidate();
    }


    /**
     * @return selected stake pair as [northXRel, northYRel, southXRel, southYRel] relative to view center,
     * or null if none selected yet.
     */
    public float[] getSelectedStakePairRelativeToCenter() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return null;

        float[] w = getSelectedStakePairWorldInternal();
        if (w == null) return null;

        float halfW = width * 0.5f;
        float halfH = height * 0.5f;
        return new float[]{w[0] - halfW, w[1] - halfH, w[2] - halfW, w[3] - halfH};
    }

    /**
     * Compute docking pose so that bow RTK matches north stake and stern RTK matches south stake.
     * Input stake coordinates are relative to view center.
     * @return [shipCenterXRel, shipCenterYRel, headingDeg] or null if view not ready.
     */
    public float[] computeDockPoseForStakePair(float northXRel, float northYRel, float southXRel, float southYRel) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return null;

        // Use same sizing rule as onDraw
        float shipHeight = height * 0.28f;
        float rtkOffset = shipHeight * RTK_OFFSET_RATIO;
        float d = shipHeight * 0.5f - rtkOffset; // center -> bow RTK distance

        // Heading definition in this view: 0° points to screen top (north), increasing clockwise.
        // We want bow (arrow) pointing to the north stake, so heading follows vector from south -> north.
        float vx = northXRel - southXRel;
        float vy = northYRel - southYRel;
        float heading = (float) Math.toDegrees(Math.atan2(vx, -vy));

        float a = (float) Math.toRadians(heading);
        // bow RTK offset from center after rotation
        float bowDx = (float) (Math.sin(a) * d);
        float bowDy = (float) (-Math.cos(a) * d);

        float cx = northXRel - bowDx;
        float cy = northYRel - bowDy;
        return new float[]{cx, cy, heading};
    }

    /**
     * @return center -> RTK distance in current view size (used for docking error calc). Returns 0 if view not ready.
     */
    public float getRtkCenterOffset() {
        int height = getHeight();
        if (height <= 0) return 0f;
        float shipHeight = height * 0.28f;
        float rtkOffset = shipHeight * RTK_OFFSET_RATIO;
        return shipHeight * 0.5f - rtkOffset;
    }

    private float[] getSelectedStakePairWorldInternal() {
        if (selectedStakePairIndex == SELECTED_NONE) return null;

        if (selectedStakePairIndex == SELECTED_FIXED_PAIR) {
            return new float[]{stake1X, stake1Y, stake2X, stake2Y};
        }

        if (selectedStakePairIndex >= 0 && selectedStakePairIndex < stakePairs.size()) {
            float[] pair = stakePairs.get(selectedStakePairIndex);
            if (pair != null && pair.length >= 4) {
                return new float[]{pair[0], pair[1], pair[2], pair[3]};
            }
        }
        return null;
    }
    
    /**
     * 放大
     */
    public void zoomIn() {
        scaleManager.zoomIn();
        invalidate();
    }
    
    /**
     * 缩小
     */
    public void zoomOut() {
        scaleManager.zoomOut();
        invalidate();
    }

    public float getCurrentScaleFactor() {
        return scaleManager.getCurrentScale();
    }

    public PointF getShipCenterWorld() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return null;
        return new PointF(width * 0.5f + offsetX, height * 0.5f + offsetY);
    }

    public float getShipHeadingDeg() {
        return headingDeg;
    }
    
    /**
     * 生成均匀网格分布的桩点对
     * 每对桩点南北方向排列，距离等于RTK距离
     * 桩点按照网格均匀分布，带有标签（如A1, B2等）
     * 
     * @param width View宽度
     * @param height View高度
     * @param rtkDistance RTK距离
     * @param shipCenterX 船舶中心X坐标
     * @param shipCenterY 船舶中心Y坐标
     * @param shipWidth 船舶宽度
     * @param shipHeight 船舶高度
     */
    private void generateRandomStakePairs(float width, float height, float rtkDistance, 
                                         float shipCenterX, float shipCenterY, 
                                         float shipWidth, float shipHeight) {
        stakePairs.clear();
        stakeLabels.clear();
        
        // 船舶占用的区域（避免桩点与船舶重叠）
        float shipLeft = shipCenterX - shipWidth * 0.6f;
        float shipRight = shipCenterX + shipWidth * 0.6f;
        float shipTop = shipCenterY - shipHeight * 0.6f;
        float shipBottom = shipCenterY + shipHeight * 0.6f;
        
        // 计算网格区域（港池安全区内）
        float extendWorld = POOL_EXTEND_M * WORLD_UNITS_PER_METER;
        float boundaryLeft = -extendWorld;
        float boundaryTop = -extendWorld;
        float boundaryRight = width + extendWorld;
        float boundaryBottom = height + extendWorld;

        float margin = Math.max(STAKE_RADIUS * 2f, 60f);
        float regionLeft = boundaryLeft + margin;
        float regionTop = boundaryTop + margin;
        float regionW = Math.max(1f, (boundaryRight - boundaryLeft) - 2f * margin);
        float regionH = Math.max(1f, (boundaryBottom - boundaryTop) - 2f * margin);

        // 计算网格行列数（确保均匀分布）
        float usableH = Math.max(1f, regionH - rtkDistance);
        
        // 根据目标数量计算行列
        int totalTarget = STAKE_PAIR_COUNT;
        float aspectRatio = regionW / usableH;
        int cols = (int) Math.round(Math.sqrt(totalTarget * aspectRatio));
        int rows = (int) Math.round(Math.sqrt(totalTarget / aspectRatio));
        
        // 确保至少有合理的行列数
        cols = Math.max(4, Math.min(cols, 15));
        rows = Math.max(3, Math.min(rows, 12));

        float cellW = regionW / cols;
        float cellH = usableH / rows;

        // 生成均匀网格桩点对
        int added = 0;
        for (int r = 0; r < rows && added < totalTarget; r++) {
            for (int c = 0; c < cols && added < totalTarget; c++) {
                // 计算网格中心位置（均匀分布，无随机扰动）
                float centerX = regionLeft + (c + 0.5f) * cellW;
                float centerY = regionTop + (r + 0.5f) * cellH;

                // 北桩位置（中心向上偏移半个RTK距离）
                float northX = centerX;
                float northY = centerY;
                
                // 南桩位置（北桩向下偏移RTK距离）
                float southX = northX;
                float southY = northY + rtkDistance;

                // 确保南桩不超出区域下界
                if (southY > regionTop + regionH + margin) continue;

                // 检查是否与船舶重叠
                if (isStakePairOverlappingShip(northX, northY, southX, southY,
                        shipLeft, shipRight, shipTop, shipBottom)) {
                    continue;
                }

                // 添加桩点对
                stakePairs.add(new float[]{northX, northY, southX, southY});
                
                // 生成标签（列用字母A-Z，行用数字1-N）
                char colLabel = (char) ('A' + (c % 26));
                String label = String.valueOf(colLabel) + (r + 1);
                stakeLabels.add(label);
                
                added++;
            }
        }
    }
    
    // 桩点标签列表
    private List<String> stakeLabels = new ArrayList<>();
    
    /**
     * 获取指定索引的桩点标签
     * @param index 桩点对索引
     * @return 标签字符串（如"A1", "B2"）
     */
    public String getStakeLabel(int index) {
        if (index >= 0 && index < stakeLabels.size()) {
            return stakeLabels.get(index);
        }
        return "";
    }
    
    /**
     * 获取当前选中桩点的标签
     */
    public String getSelectedStakeLabel() {
        if (selectedStakePairIndex == SELECTED_FIXED_PAIR) {
            return "固定桩";
        }
        return getStakeLabel(selectedStakePairIndex);
    }
    
    /**
     * 检查桩点对是否与船舶重叠
     * @param northX 北侧桩子X坐标
     * @param northY 北侧桩子Y坐标
     * @param southX 南侧桩子X坐标
     * @param southY 南侧桩子Y坐标
     * @param shipLeft 船舶左边界
     * @param shipRight 船舶右边界
     * @param shipTop 船舶上边界
     * @param shipBottom 船舶下边界
     * @return true如果桩点对与船舶重叠
     */
    private boolean isStakePairOverlappingShip(float northX, float northY, float southX, float southY,
                                              float shipLeft, float shipRight, float shipTop, float shipBottom) {
        // 检查北侧桩子是否在船舶区域内
        boolean northOverlap = northX >= shipLeft && northX <= shipRight && 
                              northY >= shipTop && northY <= shipBottom;
        // 检查南侧桩子是否在船舶区域内
        boolean southOverlap = southX >= shipLeft && southX <= shipRight && 
                              southY >= shipTop && southY <= shipBottom;
        
        return northOverlap || southOverlap;
    }
    
    /**
     * 将船舶重置到画布中心
     */
    public void resetPosition() {
        float scaleFactor = scaleManager.getCurrentScale();
        canvasOffsetX = -offsetX * scaleFactor;
        canvasOffsetY = -offsetY * scaleFactor;
        invalidate();
    }
}
