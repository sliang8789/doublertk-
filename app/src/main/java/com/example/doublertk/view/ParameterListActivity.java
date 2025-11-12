package com.example.doublertk.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.view.ParameterSystemAdapter;
import com.example.doublertk.data.CoordinateSystem;
import com.example.doublertk.data.CoordinateSystemManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 参数列表界面
 * 显示所有已创建的坐标系，支持选择、删除、导出等操作
 * TODO: 需要实现 CoordinateSystem 和 CoordinateSystemManager 类后才能使用
 */
public class ParameterListActivity extends BaseActivity {

    private RecyclerView rvParameterList;
    private Button btnDelete, btnAdd, btnExport, btnApply;

    private ParameterSystemAdapter adapter;
    private CoordinateSystemManager coordinateSystemManager;
    private ExecutorService executorService;

    private List<CoordinateSystem> coordinateSystems;
    private List<Integer> knownPointCounts;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_parameter_list;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initViews();
        initData();
        setupListeners();
        loadCoordinateSystems();
    }

    private void initViews() {
        // 设置顶部导航栏标题（使用父类提供的方法）
        setTopBarTitle("坐标系统");

        rvParameterList = findViewById(R.id.rv_parameter_list);
        btnDelete = findViewById(R.id.btn_delete);
        btnAdd = findViewById(R.id.btn_add);
//        btnExport = findViewById(R.id.btn_export);
        btnApply = findViewById(R.id.btn_apply);

        // 设置RecyclerView
        rvParameterList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParameterSystemAdapter(this);
        rvParameterList.setAdapter(adapter);
    }

    private void initData() {
        coordinateSystemManager = new CoordinateSystemManager(this);
        executorService = Executors.newSingleThreadExecutor();
        coordinateSystems = new ArrayList<>();
        knownPointCounts = new ArrayList<>();
    }

    private void setupListeners() {
        // 设置适配器监听器
        adapter.setOnItemClickListener((coordinateSystem, position) -> {
            // 选中坐标系
            updateButtonStates();
        });

        adapter.setOnItemLongClickListener((coordinateSystem, position) -> {
            // 长按显示详细信息
            showSystemDetails(coordinateSystem);
        });

        adapter.setOnMoreOptionsClickListener((coordinateSystem, position, anchorView) -> {
            // 直接进入编辑模式
            editCoordinateSystem(coordinateSystem);
        });

        // 删除按钮
        btnDelete.setOnClickListener(v -> {
            CoordinateSystem selectedSystem = adapter.getSelectedSystem();
            if (selectedSystem != null) {
                showDeleteConfirmDialog(selectedSystem);
            } else {
                Toast.makeText(this, "请先选择要删除的坐标系", Toast.LENGTH_SHORT).show();
            }
        });

        // 新增按钮
        btnAdd.setOnClickListener(v -> {
			// 跳转到参数设置界面，进入新增模式
			Intent intent = new Intent(this, ParameterSettingsActivity.class);
			intent.putExtra("mode", "create");
			startActivity(intent);
        });

        // 导出按钮
//        btnExport.setOnClickListener(v -> {
//            CoordinateSystem selectedSystem = adapter.getSelectedSystem();
//            if (selectedSystem != null) {
//                exportCoordinateSystem(selectedSystem);
//            } else {
//                Toast.makeText(this, "请先选择要导出的坐标系", Toast.LENGTH_SHORT).show();
//            }
//        });

        // 应用按钮
        btnApply.setOnClickListener(v -> {
            CoordinateSystem selectedSystem = adapter.getSelectedSystem();
            if (selectedSystem != null) {
                applyCoordinateSystem(selectedSystem);
            } else {
                Toast.makeText(this, "请先选择要应用的坐标系", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 加载坐标系列表
     */
    private void loadCoordinateSystems() {
        executorService.execute(() -> {
            try {
                List<CoordinateSystem> systems = coordinateSystemManager.getAllCoordinateSystems();
                List<Integer> counts = new ArrayList<>();

                // 获取每个坐标系的已知点数量（同步统计，避免未观察的LiveData返回null）
                for (CoordinateSystem system : systems) {
                    int count = coordinateSystemManager.getKnownPointCount(system.getId());
                    counts.add(count);
                }

                // 获取当前使用的坐标系ID
                CoordinateSystem currentSystem = coordinateSystemManager.getCurrentCoordinateSystem();
                long currentSystemId = currentSystem != null ? currentSystem.getId() : -1;

                runOnUiThread(() -> {
                    coordinateSystems.clear();
                    coordinateSystems.addAll(systems);
                    knownPointCounts.clear();
                    knownPointCounts.addAll(counts);

                    adapter.updateData(systems, counts);
                    adapter.setCurrentSystemId(currentSystemId);
                    updateButtonStates();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载坐标系列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        boolean hasSelection = adapter.getSelectedSystem() != null;
        btnDelete.setEnabled(hasSelection);
//        btnExport.setEnabled(hasSelection);
        btnApply.setEnabled(hasSelection);

        // 更新按钮样式
        btnDelete.setAlpha(hasSelection ? 1.0f : 0.5f);
//        btnExport.setAlpha(hasSelection ? 1.0f : 0.5f);
        btnApply.setAlpha(hasSelection ? 1.0f : 0.5f);
    }

    /**
     * 显示坐标系详细信息
     */
    private void showSystemDetails(CoordinateSystem system) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("坐标系详细信息");

        StringBuilder details = new StringBuilder();
        details.append("名称: ").append(system.getName()).append("\n\n");
        details.append("椭球类型: ").append(system.getEllipsoidName()).append("\n");
        details.append("投影类型: ").append(system.getProjectionName()).append("\n\n");
        details.append("长半轴: ").append(system.getSemiMajorAxis()).append(" m\n");
        details.append("扁率倒数: ").append(system.getInverseFlattening()).append("\n");
        details.append("中央子午线: ").append(system.getCentralMeridian()).append("°\n");
        details.append("比例因子: ").append(system.getScaleFactor()).append("\n");
        details.append("东偏移: ").append(system.getFalseEasting()).append(" m\n");
        details.append("北偏移: ").append(system.getFalseNorthing()).append(" m\n\n");

        if (system.isUtmProjection()) {
            details.append("UTM带号: ").append(system.getUtmZone()).append("\n");
            details.append("半球: ").append(system.getHemisphere()).append("\n\n");
        }

        details.append("基准转换: ");
        switch (system.getDatumTransform()) {
            case 0: details.append("无参数"); break;
            case 1: details.append("七参数"); break;
        }
        details.append("\n");

        details.append("平面校正: ");
        switch (system.getPlaneCorrection()) {
            case 0: details.append("无参数"); break;
            case 1: details.append("三参数"); break;
            case 2: details.append("四参数"); break;
            case 3: details.append("平面平差"); break;
        }
        details.append("\n");

        details.append("高程拟合: ");
        switch (system.getHeightFit()) {
            case 0: details.append("平面拟合"); break;
            case 1: details.append("曲面拟合"); break;
            case 2: details.append("加权平均"); break;
            case 3: details.append("垂直平差"); break;
        }

        builder.setMessage(details.toString());
        builder.setPositiveButton("确定", null);
        builder.show();
    }



    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(CoordinateSystem system) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除");
        builder.setMessage("确定要删除坐标系 \"" + system.getName() + "\" 吗？\n\n删除后将同时删除该坐标系下的所有已知点数据，此操作不可恢复。");
        builder.setPositiveButton("删除", (dialog, which) -> {
            deleteCoordinateSystem(system);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 删除坐标系
     */
    private void deleteCoordinateSystem(CoordinateSystem system) {
        executorService.execute(() -> {
            try {
                boolean success = coordinateSystemManager.deleteCoordinateSystem(system.getName());

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "坐标系已删除", Toast.LENGTH_SHORT).show();
                        loadCoordinateSystems(); // 重新加载列表
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 编辑坐标系
     */
    private void editCoordinateSystem(CoordinateSystem system) {
        // TODO: 跳转到参数设置界面
        // Intent intent = new Intent(this, ParameterSettingsActivity.class);
        // intent.putExtra("mode", "edit");
        // intent.putExtra("system_id", system.getId());
        // startActivity(intent);
        Toast.makeText(this, "参数设置功能待实现", Toast.LENGTH_SHORT).show();
    }

    /**
     * 应用坐标系
     */
    private void applyCoordinateSystem(CoordinateSystem system) {
        android.util.Log.d("ParameterListActivity", "开始应用坐标系: " + system.getName() + " (ID: " + system.getId() + ")");
        executorService.execute(() -> {
            try {
                boolean success = coordinateSystemManager.setCurrentCoordinateSystemById(system.getId());
                android.util.Log.d("ParameterListActivity", "应用坐标系结果: " + (success ? "成功" : "失败"));

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "坐标系 \"" + system.getName() + "\" 已应用", Toast.LENGTH_SHORT).show();
                        android.util.Log.d("ParameterListActivity", "坐标系应用成功");

                        // 返回结果给调用者
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("selected_system_id", system.getId());
                        resultIntent.putExtra("selected_system_name", system.getName());
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } else {
                        Toast.makeText(this, "应用失败", Toast.LENGTH_SHORT).show();
                        android.util.Log.e("ParameterListActivity", "坐标系应用失败");
                    }
                });

            } catch (Exception e) {
                android.util.Log.e("ParameterListActivity", "应用坐标系时发生异常: " + e.getMessage(), e);
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "应用失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从其他界面返回时重新加载数据
        loadCoordinateSystems();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}