package com.example.doublertk.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.adapter.JobTableAdapter;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.model.JobItem;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 作业管理Activity
 */
public class JobManagementActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private JobTableAdapter adapter;
    private List<JobItem> jobList;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_job_management;
    }

    @Override
    protected void initView() {
        // 设置标题
        TextView titleView = findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText("作业管理");
        }

        // 初始化作业列表
        jobList = new ArrayList<>();
        // 添加示例数据
        jobList.add(new JobItem("作业1", getCurrentTime()));
        jobList.add(new JobItem("作业2", getCurrentTime()));
        jobList.add(new JobItem("作业3", getCurrentTime()));

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recycler_view_job_table);
        if (recyclerView != null) {
            adapter = new JobTableAdapter(jobList);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
            
            // 设置点击监听器
            setupAdapterListeners();
        }
    }

    /**
     * 设置适配器监听器
     */
    private void setupAdapterListeners() {
        // 编辑按钮点击事件 - 显示操作菜单
        adapter.setOnEditClickListener(position -> {
            showActionMenuDialog(position);
        });
    }

    /**
     * 显示操作菜单对话框
     */
    private void showActionMenuDialog(int position) {
        String[] options = {"添加", "修改", "删除"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择操作");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 添加
                    showAddDialog();
                    break;
                case 1: // 修改
                    if (position >= 0 && position < jobList.size()) {
                        showEditDialog(position);
                    }
                    break;
                case 2: // 删除
                    if (position >= 0 && position < jobList.size()) {
                        showDeleteConfirmDialog(position);
                    }
                    break;
            }
        });
        builder.show();
    }

    /**
     * 显示添加对话框
     */
    private void showAddDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_job_edit, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextInputLayout tilJobName = dialogView.findViewById(R.id.til_job_name);
        TextInputEditText etJobName = dialogView.findViewById(R.id.et_job_name);
        TextView tvTime = dialogView.findViewById(R.id.tv_dialog_time);

        tvTitle.setText("添加作业");
        tvTime.setText(getCurrentTime());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("确定", (d, which) -> {
                    String jobName = etJobName.getText() != null ? etJobName.getText().toString().trim() : "";
                    if (!TextUtils.isEmpty(jobName)) {
                        JobItem newJob = new JobItem(jobName, getCurrentTime());
                        adapter.addJob(newJob);
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.show();
    }

    /**
     * 显示编辑对话框
     */
    private void showEditDialog(int position) {
        if (position < 0 || position >= jobList.size()) {
            return;
        }

        JobItem job = jobList.get(position);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_job_edit, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextInputLayout tilJobName = dialogView.findViewById(R.id.til_job_name);
        TextInputEditText etJobName = dialogView.findViewById(R.id.et_job_name);
        TextView tvTime = dialogView.findViewById(R.id.tv_dialog_time);

        tvTitle.setText("修改作业");
        etJobName.setText(job.getJobName());
        tvTime.setText(getCurrentTime());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("确定", (d, which) -> {
                    String jobName = etJobName.getText() != null ? etJobName.getText().toString().trim() : "";
                    if (!TextUtils.isEmpty(jobName)) {
                        JobItem updatedJob = new JobItem(jobName, getCurrentTime());
                        adapter.updateJob(position, updatedJob);
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.show();
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(int position) {
        if (position < 0 || position >= jobList.size()) {
            return;
        }

        JobItem job = jobList.get(position);
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除作业 \"" + job.getJobName() + "\" 吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    adapter.removeJob(position);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 获取当前时间（格式化字符串）
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}

