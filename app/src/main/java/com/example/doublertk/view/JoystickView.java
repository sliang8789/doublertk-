package com.example.doublertk.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 虚拟摇杆控件，用于控制船舶移动
 */
public class JoystickView extends View {

    private Paint basePaint;
    private Paint stickPaint;
    private Paint borderPaint;

    private float centerX;
    private float centerY;
    private float baseRadius;
    private float stickRadius;

    private float stickX;
    private float stickY;

    private OnJoystickMoveListener listener;

    public interface OnJoystickMoveListener {
        /**
         * 摇杆移动回调
         * @param xPercent X方向偏移百分比 [-1, 1]，正值向右
         * @param yPercent Y方向偏移百分比 [-1, 1]，正值向下
         */
        void onMove(float xPercent, float yPercent);

        /**
         * 摇杆释放回调
         */
        void onRelease();
    }

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(Color.parseColor("#E0E0E0"));
        basePaint.setStyle(Paint.Style.FILL);

        stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickPaint.setColor(Color.parseColor("#673AB7"));
        stickPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#BDBDBD"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f - 8f;
        stickRadius = baseRadius * 0.4f;
        stickX = centerX;
        stickY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制底座
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius, borderPaint);

        // 绘制摇杆
        canvas.drawCircle(stickX, stickY, stickRadius, stickPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float touchX = event.getX();
                float touchY = event.getY();

                float dx = touchX - centerX;
                float dy = touchY - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                float maxDistance = baseRadius - stickRadius;

                if (distance > maxDistance) {
                    // 限制在底座范围内
                    float ratio = maxDistance / distance;
                    stickX = centerX + dx * ratio;
                    stickY = centerY + dy * ratio;
                } else {
                    stickX = touchX;
                    stickY = touchY;
                }

                invalidate();

                if (listener != null) {
                    float xPercent = (stickX - centerX) / maxDistance;
                    float yPercent = (stickY - centerY) / maxDistance;
                    listener.onMove(xPercent, yPercent);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 回到中心
                stickX = centerX;
                stickY = centerY;
                invalidate();

                if (listener != null) {
                    listener.onRelease();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    public void setOnJoystickMoveListener(OnJoystickMoveListener listener) {
        this.listener = listener;
    }
}
