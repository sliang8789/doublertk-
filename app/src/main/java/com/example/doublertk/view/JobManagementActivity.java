package com.example.doublertk.view;

import android.content.Intent;
import android.content.res.AssetManager;
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
import com.example.doublertk.dwg.DwgDxfParser;
import com.example.doublertk.dwg.DxfOverlayResult;
import com.example.doublertk.dwg.EntityInfo;
import com.example.doublertk.dwg.LayerInfo;
import com.example.doublertk.dwg.ParseResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class JobManagementActivity extends BaseActivity {

    private EditText etJobName;
    private TextView tvJobTime;
    private Button btnSaveJob;
    private Button btnImportDxf;
    private TextView tvDxfPath;
    private TextView tvDxfStatus;
    private RecyclerView rvJobList;
    private TextView tvEmptyState;

    private JobListAdapter adapter;
    private JobRepository jobRepository;
    private SimpleDateFormat dateFormat;

    private String selectedDxfAssetName;
    private String selectedDxfLocalPath;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_job_management;
    }

    @Override
    protected void initView() {
        setTopBarTitle(getString(R.string.job_management_title));

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
        btnImportDxf = findViewById(R.id.btn_import_dxf);
        tvDxfPath = findViewById(R.id.tv_dxf_path);
        tvDxfStatus = findViewById(R.id.tv_dxf_status);
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

        if (btnImportDxf != null) {
            btnImportDxf.setOnClickListener(v -> openBuiltInDxfPicker());
        }

        // 适配器管理按钮点击监听
        adapter.setOnManageClickListener((job, position, anchorView) -> {
            showManageOptionsDialog(job, position);
        });
    }

    private void openBuiltInDxfPicker() {
        String[] dxfAssets;
        try {
            dxfAssets = listDxfAssets();
        } catch (IOException e) {
            Toast.makeText(this, "读取内置DXF列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (dxfAssets.length == 0) {
            Toast.makeText(this, "assets 中未找到 DXF 文件", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择内置DXF");
        builder.setItems(dxfAssets, (dialog, which) -> {
            if (which < 0 || which >= dxfAssets.length) return;
            importDxfFromAssets(dxfAssets[which]);
        });
        builder.setNegativeButton(getString(R.string.job_cancel), null);
        builder.show();
    }

     private String[] listDxfAssets() throws IOException {
         AssetManager am = getAssets();
         String[] names = am.list("");
         if (names == null) return new String[0];

         List<String> result = new ArrayList<>();
         for (String n : names) {
             if (n == null) continue;
             String lower = n.toLowerCase(Locale.ROOT);
             if (lower.endsWith(".dxf")) {
                 result.add(n);
             }
         }
         Collections.sort(result);
         return result.toArray(new String[0]);
     }

     private void importDxfFromAssets(String assetName) {
         selectedDxfAssetName = assetName;
         if (tvDxfStatus != null) tvDxfStatus.setText("正在导入: " + assetName);

         new Thread(() -> {
             try {
                 File localFile = copyAssetToCache(assetName);
                 selectedDxfLocalPath = localFile.getAbsolutePath();

                 ParseResult parseResult;
                 try (InputStream is = getAssets().open(assetName)) {
                     parseResult = DwgDxfParser.parseDxfStream(is);
                 }

                 int pointCount = 0;
                 int linkCount = 0;
                 try (InputStream is = getAssets().open(assetName)) {
                     DxfOverlayResult overlay = DwgDxfParser.parseDxfToOverlay(is);
                     pointCount = overlay.getPoints() == null ? 0 : overlay.getPoints().size();
                     linkCount = overlay.getLinks() == null ? 0 : overlay.getLinks().size();
                 }

                 String details = buildDxfDetailsText(parseResult);
                 String summary = buildDxfSummaryText(parseResult, pointCount, linkCount);

                 int finalPointCount = pointCount;
                 int finalLinkCount = linkCount;
                 runOnUiThread(() -> {
                     if (tvDxfPath != null) tvDxfPath.setText(selectedDxfLocalPath);
                     if (tvDxfStatus != null) {
                         tvDxfStatus.setText(summary);
                     }

                     AlertDialog.Builder builder = new AlertDialog.Builder(this);
                     builder.setTitle("DXF解析结果");
                     builder.setMessage(details);
                     builder.setPositiveButton("确定", null);
                     
                     // 添加"查看渲染"按钮
                     if (parseResult != null && parseResult.getEntities() != null && !parseResult.getEntities().isEmpty()) {
                         builder.setNeutralButton("查看渲染", (dialog, which) -> {
                             // 打开DXF查看器Activity
                             Intent intent = new Intent(this, DxfViewerActivity.class);
                             intent.putExtra("dxf_path", selectedDxfLocalPath);
                             startActivity(intent);
                         });
                     }
                     
                     builder.show();
                 });
             } catch (Exception e) {
                 runOnUiThread(() -> {
                     if (tvDxfStatus != null) tvDxfStatus.setText("导入失败: " + e.getMessage());
                     Toast.makeText(this, "DXF导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                 });
             }
         }).start();
     }

     private String buildDxfSummaryText(ParseResult result, int pointCount, int linkCount) {
         int layerCount = result == null || result.getLayers() == null ? 0 : result.getLayers().size();
         int entityTypeCount = uniqueEntityTypes(result).size();
         return "导入完成: 图层=" + layerCount + " 实体类型=" + entityTypeCount + " 点=" + pointCount + " 线=" + linkCount;
     }

     private String buildDxfDetailsText(ParseResult result) {
         StringBuilder sb = new StringBuilder();
         if (result == null) {
             sb.append("解析失败：result 为空");
             return sb.toString();
         }

         List<LayerInfo> layers = result.getLayers();
         Set<String> entityTypes = uniqueEntityTypes(result);

         sb.append("文件: ").append(selectedDxfAssetName == null ? "" : selectedDxfAssetName).append("\n");
         sb.append("是否识别为DXF: ").append(result.isSuccess()).append("\n\n");

         sb.append("图层 (" + (layers == null ? 0 : layers.size()) + "):\n");
         if (layers != null && !layers.isEmpty()) {
             for (LayerInfo l : layers) {
                 if (l == null) continue;
                 sb.append("- ").append(l.getName() == null ? "" : l.getName()).append("\n");
             }
         }
         sb.append("\n");

         sb.append("实体类型 (" + entityTypes.size() + "):\n");
         if (!entityTypes.isEmpty()) {
             for (String t : entityTypes) {
                 sb.append("- ").append(t).append("\n");
             }
         }

         if (!result.isSuccess() && !TextUtils.isEmpty(result.getErrorMessage())) {
             sb.append("\n错误: ").append(result.getErrorMessage());
         }

         return sb.toString();
     }

     private Set<String> uniqueEntityTypes(ParseResult result) {
         Set<String> set = new LinkedHashSet<>();
         if (result == null || result.getEntities() == null) return set;
         for (EntityInfo e : result.getEntities()) {
             if (e == null) continue;
             if (!TextUtils.isEmpty(e.getType())) {
                 set.add(e.getType());
             }
         }
         return set;
     }

     private File copyAssetToCache(String assetName) throws IOException {
         File out = new File(getCacheDir(), assetName);
         try (InputStream is = getAssets().open(assetName);
              FileOutputStream os = new FileOutputStream(out)) {
             byte[] buf = new byte[8 * 1024];
             int len;
             while ((len = is.read(buf)) != -1) {
                 os.write(buf, 0, len);
             }
             os.flush();
         }
         return out;
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

