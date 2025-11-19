package com.example.doublertk.view;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

public class SettingsActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_settings;
    }

    @Override
    protected void initView() {
        setTopBarTitle("设置");
    }
}

