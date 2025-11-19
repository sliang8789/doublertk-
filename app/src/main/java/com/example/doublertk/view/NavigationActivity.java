package com.example.doublertk.view;

import android.content.Intent;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.google.android.material.card.MaterialCardView;

public class NavigationActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_navigation;
    }

    @Override
    protected void initView() {
        // 为每个卡片添加点击事件
        setupCardClickListeners();
    }

    /**
     * 导航界面不需要设置按钮功能，因为已经在导航界面了
     */
    @Override
    protected void onSettingsButtonClicked() {
        // 在导航界面，设置按钮不做任何操作
        // 或者可以显示一个提示
    }

    /**
     * 为所有卡片设置点击监听器
     */
    private void setupCardClickListeners() {
        // 坐标系统卡片
        MaterialCardView cardCoordinateSystem = findViewById(R.id.card_coordinate_system);
        if (cardCoordinateSystem != null) {
            cardCoordinateSystem.setOnClickListener(v -> {
					Intent intent = new Intent(NavigationActivity.this, ParameterListActivity.class);
                startActivity(intent);
            });
        }

        // 作业管理卡片 - 已删除，跳转到空页面
        MaterialCardView cardJobManagement = findViewById(R.id.card_job_management);
        if (cardJobManagement != null) {
            cardJobManagement.setOnClickListener(v -> {
                Intent intent = new Intent(NavigationActivity.this, JobManagementActivity.class);
                startActivity(intent);
            });
        }

        // 开始引导卡片
        MaterialCardView cardGuide = findViewById(R.id.card_guide);
        if (cardGuide != null) {
            cardGuide.setOnClickListener(v -> {
                Intent intent = new Intent(NavigationActivity.this, StartGuideActivity.class);
                startActivity(intent);
            });
        }

        // 点测量卡片
        MaterialCardView cardPointMeasurement = findViewById(R.id.card_point_measurement);
        if (cardPointMeasurement != null) {
            cardPointMeasurement.setOnClickListener(v -> {
                Intent intent = new Intent(NavigationActivity.this, PointMeasureMenuActivity.class);
                startActivity(intent);
            });
        }

        // 电台差分卡片
        MaterialCardView cardRadioDifferential = findViewById(R.id.card_radio_differential);
        if (cardRadioDifferential != null) {
            cardRadioDifferential.setOnClickListener(v -> {
                Intent intent = new Intent(NavigationActivity.this, RadioDifferentialActivity.class);
                startActivity(intent);
            });
        }

        // 设置卡片
        MaterialCardView cardSettings = findViewById(R.id.card_settings);
        if (cardSettings != null) {
            cardSettings.setOnClickListener(v -> {
                Intent intent = new Intent(NavigationActivity.this, SettingsActivity.class);
                startActivity(intent);
            });
        }
    }
}

