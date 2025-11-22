package com.example.doublertk.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
    
    // 船舶位置偏移量（相对于中心）
    private float offsetX = 0f;
    private float offsetY = 0f;
    
    // 整体画布偏移量（用于边界调整，确保船舶在可见区域内）
    private float canvasOffsetX = 0f;
    private float canvasOffsetY = 0f;
    private boolean manualCenterLocked = false;
    
    // 移动步长
    private static final float MOVE_STEP = 10f;
    
    // 边界安全边距（船舶距离边界的最小距离）
    private static final float BOUNDARY_MARGIN = 10f;  // 上下边界安全距离
    private static final float BOUNDARY_MARGIN_HORIZONTAL = 5f;  // 左右边界安全距离（更小）
    
    // RTK和桩子大小
    private static final float RTK_RADIUS = 6f;  // RTK点半径
    private static final float STAKE_RADIUS = 12f;  // 桩子半径
    
    // RTK距离船头和船尾的偏移比例（相对于船长）
    private static final float RTK_OFFSET_RATIO = 0.08f;  // 距离端点8%的船长
    
    // 桩子对数量
    private static final int STAKE_PAIR_COUNT = 25;  // 随机生成25对桩点
    
    // 桩子位置（在空白区域，左上角和右上角附近）- 保留原有的一对
    private float stake1X = 0f;
    private float stake1Y = 0f;
    private float stake2X = 0f;
    private float stake2Y = 0f;
    
    // 随机桩点对列表（每个对包含两个点：[北侧X, 北侧Y, 南侧X, 南侧Y]）
    private List<float[]> stakePairs = new ArrayList<>();
    private Random random = new Random();
    
    // 缩放管理器（控制桩子、船舶、RTK整体缩放）
    private final ScaleManager scaleManager = new ScaleManager();

    // 双指缩放手势检测器
    private ScaleGestureDetector scaleGestureDetector;
    
    // 标记桩点是否已生成（确保只生成一次）
    private boolean stakesGenerated = false;

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

        // 初始化双指缩放手势检测器
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (factor <= 0f || Float.isNaN(factor) || Float.isInfinite(factor)) {
                    return false;
                }
                scaleManager.applyScaleFactor(factor);
                // 手动缩放后，允许边界逻辑重新工作
                manualCenterLocked = false;
                invalidate();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);
        }
        // 仅处理缩放手势，不拦截其它事件
        return true;
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
        
        // ========== 绘制船舶（前景层） ==========
        
        // 绘制船舶路径
        Path shipPath = createShipPath(centerX, centerY, shipWidth, shipHeight);
        
        // 先绘制填充
        canvas.drawPath(shipPath, shipPaint);
        
        // 再绘制轮廓
        canvas.drawPath(shipPath, outlinePaint);
        
        // 绘制RTK点（船头和船尾）
        canvas.drawCircle(rtkBowX, rtkBowY, RTK_RADIUS, rtkPaint);
        canvas.drawCircle(rtkSternX, rtkSternY, RTK_RADIUS, rtkPaint);

        // 恢复画布状态
        canvas.restore();
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
        manualCenterLocked = false;
        invalidate();
    }
    
    /**
     * 向下移动船舶
     */
    public void moveDown() {
        offsetY += MOVE_STEP;
        manualCenterLocked = false;
        invalidate();
    }
    
    /**
     * 向左移动船舶
     */
    public void moveLeft() {
        offsetX -= MOVE_STEP;
        manualCenterLocked = false;
        invalidate();
    }
    
    /**
     * 向右移动船舶
     */
    public void moveRight() {
        offsetX += MOVE_STEP;
        manualCenterLocked = false;
        invalidate();
    }
    
    /**
     * 调整画布偏移量，确保船舶在可见区域内
     * @param width View宽度
     * @param height View高度
     * @param scaleFactor 当前缩放因子
     */
    private void adjustCanvasOffsetForBoundaries(float width, float height, float scaleFactor) {
        // 计算船舶的实际尺寸（考虑缩放）
        float shipWidth = width * 0.28f * scaleFactor;
        float shipHeight = height * 0.28f * scaleFactor;
        
        // 计算船舶在当前偏移量下的中心位置
        float shipCenterX = width * 0.5f + offsetX;
        float shipCenterY = height * 0.5f + offsetY;
        
        // 计算船舶边界（考虑缩放后的实际尺寸）
        float shipLeft = shipCenterX - shipWidth * 0.5f;
        float shipRight = shipCenterX + shipWidth * 0.5f;
        float shipTop = shipCenterY - shipHeight * 0.5f;
        float shipBottom = shipCenterY + shipHeight * 0.5f;
        
        // 计算View的可见区域边界（考虑缩放）
        float viewLeft = 0f;
        float viewRight = width;
        float viewTop = 0f;
        float viewBottom = height;
        
        // 计算需要的偏移量调整
        float deltaX = 0f;
        float deltaY = 0f;
        
        // 检查左边界（使用更小的水平边界安全距离）
        if (shipLeft < viewLeft + BOUNDARY_MARGIN_HORIZONTAL) {
            deltaX = (viewLeft + BOUNDARY_MARGIN_HORIZONTAL) - shipLeft;
        }
        // 检查右边界（使用更小的水平边界安全距离）
        else if (shipRight > viewRight - BOUNDARY_MARGIN_HORIZONTAL) {
            deltaX = (viewRight - BOUNDARY_MARGIN_HORIZONTAL) - shipRight;
        }
        
        // 检查上边界
        if (shipTop < viewTop + BOUNDARY_MARGIN) {
            deltaY = (viewTop + BOUNDARY_MARGIN) - shipTop;
        }
        // 检查下边界
        else if (shipBottom > viewBottom - BOUNDARY_MARGIN) {
            deltaY = (viewBottom - BOUNDARY_MARGIN) - shipBottom;
        }
        
        boolean insideBounds = shipLeft >= viewLeft + BOUNDARY_MARGIN_HORIZONTAL &&
                shipRight <= viewRight - BOUNDARY_MARGIN_HORIZONTAL &&
                shipTop >= viewTop + BOUNDARY_MARGIN &&
                shipBottom <= viewBottom - BOUNDARY_MARGIN;

        if (!insideBounds) {
            canvasOffsetX += deltaX;
            canvasOffsetY += deltaY;
            manualCenterLocked = false;
        } else if (!manualCenterLocked) {
            canvasOffsetX *= 0.9f;
            canvasOffsetY *= 0.9f;
            if (Math.abs(canvasOffsetX) < 1f) canvasOffsetX = 0f;
            if (Math.abs(canvasOffsetY) < 1f) canvasOffsetY = 0f;
        }
    }
    
    /**
     * 放大
     */
    public void zoomIn() {
        scaleManager.zoomIn();
        manualCenterLocked = false;
        invalidate();
    }
    
    /**
     * 缩小
     */
    public void zoomOut() {
        scaleManager.zoomOut();
        manualCenterLocked = false;
        invalidate();
    }
    
    /**
     * 生成随机桩点对
     * 每对桩点南北方向排列，距离等于RTK距离
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
        
        // 船舶占用的区域（避免桩点与船舶重叠）
        float shipLeft = shipCenterX - shipWidth * 0.6f;
        float shipRight = shipCenterX + shipWidth * 0.6f;
        float shipTop = shipCenterY - shipHeight * 0.6f;
        float shipBottom = shipCenterY + shipHeight * 0.6f;
        
        // 生成随机桩点对（范围大于当前可见区域，包含被遮挡的区域）
        for (int i = 0; i < STAKE_PAIR_COUNT; i++) {
            float northX, northY, southX, southY;
            int attempts = 0;
            
            // 尝试找到一个不与船舶重叠的位置
            do {
                // 随机X坐标：允许生成在可见区域外（左侧和右侧各扩展一屏）
                northX = -width + random.nextFloat() * (width * 3f);
                // 随机Y坐标（北侧桩子）：允许生成在可见区域外（上方和下方各扩展一屏）
                float maxNorthY = height * 2f - rtkDistance; // 确保南侧桩子不超出下界
                northY = -height + random.nextFloat() * (maxNorthY + height);
                
                // 南侧桩子（南北方向，X坐标相同，Y坐标增加rtkDistance）
                southX = northX;  // X坐标相同，保持垂直
                southY = northY + rtkDistance;  // Y坐标向下，形成南北方向
                
                attempts++;
            } while (isStakePairOverlappingShip(northX, northY, southX, southY, 
                                               shipLeft, shipRight, shipTop, shipBottom) 
                    && attempts < 50);
            
            // 如果找到了合适的位置，添加到列表
            if (attempts < 50) {
                stakePairs.add(new float[]{northX, northY, southX, southY});
            }
        }
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
     * 将视图居中到当前船舶位置（保持船舶与桩子的相对位置）
     * 将船舶居中到当前画布的中心
     */
    public void resetPosition() {
        // 获取当前缩放因子
        float scaleFactor = scaleManager.getCurrentScale();
        
        // 根据缩放因子计算画布偏移，使船舶显示在屏幕中心
        // 船舶在世界坐标中的中心：width * 0.5f + offsetX
        // 在屏幕坐标中显示为：canvasOffsetX + (offsetX) * scaleFactor + width * 0.5f
        // 要让它等于 width * 0.5f（屏幕中心），需要：
        // canvasOffsetX + offsetX * scaleFactor = 0
        // 所以：canvasOffsetX = -offsetX * scaleFactor
        canvasOffsetX = -offsetX * scaleFactor;
        canvasOffsetY = -offsetY * scaleFactor;
        manualCenterLocked = true;
        invalidate();
    }
}

