package com.example.doublertk.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.R;

/**
 * 点测量页面（重定向到菜单）
 */
public class PointMeasureActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_point_measure_menu;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 重定向到菜单界面
        Intent intent = new Intent(this, PointMeasureMenuActivity.class);
        startActivity(intent);
        finish();
    }
}


