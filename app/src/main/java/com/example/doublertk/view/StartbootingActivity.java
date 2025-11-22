package com.example.doublertk.view;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

public class StartbootingActivity extends BaseActivity {

    private ShipView shipView;
    private Handler moveHandler = new Handler(Looper.getMainLooper());
    private Runnable moveRunnable;
    private boolean isMoving = false;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_start_booting;
    }

    @Override
    protected void initView() {
        setTopBarTitle("开始引导");
        setupShipControls();
    }

    /**
     * 设置船舶控制按钮
     */
    private void setupShipControls() {
        // 获取船舶视图
        shipView = findViewById(R.id.ship_view);
        
        // 获取方向控制按钮
        View btnMoveUp = findViewById(R.id.btn_move_up);
        View btnMoveDown = findViewById(R.id.btn_move_down);
        View btnMoveLeft = findViewById(R.id.btn_move_left);
        View btnMoveRight = findViewById(R.id.btn_move_right);

        // 设置按钮点击和长按事件
        if (btnMoveUp != null && shipView != null) {
            setupMoveButton(btnMoveUp, () -> shipView.moveUp());
        }

        if (btnMoveDown != null && shipView != null) {
            setupMoveButton(btnMoveDown, () -> shipView.moveDown());
        }

        if (btnMoveLeft != null && shipView != null) {
            setupMoveButton(btnMoveLeft, () -> shipView.moveLeft());
        }

        if (btnMoveRight != null && shipView != null) {
            setupMoveButton(btnMoveRight, () -> shipView.moveRight());
        }

        // 船身居中按钮
        Button btnResetPosition = findViewById(R.id.btn_reset_position);
        if (btnResetPosition != null && shipView != null) {
            btnResetPosition.setOnClickListener(v -> shipView.resetPosition());
        }
    }

    /**
     * 设置方向按钮的点击和长按功能
     * @param button 按钮
     * @param moveAction 移动动作
     */
    private void setupMoveButton(View button, Runnable moveAction) {
        // 单击事件
        button.setOnClickListener(v -> {
            if (!isMoving) {
                moveAction.run();
            }
        });

        // 长按事件（按下持续移动）
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 按下时立即执行一次移动
                    moveAction.run();
                    // 开始定时重复移动
                    isMoving = true;
                    moveRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (isMoving) {
                                moveAction.run();
                                // 每50毫秒移动一次
                                moveHandler.postDelayed(this, 50);
                            }
                        }
                    };
                    moveHandler.postDelayed(moveRunnable, 300); // 300ms后开始重复移动
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 抬起时停止移动
                    isMoving = false;
                    if (moveRunnable != null) {
                        moveHandler.removeCallbacks(moveRunnable);
                        moveRunnable = null;
                    }
                    return false; // 返回false让点击事件也能触发
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理Handler，避免内存泄漏
        if (moveHandler != null) {
            moveHandler.removeCallbacksAndMessages(null);
        }
    }
}

