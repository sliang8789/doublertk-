package com.example.doublertk.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

/**
 * 点测量菜单页面
 */
public class PointMeasureMenuActivity extends BaseActivity {

    private static final String TAG = "PointMeasureMenuActivity";
    private boolean isImmersiveModeEnabled = false;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_point_measure_menu;
    }

    @Override
    protected void initView() {
        setTopBarTitle("点测量");
    }

    // 强大的沉浸式模式实现
    private void enableImmersiveMode() {
        try {
            Log.d(TAG, "Enabling immersive mode");

            // 立即设置全屏标志，防止任何闪烁
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );

            // 设置布局参数，确保全屏
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

            // 使用 WindowInsetsControllerCompat 控制
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.hide(WindowInsetsCompat.Type.navigationBars());
                controller.hide(WindowInsetsCompat.Type.statusBars());
            }

            // 使用传统的setSystemUiVisibility（兼容性）
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            }

            // 强制重新布局
            getWindow().getDecorView().requestLayout();

            isImmersiveModeEnabled = true;
            Log.d(TAG, "Immersive mode enabled");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling immersive mode", e);
        }
    }

    // 强制隐藏系统UI的紧急方法
    private void forceHideSystemUI() {
        try {
            // 立即设置全屏
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );

            // 添加更多布局标志，确保内容填充整个屏幕
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            // 强制隐藏导航栏
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            }

            // 使用 WindowInsetsControllerCompat
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.hide(WindowInsetsCompat.Type.navigationBars());
                controller.hide(WindowInsetsCompat.Type.statusBars());
            }

            // 强制重新布局
            getWindow().getDecorView().requestLayout();

            Log.d(TAG, "forceHideSystemUI executed");
        } catch (Exception e) {
            Log.e(TAG, "Error in forceHideSystemUI", e);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 在窗口附加后立即强制隐藏系统UI
        forceHideSystemUI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 立即强制隐藏系统UI，防止任何闪烁
        forceHideSystemUI();

        // 禁用Activity切换动画，避免变暗效果
        overridePendingTransition(0, 0);

        // 立即接管系统UI控制，防止BaseActivity的沉浸式模式干扰
        getWindow().getDecorView().post(() -> {
            // 覆盖BaseActivity的沉浸式模式设置
            forceHideSystemUI();
            enableImmersiveMode();
        });

        setupClickListeners();

        // 多重延迟执行，确保系统UI完全隐藏
        getWindow().getDecorView().post(() -> {
            enableImmersiveMode();
        });

        // 再次延迟执行，确保系统UI状态稳定
        getWindow().getDecorView().postDelayed(() -> {
            enableImmersiveMode();
        }, 50);

        // 第三次延迟执行，防止任何可能的闪烁
        getWindow().getDecorView().postDelayed(() -> {
            enableImmersiveMode();
        }, 150);

        // 额外的延迟执行，确保在低端设备上也能生效
        getWindow().getDecorView().postDelayed(() -> {
            if (!isImmersiveModeEnabled) {
                forceHideSystemUI();
            }
        }, 300);

        // 最后的保险措施，确保系统UI完全隐藏
        getWindow().getDecorView().postDelayed(() -> {
            forceHideSystemUI();
            enableImmersiveMode();
        }, 500);
    }

    private void setupClickListeners() {
        // 点库查看卡片点击事件
        View cardPointLibrary = findViewById(R.id.card_point_library);
        if (cardPointLibrary != null) {
            cardPointLibrary.setOnClickListener(v -> {
                Intent intent = new Intent(this, PointLibraryActivity.class);
                intent.putExtra("from_point_measure_menu", true);
                startActivity(intent);
            });
        }

        // 开始测量卡片点击事件
        View cardStartMeasure = findViewById(R.id.card_start_measure);
        if (cardStartMeasure != null) {
            cardStartMeasure.setOnClickListener(v -> {
                Intent intent = new Intent(this, PointMeasureMainActivity.class);
                intent.putExtra("from_point_measure_menu", true);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 立即强制隐藏系统UI，防止任何闪烁
        forceHideSystemUI();

        // 立即接管系统UI控制，防止BaseActivity的沉浸式模式干扰
        getWindow().getDecorView().post(() -> {
            // 覆盖BaseActivity的沉浸式模式设置
            forceHideSystemUI();
            enableImmersiveMode();
        });

        // 多重延迟执行，确保系统UI保持隐藏状态
        getWindow().getDecorView().post(() -> {
            enableImmersiveMode();
        });

        getWindow().getDecorView().postDelayed(() -> {
            enableImmersiveMode();
        }, 50);

        getWindow().getDecorView().postDelayed(() -> {
            enableImmersiveMode();
        }, 150);

        // 额外的延迟执行，确保在低端设备上也能生效
        getWindow().getDecorView().postDelayed(() -> {
            if (!isImmersiveModeEnabled) {
                forceHideSystemUI();
            }
        }, 300);

        // 最后的保险措施，确保系统UI完全隐藏
        getWindow().getDecorView().postDelayed(() -> {
            forceHideSystemUI();
            enableImmersiveMode();
        }, 500);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 在 onStart 中也确保沉浸式模式
        forceHideSystemUI();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // 在 onRestart 中也确保沉浸式模式
        forceHideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 立即强制隐藏系统UI，防止任何闪烁
            forceHideSystemUI();

            // 多重延迟执行，确保系统UI保持隐藏状态
            getWindow().getDecorView().post(() -> {
                enableImmersiveMode();
            });

            getWindow().getDecorView().postDelayed(() -> {
                enableImmersiveMode();
            }, 50);

            getWindow().getDecorView().postDelayed(() -> {
                enableImmersiveMode();
            }, 150);

            // 额外的延迟执行，确保在低端设备上也能生效
            getWindow().getDecorView().postDelayed(() -> {
                if (!isImmersiveModeEnabled) {
                    forceHideSystemUI();
                }
            }, 300);

            // 最后的保险措施，确保系统UI完全隐藏
            getWindow().getDecorView().postDelayed(() -> {
                forceHideSystemUI();
                enableImmersiveMode();
            }, 500);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // 在界面创建完成后再次确保沉浸式模式
        forceHideSystemUI();
        enableImmersiveMode();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 当通过Intent重新启动Activity时，确保系统UI保持隐藏
        forceHideSystemUI();
    }
}

