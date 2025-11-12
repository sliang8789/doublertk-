package com.example.doublertk.view;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

public class JobManagementActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_job_management;
    }

    @Override
    protected void initView() {
        setTopBarTitle(R.string.job_management_title);
    }
}


