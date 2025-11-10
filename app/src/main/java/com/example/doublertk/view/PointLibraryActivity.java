package com.example.doublertk.view;

import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

/**
 * 点库查看Activity
 */
public class PointLibraryActivity extends BaseActivity {

    private ListView listView;
    private TextView titleView;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_point_library;
    }

    @Override
    protected void initView() {
        // 设置标题
        View headerView = findViewById(R.id.header);
        if (headerView != null) {
            titleView = headerView.findViewById(R.id.title);
            if (titleView != null) {
                titleView.setText("点库查看");
            }
        }

        // 初始化列表视图
        listView = findViewById(R.id.list);
        if (listView != null) {
            // TODO: 设置列表适配器，显示点库数据
            // 暂时使用空适配器
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    new String[]{"点库数据待实现"}
            );
            listView.setAdapter(adapter);
        }
    }
}

