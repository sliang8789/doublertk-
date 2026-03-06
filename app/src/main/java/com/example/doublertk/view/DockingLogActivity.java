package com.example.doublertk.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.data.DockingLog;
import com.example.doublertk.data.DockingLogRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class DockingLogActivity extends BaseActivity {

    private TextView tvTotalCount;
    private TextView tvSuccessCount;
    private TextView tvSuccessRate;
    private TextView tvAvgDuration;
    private TextView tvAvgError;
    private TextView tvEmptyHint;

    private Button btnFilterAll;
    private Button btnFilterSuccess;
    private Button btnFilterFailed;

    private RecyclerView recyclerView;
    private DockingLogAdapter adapter;

    private DockingLogRepository repository;
    private List<DockingLog> allLogs = new ArrayList<>();
    private String currentFilter = "ALL";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_docking_log;
    }

    @Override
    protected void initView() {
        setTopBarTitle("停靠日志");

        repository = new DockingLogRepository(this);

        tvTotalCount = findViewById(R.id.tv_total_count);
        tvSuccessCount = findViewById(R.id.tv_success_count);
        tvSuccessRate = findViewById(R.id.tv_success_rate);
        tvAvgDuration = findViewById(R.id.tv_avg_duration);
        tvAvgError = findViewById(R.id.tv_avg_error);
        tvEmptyHint = findViewById(R.id.tv_empty_hint);

        btnFilterAll = findViewById(R.id.btn_filter_all);
        btnFilterSuccess = findViewById(R.id.btn_filter_success);
        btnFilterFailed = findViewById(R.id.btn_filter_failed);

        recyclerView = findViewById(R.id.recycler_view_logs);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DockingLogAdapter(this);
        recyclerView.setAdapter(adapter);

        btnFilterAll.setOnClickListener(v -> filterLogs("ALL"));
        btnFilterSuccess.setOnClickListener(v -> filterLogs("SUCCESS"));
        btnFilterFailed.setOnClickListener(v -> filterLogs("FAILED"));

        loadStatistics();
        loadLogs();
    }

    private void loadStatistics() {
        new Thread(() -> {
            try {
                Future<DockingLogRepository.DockingStatistics> future = repository.getStatistics();
                DockingLogRepository.DockingStatistics stats = future.get();

                runOnUiThread(() -> {
                    if (stats != null) {
                        tvTotalCount.setText(String.valueOf(stats.totalCount));
                        tvSuccessCount.setText(String.valueOf(stats.successCount));
                        tvSuccessRate.setText(stats.getFormattedSuccessRate());
                        tvAvgDuration.setText(stats.getFormattedAvgDuration());

                        double avgError = (stats.avgBowError + stats.avgSternError) / 2;
                        tvAvgError.setText(String.format("%.2fm", avgError));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "加载统计信息失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadLogs() {
        new Thread(() -> {
            try {
                Future<List<DockingLog>> future = repository.getAllLogs();
                List<DockingLog> logs = future.get();

                runOnUiThread(() -> {
                    if (logs != null && !logs.isEmpty()) {
                        allLogs = logs;
                        filterLogs(currentFilter);
                        tvEmptyHint.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    } else {
                        tvEmptyHint.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载日志失败", Toast.LENGTH_SHORT).show();
                    tvEmptyHint.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void filterLogs(String filter) {
        currentFilter = filter;

        btnFilterAll.setSelected(filter.equals("ALL"));
        btnFilterSuccess.setSelected(filter.equals("SUCCESS"));
        btnFilterFailed.setSelected(filter.equals("FAILED"));

        List<DockingLog> filteredLogs = new ArrayList<>();
        for (DockingLog log : allLogs) {
            if (filter.equals("ALL")) {
                filteredLogs.add(log);
            } else if (filter.equals("SUCCESS") && log.isSuccess()) {
                filteredLogs.add(log);
            } else if (filter.equals("FAILED") && !log.isSuccess() && !log.getStatus().equals("IN_PROGRESS")) {
                filteredLogs.add(log);
            }
        }

        adapter.setLogs(filteredLogs);

        if (filteredLogs.isEmpty()) {
            tvEmptyHint.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyHint.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
        loadLogs();
    }
}
