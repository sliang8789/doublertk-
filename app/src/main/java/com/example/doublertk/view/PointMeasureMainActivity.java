package com.example.doublertk.view;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

// TODO: 以下导入的类需要实现或替换
// import com.example.doublertk.app.GnssCore;
// import com.example.doublertk.data.CoordinateSystem;
// import com.example.doublertk.data.CoordinateSystemManager;
// import com.example.doublertk.data.KnownPointRepository;
// import com.example.doublertk.data.PointRecord;
// import com.example.doublertk.data.PointRecordRepository;
// import com.example.doublertk.location.DifferentialGps;
// import com.example.doublertk.location.InfoData;
// import com.example.doublertk.location.Location;
// import com.example.doublertk.calculate.SolidifyCalculator2;
// import com.example.doublertk.calculate.Vector3D;
// import com.example.doublertk.calculate.GaussKrugerCGCS2000;
// import com.example.doublertk.calculate.BJ54GaussKruger;
// import com.example.doublertk.calculate.Xian80GaussKruger;
// import com.example.doublertk.calculate.WGS84ToUTM;
// import com.example.doublertk.utils.WXTextToSpeech;

import java.util.List;

/**
 * 点测量主界面（实际测量页面）
 */
public class PointMeasureMainActivity extends BaseActivity {

    private android.widget.TextView tvNorth, tvEast, tvHeight;
    private android.widget.TextView tvLat, tvLon, tvHgt;
    private Spinner spinnerMeasureMode;
    // TODO: 以下字段需要对应的类实现
    // private KnownPointRepository knownPointRepository;
    // private CoordinateSystemManager coordinateSystemManager;
    private Handler updateHandler;
    private Runnable updateRunnable;
    // private SolidifyCalculator2 solidifyCalculator;
    // private WXTextToSpeech wxTextToSpeech;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_point_measure_main;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: 绑定头部栏（如果需要）
        // rebindHeaderViews();
        
        // TODO: 初始化语音播报并播报"开始测量"
        // wxTextToSpeech = new WXTextToSpeech(this);
        // wxTextToSpeech.speakWhenReady("开始测量");

        // TODO: init repos（这些类需要实现）
        // knownPointRepository = new KnownPointRepository(getApplication());
        // coordinateSystemManager = new CoordinateSystemManager(this);

        tvNorth = findViewById(R.id.tv_north);
        tvEast = findViewById(R.id.tv_east);
        tvHeight = findViewById(R.id.tv_height);
        tvLat = findViewById(R.id.tv_lat);
        tvLon = findViewById(R.id.tv_lon);
        tvHgt = findViewById(R.id.tv_hgt);
        spinnerMeasureMode = findViewById(R.id.spinner_measure_mode);

        // 初始化测量方式下拉选择器
        initMeasureModeSpinner();

        // 采点按钮：弹出输入点名对话框
        Button btnCollect = findViewById(R.id.btn_collect);
        btnCollect.setOnClickListener(v -> {
            showPointNameInputDialog();
        });

        // 杆高设置入口
        Button btnPole = findViewById(R.id.btn_pole_height);
        if (btnPole != null) {
            btnPole.setOnClickListener(v -> {
                showPoleHeightDialog();
            });
        }

        // TODO: 初始化定时更新（需要相关类实现）
        // initPeriodicUpdate();

        // TODO: 首次刷新显示（需要相关类实现）
        // updateTopBar();
        
