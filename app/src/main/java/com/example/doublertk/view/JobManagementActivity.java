package com.example.doublertk.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.data.Job;
import com.example.doublertk.data.JobRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class JobManagementActivity extends BaseActivity {

    private EditText etJobName;
    private TextView tvJobTime;
    private Button btnSaveJob;
    private RecyclerView rvJobList;
    private TextView tvEmptyState;

    private JobListAdapter adapter;
    private JobRepository jobRepository;
    private SimpleDateFormat dateFormat;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_job_management;
    }

    @Override
    protected void initView() {
        setTopBarTitle("作业管理");

        initViews();
        initData();
        setupListeners();
        updateJobTime();
        loadJobs();
    }

    private void initViews() {
        etJobName = findViewById(R.id.et_job_name);
        tvJobTime = findViewById(R.id.tv_job_time);
        btnSaveJob = findViewById(R.id.btn_save_job);
        rvJobList = findViewById(R.id.rv_job_list);
        tvEmptyState = findViewById(R.id.tv_empty_state);

        // 设置RecyclerView
        rvJobList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JobListAdapter(this);
        rvJobList.setAdapter(adapter);
    }

    private void initData() {
        jobRepository = new JobRepository(this);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    private void setupListeners() {
        // 保存作业按钮
        btnSaveJob.setOnClickListener(v -> saveJob());

        // 适配器管理按钮点击监听
        adapter.setOnManageClickListener((job, position, anchorView) -> {
            showManageOptionsDialog(job, position);
        });
    }

    /**
     * 更新作业时间显示（自动获取系统时间）
     */
    private void updateJobTime() {
        String currentTime = dateFormat.format(new Date());
        tvJobTime.setText(currentTime);
    }

    /**
     * 保存作业
     */
    private void saveJob() {
        String jobName = etJobName.getText().toString().trim();
        
        if (TextUtils.isEmpty(jobName)) {
            Toast.makeText(this, getString(R.string.job_name_error), Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建作业对象，时间自动使用当前系统时间
        Job job = new Job(jobName);
        job.setCreatedAt(System.currentTimeMillis());

        // 在后台线程保存
        new Thread(() -> {
            try {
                Future<Long> future = jobRepository.insertJob(job);
                Long jobId = future.get();
                
                runOnUiThread(() -> {
                    if (jobId > 0) {
                        Toast.makeText(this, getString(R.string.job_save_success), Toast.LENGTH_SHORT).show();
                        // 清空输入框
                        etJobName.setText("");
                        // 更新时间显示
                        updateJobTime();
                        // 刷新列表（通过观察者自动更新）
                    } else {
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 加载作业列表
     */
    private void loadJobs() {
        // 使用LiveData观察作业列表变化
        jobRepository.observeAllJobs().observe(this, new Observer<List<Job>>() {
            @Override
            public void onChanged(List<Job> jobs) {
                if (jobs != null && !jobs.isEmpty()) {
                    adapter.updateData(jobs);
                    rvJobList.setVisibility(View.VISIBLE);
                    tvEmptyState.setVisibility(View.GONE);
                } else {
                    rvJobList.setVisibility(View.GONE);
                    tvEmptyState.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * 显示管理选项对话框（编辑/删除）
     */
    private void showManageOptionsDialog(Job job, int position) {
        String[] options = {
            getString(R.string.job_action_modify),
            getString(R.string.job_action_delete)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.job_action_title));
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // 编辑
                showEditJobDialog(job, position);
            } else if (which == 1) {
                // 删除
                showDeleteConfirmDialog(job, position);
            }
        });
        builder.setNegativeButton(getString(R.string.job_cancel), null);
        builder.show();
    }

    /**
     * 显示编辑作业对话框
     */
    private void showEditJobDialog(Job job, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.job_edit_title));

        // 创建输入框
        final EditText input = new EditText(this);
        input.setHint(getString(R.string.job_name_hint));
        input.setText(job.getName());
        input.setSelectAllOnFocus(true);
        
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.job_edit_confirm), (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, getString(R.string.job_name_error), Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 更新作业名称
            job.setName(newName);
            updateJob(job, position);
        });
        builder.setNegativeButton(getString(R.string.job_cancel), null);
        builder.show();
    }

    /**
     * 更新作业
     */
    private void updateJob(Job job, int position) {
        new Thread(() -> {
            try {
                Future<Integer> future = jobRepository.updateJob(job);
                Integer result = future.get();
                
                runOnUiThread(() -> {
                    if (result > 0) {
                        Toast.makeText(this, "作业已更新", Toast.LENGTH_SHORT).show();
                        // 列表会自动更新（通过LiveData观察者）
                    } else {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(Job job, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.job_delete_title));
        builder.setMessage(getString(R.string.job_delete_message, job.getName()));
        builder.setPositiveButton(getString(R.string.job_delete_confirm), (dialog, which) -> {
            deleteJob(job, position);
        });
        builder.setNegativeButton(getString(R.string.job_cancel), null);
        builder.show();
    }

    /**
     * 删除作业
     */
    private void deleteJob(Job job, int position) {
        new Thread(() -> {
            try {
                Future<Void> future = jobRepository.deleteJob(job);
                future.get();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "作业已删除", Toast.LENGTH_SHORT).show();
                    // 列表会自动更新（通过LiveData观察者）
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}

