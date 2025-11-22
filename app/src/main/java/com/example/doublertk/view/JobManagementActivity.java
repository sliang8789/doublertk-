package com.example.doublertk.view;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.adapter.JobListAdapter;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.data.Job;
import com.example.doublertk.data.JobRepository;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * 作业管理界面
 * 包含两个功能：新建作业和作业管理
 */
public class JobManagementActivity extends BaseActivity {

    private TextInputEditText etJobName;
    private TextView tvJobTime;
    private Button btnSaveJob;
    private RecyclerView rvJobList;
    private TextView tvEmptyState;

    private JobListAdapter adapter;
    private JobRepository jobRepository;
    private ExecutorService executorService;
    private List<Job> jobs;
    private SimpleDateFormat dateFormat;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_job_management;
    }

    @Override
    protected void initView() {
        setTopBarTitle(R.string.job_management_title);

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
        jobRepository = new JobRepository(getApplication());
        executorService = Executors.newSingleThreadExecutor();
        jobs = new ArrayList<>();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    private void setupListeners() {
        // 保存作业按钮
        btnSaveJob.setOnClickListener(v -> saveJob());

        // 作业名称输入监听
        etJobName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 更新作业时间（实时）
                updateJobTime();
            }
        });

        // 适配器监听器
        adapter.setOnItemClickListener((job, position) -> {
            // 点击作业项，可以显示详情或进行其他操作
            Toast.makeText(this, "作业: " + job.getName(), Toast.LENGTH_SHORT).show();
        });

        adapter.setOnEditClickListener((job, position) -> {
            // 编辑作业
            editJob(job, position);
        });

        adapter.setOnDeleteClickListener((job, position) -> {
            // 删除作业
            showDeleteConfirmDialog(job, position);
        });
    }

    /**
     * 更新作业时间显示
     */
    private void updateJobTime() {
        String timeStr = dateFormat.format(new Date());
        tvJobTime.setText(timeStr);
    }

    /**
     * 保存作业
     */
    private void saveJob() {
        String jobName = etJobName.getText() != null ? etJobName.getText().toString().trim() : "";

        if (jobName.isEmpty()) {
            Toast.makeText(this, R.string.job_name_error, Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查名称是否已存在
        executorService.execute(() -> {
            boolean exists = jobRepository.isNameExists(jobName);
            runOnUiThread(() -> {
                if (exists) {
                    Toast.makeText(this, "作业名称已存在，请使用其他名称", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 创建新作业
                Job job = new Job(jobName);
                long id = jobRepository.insertAndReturnId(job);

                if (id > 0) {
                    Toast.makeText(this, R.string.job_save_success, Toast.LENGTH_SHORT).show();
                    // 清空输入框
                    etJobName.setText("");
                    // 重新加载作业列表
                    loadJobs();
                } else {
                    Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * 加载作业列表
     */
    private void loadJobs() {
        executorService.execute(() -> {
            List<Job> jobList = jobRepository.getAllSync();
            runOnUiThread(() -> {
                if (jobList != null) {
                    jobs.clear();
                    jobs.addAll(jobList);
                    adapter.updateData(jobs);

                    // 显示/隐藏空状态
                    if (jobs.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        rvJobList.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        rvJobList.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    /**
     * 编辑作业
     */
    private void editJob(Job job, int position) {
        // 创建编辑对话框
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_job, null);
        EditText etEditJobName = dialogView.findViewById(R.id.et_edit_job_name);
        etEditJobName.setText(job.getName());
        etEditJobName.setSelection(job.getName().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.job_edit_title)
                .setView(dialogView)
                .setPositiveButton(R.string.job_edit_confirm, (d, which) -> {
                    String newName = etEditJobName.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, R.string.job_name_error, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 检查名称是否已存在（排除当前作业）
                    executorService.execute(() -> {
                        boolean exists = jobRepository.isNameExistsExcludingId(newName, job.getId());
                        runOnUiThread(() -> {
                            if (exists) {
                                Toast.makeText(this, "作业名称已存在，请使用其他名称", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // 更新作业
                            job.setName(newName);
                            boolean success = jobRepository.updateAndReturnResult(job);

                            if (success) {
                                Toast.makeText(this, "作业已更新", Toast.LENGTH_SHORT).show();
                                // 更新适配器
                                adapter.updateJob(position, job);
                            } else {
                                Toast.makeText(this, "更新失败，请重试", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.job_cancel, null)
                .create();

        dialog.show();
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(Job job, int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.job_delete_title)
                .setMessage(getString(R.string.job_delete_message, job.getName()))
                .setPositiveButton(R.string.job_delete_confirm, (dialog, which) -> {
                    // 删除作业
                    executorService.execute(() -> {
                        jobRepository.delete(job);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "作业已删除", Toast.LENGTH_SHORT).show();
                            // 从列表中移除
                            adapter.removeJob(position);
                            // 如果列表为空，显示空状态
                            if (adapter.getItemCount() == 0) {
                                tvEmptyState.setVisibility(View.VISIBLE);
                                rvJobList.setVisibility(View.GONE);
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.job_cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次显示时刷新列表
        loadJobs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

