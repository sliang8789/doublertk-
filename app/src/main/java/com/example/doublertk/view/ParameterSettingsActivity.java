package com.example.doublertk.view;

import android.os.Bundle;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.google.android.material.tabs.TabLayout;

public class ParameterSettingsActivity extends BaseActivity {

	@Override
	protected int getLayoutId() {
		// 若你的项目已有完整的参数设置布局，则使用该布局
		// 否则先使用一个简单占位布局 activity_empty
		int layoutId;
		try {
			layoutId = R.layout.activity_parameter_settings;
		} catch (Exception e) {
			layoutId = R.layout.activity_empty;
		}
		return layoutId;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 读取模式参数并做基础展示，后续可扩展实际编辑逻辑
		String mode = getIntent() != null ? getIntent().getStringExtra("mode") : null;
		if (mode == null) mode = "create";

		// 设置顶部导航栏标题（使用父类提供的方法）
		if ("create".equals(mode)) {
			setTopBarTitle("新建坐标系");
		} else if ("edit".equals(mode)) {
			setTopBarTitle("编辑坐标系");
		} else {
			setTopBarTitle("参数设置");
		}

		// Tab: “点校正” → 跳转到点校正界面（layout_point_correction）
		try {
			TabLayout tabLayout = findViewById(R.id.tab_layout);
			if (tabLayout != null) {
				tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
					@Override
					public void onTabSelected(TabLayout.Tab tab) {
						CharSequence text = tab.getText();
						if (text != null && "点校正".contentEquals(text)) {
							android.content.Intent intent = new android.content.Intent(ParameterSettingsActivity.this, PointCorrectionActivity.class);
							startActivity(intent);
							// 选中后切回第一个标签，避免返回时位置错乱
							tabLayout.selectTab(tabLayout.getTabAt(0));
						}
					}
					@Override public void onTabUnselected(TabLayout.Tab tab) {}
					@Override public void onTabReselected(TabLayout.Tab tab) {}
				});
			}
		} catch (Exception ignored) {
		}
	}
}

