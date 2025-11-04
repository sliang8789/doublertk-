package com.example.doublertk.base;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * BaseActivity - 所有Activity的基类
 * 功能：
 * 1. 自动隐藏系统状态栏和导航栏
 * 2. 使用EdgeToEdge全屏布局
 */
public abstract class BaseActivity extends AppCompatActivity {

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

