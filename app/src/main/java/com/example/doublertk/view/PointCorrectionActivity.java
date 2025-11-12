package com.example.doublertk.view;

import android.os.Bundle;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

public class PointCorrectionActivity extends BaseActivity {

	@Override
	protected int getLayoutId() {
		return R.layout.layout_point_correction;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 设置顶部导航栏标题（使用父类提供的方法）
		setTopBarTitle("点校正");
	}
}

