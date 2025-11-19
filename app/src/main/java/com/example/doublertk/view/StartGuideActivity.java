package com.example.doublertk.view;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

public class StartGuideActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_start_guide;
    }

    @Override
    protected void initView() {
        setTopBarTitle("开始引导");
    }
}

