package com.example.doublertk.view;

import android.os.Bundle;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

public class EmptyActivity extends BaseActivity {

    public static final String EXTRA_TITLE = "extra_title";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_empty;
    }

    @Override
    protected void initView() {
        // 获取传递的标题并设置（使用父类提供的方法）
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null && !title.isEmpty()) {
            setTopBarTitle(title);
        }
    }
}

