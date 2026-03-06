package com.example.doublertk.view;

/**
 * 统一管理缩放比例，确保桩子、船舶、RTK 同步缩放
 */
public class ScaleManager {

    private static final float DEFAULT_SCALE = 1f;
    private static final float MIN_SCALE = 0.6f;
    private static final float MAX_SCALE = 1.6f;
    private static final float SCALE_STEP = 0.1f;

    private float currentScale = DEFAULT_SCALE;

    public float getCurrentScale() {
        return currentScale;
    }

    public float getMinScale() {
        return MIN_SCALE;
    }

    public float getMaxScale() {
        return MAX_SCALE;
    }

    public void resetScale() {
        currentScale = DEFAULT_SCALE;
    }

    public void zoomIn() {
        currentScale = Math.min(currentScale + SCALE_STEP, MAX_SCALE);
    }

    public void zoomOut() {
        currentScale = Math.max(currentScale - SCALE_STEP, MIN_SCALE);
    }

    /**
     * 按比例缩放（用于双指缩放手势）
     * @param scaleFactor 本次缩放因子（通常来自 ScaleGestureDetector#getScaleFactor）
     */
    public void applyScaleFactor(float scaleFactor) {
        currentScale *= scaleFactor;
        if (currentScale < MIN_SCALE) {
            currentScale = MIN_SCALE;
        } else if (currentScale > MAX_SCALE) {
            currentScale = MAX_SCALE;
        }
    }
}