        // TODO: 加载历史测量点（需要相关类实现）
        // loadHistoricalPoints();
    }

    /**
     * 加载历史测量点并显示在平面网格上
     * 按时间排序，将最早的点作为坐标轴原点
     * TODO: 需要 PointRecordRepository 等类实现
     */
    private void loadHistoricalPoints() {
        // TODO: 需要 PointRecordRepository 等类实现后才能使用
        Log.d("PointMeasure", "loadHistoricalPoints: 功能待实现，需要相关数据类支持");
    }

    /**
     * 初始化测量方式下拉选择器
     */
    private void initMeasureModeSpinner() {
        String[] modes = {"对中杆测量模式"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMeasureMode.setAdapter(adapter);
        spinnerMeasureMode.setSelection(0);
        spinnerMeasureMode.setEnabled(false);
        spinnerMeasureMode.setClickable(false);
        
        // 保存默认测量方式
        SharedPreferences sp = getSharedPreferences("measure_prefs", MODE_PRIVATE);
        sp.edit().putString("mode", "rod").apply();
    }

    /**
     * 显示点名输入对话框
     */
    private void showPointNameInputDialog() {
        final EditText input = new EditText(this);
        input.setHint("请输入点名");
        
        new AlertDialog.Builder(this)
                .setTitle("输入点名")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String pointName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (pointName.isEmpty()) {
                        pointName = "点-" + new java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(new java.util.Date());
                    }
                    collectPointWithName(pointName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示杆高设置对话框
     */
    private void showPoleHeightDialog() {
        try {
            final EditText input = new EditText(this);
            input.setHint("1.500");
            // 预填上次保存值
            SharedPreferences sp = getSharedPreferences("pole_prefs", MODE_PRIVATE);
            String last = sp.getString("vertical_height", "1.500");
            input.setText(last);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("设置竖直杆高 (m)")
                    .setView(input)
                    .setPositiveButton("确定", (d, which) -> {
                        String val = input.getText() != null ? input.getText().toString().trim() : "";
                        if (val.isEmpty()) val = "1.500";
                        double parsed;
                        try { parsed = Double.parseDouble(val); } catch (Exception e) { parsed = 1.5; }
                        if (parsed > 10) parsed = parsed / 1000.0;
                        String norm = String.format(java.util.Locale.US, "%.3f", parsed);
                        sp.edit().putString("vertical_height", norm).apply();
                        Toast.makeText(this, "已设置杆高: " + norm + " m", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .create();
            dialog.show();
        } catch (Exception ignore) {}
    }

    /**
     * 根据点名采集测点
     * TODO: 需要 GnssCore 等类实现后才能使用
     */
    private void collectPointWithName(String pointName) {
        // TODO: 需要相关类实现后才能使用
        Toast.makeText(this, "测量功能待实现，需要相关类支持", Toast.LENGTH_SHORT).show();
        Log.d("PointMeasure", "collectPointWithName: " + pointName + " - 功能待实现");
        /*
        // 获取当前选择的测量方式
        String mode = "rod";
        
        // 保存选择的测量方式
        SharedPreferences sp = getSharedPreferences("measure_prefs", MODE_PRIVATE);
        sp.edit().putString("mode", mode).apply();

        // 对中杆测量模式：向RTK下发天线设置指令
        if ("rod".equals(mode)) {
            try {
                SharedPreferences p = getSharedPreferences("pole_prefs", MODE_PRIVATE);
                String hStr = p.getString("vertical_height", "1.500");
                double h = 1.5;
                try { h = Double.parseDouble(hStr); } catch (Exception ignore) {}
                // 设备要求单位为毫米：1.500 m → 1500
                int mm = (int) Math.round(h * 1000.0);
                String heightCmd = String.format(java.util.Locale.US, "SET,DEVICE.ANT_HEIGHT,%d\r\n", mm);
                DifferentialGps gps = GnssCore.instance().getGps();
                if (gps != null && gps.isOpen()) {
                    gps.send("SET,DEVICE.ANT_MEASURE,4\r\n");
                    gps.send(heightCmd);
                } else {
                    Toast.makeText(this, "RTK未连接，无法下发杆高设置", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignore) {}
        }

        // 执行测量
        collectPointOnce(pointName);
        */
    }

    /**
     * 初始化定时更新坐标显示
     * TODO: 需要相关类实现后才能使用
     */
    private void initPeriodicUpdate() {
        // TODO: 需要相关类实现后才能使用
        Log.d("PointMeasure", "initPeriodicUpdate: 功能待实现");
        /*
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTopBar();
                updateHandler.postDelayed(this, 1000); // 每秒更新一次
            }
        };
        */
    }

    @Override
    protected void onResume() {
        super.onResume();
        // TODO: 开始定时更新（需要相关类实现）
        // if (updateHandler != null && updateRunnable != null) {
        //     updateHandler.post(updateRunnable);
        // }
        
        // TODO: 每次恢复时重新加载历史点（需要相关类实现）
        // loadHistoricalPoints();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 停止定时更新
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private void collectPointOnce(String pointName) {
        // TODO: 需要 GnssCore 等类实现后才能使用
        Toast.makeText(this, "测量功能待实现，需要相关类支持", Toast.LENGTH_SHORT).show();
        Log.d("PointMeasure", "collectPointOnce: " + pointName + " - 功能待实现");
    }

    private void updateTopBar() {
        // TODO: 需要 GnssCore 等类实现后才能使用
        Log.d("PointMeasure", "updateTopBar: 功能待实现");
        // 显示占位符
        if (tvNorth != null) tvNorth.setText("?");
        if (tvEast != null) tvEast.setText("?");
        if (tvHeight != null) tvHeight.setText("?");
        if (tvLat != null) tvLat.setText("?");
        if (tvLon != null) tvLon.setText("?");
        if (tvHgt != null) tvHgt.setText("?");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: 释放语音播报资源（需要相关类实现）
        // if (wxTextToSpeech != null) {
        //     wxTextToSpeech.shutdown();
        //     wxTextToSpeech = null;
        // }
    }

    /**
     * 获取投影转换后的坐标（仅投影转换，不应用平面校正/基准转换/高程拟合）
     * TODO: 需要相关类实现后才能使用
     * @param lon 经度（度）
     * @param lat 纬度（度）
     * @param cs 坐标系统
     * @return 投影后的坐标 [北坐标, 东坐标]，失败返回null
     */
    private double[] getProjectedCoordinate(double lon, double lat, Object cs) {
        // TODO: 需要 CoordinateSystem 等类实现后才能使用
        Log.d("PointMeasure", "getProjectedCoordinate: 功能待实现");
            return null;
    }

    
}


