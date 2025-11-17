package com.example.doublertk.view;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.model.JobItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JobManagementActivity extends BaseActivity {

    private static final String PREFS_NAME = "job_management_prefs";
    private static final String KEY_JOBS = "jobs";

    private TextInputEditText jobNameInput;
    private TextView jobTimeValue;
    private TextView emptyPlaceholder;
    private JobListAdapter jobListAdapter;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final List<JobItem> jobItems = new ArrayList<>();
    private long currentDisplayTime;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_job_management;
    }

    @Override
    protected void initView() {
        setTopBarTitle(R.string.job_management_title);
        setupCreateSection();
        setupRecycler();
    }

    private void setupCreateSection() {
        jobNameInput = findViewById(R.id.input_job_name);
        jobTimeValue = findViewById(R.id.text_job_time_value);
        emptyPlaceholder = findViewById(R.id.text_empty_placeholder);

        MaterialButton saveJobButton = findViewById(R.id.button_save_job);

        currentDisplayTime = System.currentTimeMillis();
        updateJobTimeLabel(currentDisplayTime);
        saveJobButton.setOnClickListener(v -> saveJob());
    }

    private void setupRecycler() {
        RecyclerView recyclerView = findViewById(R.id.recycler_jobs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        jobListAdapter = new JobListAdapter();
        recyclerView.setAdapter(jobListAdapter);
        loadJobsFromStorage();
        jobListAdapter.setOnJobEditClickListener((item, position) -> showActionDialog(position));

        refreshPlaceholder();
    }

    private void updateJobTimeLabel(long timeMillis) {
        jobTimeValue.setText(timeFormat.format(timeMillis));
    }

    private void saveJob() {
        String jobName = jobNameInput.getText() != null ? jobNameInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(jobName)) {
            Toast.makeText(this, R.string.job_name_error, Toast.LENGTH_SHORT).show();
            return;
        }

        long jobTimestamp = System.currentTimeMillis();
        JobItem jobItem = new JobItem(jobName, jobTimestamp);
        jobItems.add(0, jobItem);
        jobListAdapter.addItem(jobItem);
        refreshPlaceholder();
        saveJobsToStorage();
        jobNameInput.setText("");
        currentDisplayTime = System.currentTimeMillis();
        updateJobTimeLabel(currentDisplayTime);
        Toast.makeText(this, R.string.job_save_success, Toast.LENGTH_SHORT).show();
    }

    private void showActionDialog(int position) {
        if (position < 0 || position >= jobItems.size()) return;

        String[] actions = new String[]{
                getString(R.string.job_action_modify),
                getString(R.string.job_action_delete)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.job_action_title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showEditDialog(position);
                    } else if (which == 1) {
                        showDeleteConfirmDialog(position);
                    }
                })
                .show();
    }

    private void showEditDialog(int position) {
        if (position < 0 || position >= jobItems.size()) return;
        JobItem original = jobItems.get(position);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(original.getName());
        input.setSelection(original.getName().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.job_edit_title)
                .setView(input)
                .setPositiveButton(R.string.job_edit_confirm, (dialog, which) -> {
                    String newName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(newName)) {
                        Toast.makeText(this, R.string.job_name_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JobItem updated = new JobItem(newName, original.getTimestamp());
                    jobItems.set(position, updated);
                    jobListAdapter.updateItem(position, updated);
                    saveJobsToStorage();
                })
                .setNegativeButton(R.string.job_cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog(int position) {
        if (position < 0 || position >= jobItems.size()) return;
        JobItem item = jobItems.get(position);

        new AlertDialog.Builder(this)
                .setTitle(R.string.job_delete_title)
                .setMessage(getString(R.string.job_delete_message, item.getName()))
                .setPositiveButton(R.string.job_delete_confirm, (dialog, which) -> {
                    jobItems.remove(position);
                    jobListAdapter.removeItem(position);
                    refreshPlaceholder();
                    saveJobsToStorage();
                })
                .setNegativeButton(R.string.job_cancel, null)
                .show();
    }

    private void refreshPlaceholder() {
        emptyPlaceholder.setVisibility(jobItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * 从本地存储加载历史作业
     */
    private void loadJobsFromStorage() {
        jobItems.clear();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = sp.getString(KEY_JOBS, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String name = obj.optString("name", "");
                    long timestamp = obj.optLong("timestamp", 0L);
                    if (!TextUtils.isEmpty(name) && timestamp > 0L) {
                        jobItems.add(new JobItem(name, timestamp));
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        jobListAdapter.setItems(jobItems);
        refreshPlaceholder();
    }

    /**
     * 将当前作业列表保存到本地
     */
    private void saveJobsToStorage() {
        JSONArray array = new JSONArray();
        for (JobItem item : jobItems) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", item.getName());
                obj.put("timestamp", item.getTimestamp());
            } catch (JSONException ignored) {
            }
            array.put(obj);
        }
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_JOBS, array.toString()).apply();
    }
}
