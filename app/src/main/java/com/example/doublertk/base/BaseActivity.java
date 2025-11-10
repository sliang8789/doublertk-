package com.example.doublertk.base;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.doublertk.R;
import com.example.doublertk.view.NavigationActivity;

/**
 * BaseActivity - 所有Activity的基类
 * 功能：
 * 1. 自动隐藏系统状态栏和导航栏
 * 2. 使用EdgeToEdge全屏布局
 * 3. 提供通用的底部导航栏功能
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 启用EdgeToEdge全屏
        EdgeToEdge.enable(this);
        
        // 隐藏系统状态栏和导航栏
        hideSystemBars();
        
        // 调用子类的setContentView
        if (getLayoutId() != 0) {
            setContentView(getLayoutId());
        }
        
        // 设置WindowInsets监听，确保内容不被系统栏遮挡
        setupWindowInsets();
        
        // 初始化视图
        initView();
        
        // 初始化底部导航栏
        initBottomBar();
    }

    /**
     * 获取布局文件ID，子类必须实现
     * @return 布局文件ID
     */
    protected abstract int getLayoutId();

    /**
     * 初始化视图，子类可选择性重写
     */
    protected void initView() {
        // 子类可选择性实现
    }

    /**
     * 初始化底部导航栏
     * 如果布局中包含 bottom_bar，则自动初始化返回按钮和设置按钮
     */
    protected void initBottomBar() {
        // 查找底部导航栏
        View bottomBar = findViewById(R.id.bottom_bar);
        if (bottomBar == null) {
            // 底部导航栏不存在是正常的，某些页面可能不需要
            return;
        }

        // 初始化返回按钮
        ImageView backButton = bottomBar.findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                Log.d(TAG, "Back button clicked");
                onBackButtonClicked();
            });
        }

        // 初始化设置按钮
        ImageView toolSettings = bottomBar.findViewById(R.id.tool_settings);
        if (toolSettings != null) {
            toolSettings.setClickable(true);
            toolSettings.setOnClickListener(v -> {
                Log.d(TAG, "Settings button clicked");
                onSettingsButtonClicked();
            });
        }
    }

    /**
     * 返回按钮点击事件
     * 默认行为是关闭当前Activity，子类可以重写此方法自定义行为
     */
    protected void onBackButtonClicked() {
        finish();
    }

    /**
     * 设置按钮点击事件
     * 默认行为是跳转到导航界面，子类可以重写此方法自定义行为
     */
    protected void onSettingsButtonClicked() {
        try {
            Intent intent = new Intent(this, NavigationActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate to NavigationActivity", e);
        }
    }

    /**
     * 隐藏系统状态栏和导航栏
     * 实现全屏沉浸式体验
     */
    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        
        // 设置全屏标志
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // API Level 23+ 使用新的方式隐藏系统栏
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // 设置导航栏透明
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        
        // 设置状态栏透明
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    }

    /**
     * 监听视图获取焦点，如果系统栏显示则立即隐藏
     */
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // 完全全屏，padding设为0
            v.setPadding(0, 0, 0, 0);
            
            return insets;
        });
    }

    /**
     * 当Activity获得焦点时，确保系统栏保持隐藏
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }
}

