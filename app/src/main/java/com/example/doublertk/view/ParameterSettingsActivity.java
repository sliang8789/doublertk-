package com.example.doublertk.view;

/**
 * 参数设置活动
 *
 * 功能特性：
 * 1. 椭球、投影、基准转换、平面校正、高程拟合参数设置
 * 2. 智能投影方法选择（UTM或高斯-克吕格）
 * 3. 自动保存投影参数到数据库
 * 4. 用户友好的提示和状态反馈
 * 5. 防止覆盖用户手动输入的数据
 */

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.calculate.CentralMeridianCalculation;
import com.example.doublertk.calculate.FourParameterSolver;
import com.example.doublertk.calculate.GaussKrugerCGCS2000;
import com.example.doublertk.calculate.HeightFittingCalculator;
import com.example.doublertk.calculate.PlaneAdjustmentSolver;
import com.example.doublertk.calculate.PlaneAdjustmentTransform;
import com.example.doublertk.calculate.Point2D;
import com.example.doublertk.calculate.SevenParameterSolver;
import com.example.doublertk.calculate.SevenParameterTransform;
import com.example.doublertk.calculate.ThreeParameterSolver;
import com.example.doublertk.calculate.ThreeParameterTransform;
import com.example.doublertk.calculate.Vector3D;
import com.example.doublertk.calculate.WGS84ToUTM;
import com.example.doublertk.data.CoordinateSystem;
import com.example.doublertk.data.CoordinateSystemRepository;
import com.example.doublertk.data.KnownPoint;
import com.example.doublertk.data.KnownPointRepository;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;

// 已移除对 Proj4J 的依赖，UTM 计算改用本地实现 WGS84ToUTM

public class ParameterSettingsActivity extends BaseActivity {

    // 日志标签常量
    private static final String TAG_COORDINATE_CONVERSION = "CoordinateConversion";
    private static final String TAG_PLANE_CORRECTION = "PlaneCorrection";
    private static final String TAG_PROJECTION_CALC = "ProjectionCalc";

    private int selectedEllipsoidIndex = 0;
    private int selectedProjectionIndex = 0;   // 基准转换: 0 无参数 1 七参数
    private int selectedPlaneIndex = 0;     // 0=无参数 1=三参数 2=四参数 3=平面平差
    private int selectedHeightFitIndex = 4; // 4=加权平均 (原来是1=垂直平差)
    private View lastSel;
    // rows to toggle
    private View rowOriginLat, rowScale, rowAddE, rowAddN, rowZone, rowHemisphere;
    private View divOriginLat, divScale, divAddE, divAddN, divHemisphereTop, divHemisphereBottom;
    private Button btnSave;
    private EditText etSemiMajor, etInvF, etCentralMeridian;
    private android.widget.ScrollView scrollView;
    private TabLayout tabLayout;
    private LinearLayout leftMenu;
    private View coordinateSystemContent;
    private View pointCorrectionContent;
    private LinearLayout containerKnownPoints;
    private int knownPointCount = 2;

    // ─────────── 新增：数据库仓库 ───────────
    private CoordinateSystemRepository csRepo;
    private KnownPointRepository kpRepo;
    private long currentCsId = -1;
    private java.util.concurrent.ExecutorService executorService;

    // panels for param display
    private View panelSevenParam, panelThreeParam, panelFourParam, panelPlaneAdjustment;
    private View phWeighted, phPlane, phSurface, phVertical;
    private boolean isLoadingFromDatabase = false; // 标记是否正在从数据库加载数据
    private boolean userManuallyEnteredCentralMeridian = false; // 标记用户是否手动输入了中央子午线

    @Override
    protected int getLayoutId() {
        return R.layout.activity_parameter_settings;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 初始化仓库和执行器
        csRepo = new CoordinateSystemRepository(getApplication());
        kpRepo = new KnownPointRepository(getApplication());
        executorService = java.util.concurrent.Executors.newSingleThreadExecutor();
        super.onCreate(savedInstanceState);
        initViews();
        setupListeners();

        // 检查运行模式
        String mode = getIntent().getStringExtra("mode");
        if ("edit".equals(mode)) {
            // 编辑模式：加载现有坐标系数据
            long systemId = getIntent().getLongExtra("system_id", -1);
            if (systemId != -1) {
                currentCsId = systemId;
                loadCoordinateSystemData(systemId);
                // 同时加载已知点数据
                loadKnownPointsData(systemId);
            }
            // 编辑模式：坐标系名称可以直接在EditText中输入
        } else if ("create".equals(mode)) {
            // 新建模式：清空所有字段，重置状态
            currentCsId = -1; // 确保是新建模式
            resetAllFields(); // 清空所有输入字段
            clearKnownPoints(); // 清空已知点数据
            userManuallyEnteredCentralMeridian = false; // 重置手动输入标记
        } else {
            // 默认模式：如果未传入 systemId，但数据库中已有坐标系，默认选择最近的一个并加载其点数据
            if (currentCsId <= 0) {
                executorService.execute(() -> {
                    java.util.List<CoordinateSystem> all = csRepo.getAllSync();
                    if (all != null && !all.isEmpty()) {
                        // 按 createdAt DESC 的顺序返回，取第一个
                        CoordinateSystem latest = all.get(0);
                        currentCsId = latest.getId();
                        runOnUiThread(() -> {
                            loadCoordinateSystemData(currentCsId);
                            loadKnownPointsData(currentCsId);
                        });
                    }
                });
            }
        }

        // 检查是否直接进入点校正模式
        String targetTab = getIntent().getStringExtra("target_tab");
        if ("point_correction".equals(targetTab) && currentCsId > 0) {
            // 延迟切换到点校正标签页，确保界面已初始化
            tabLayout.post(() -> {
                if (tabLayout != null && tabLayout.getTabCount() > 1) {
                    tabLayout.selectTab(tabLayout.getTabAt(1));
                }
            });
        }
    }


    private void initViews() {
        btnSave = findViewById(R.id.btn_save);
        scrollView = findViewById(R.id.right_content_scroll);
        tabLayout = findViewById(R.id.tab_layout);
        leftMenu = findViewById(R.id.left_menu);

        // 保存原始的坐标系内容
        coordinateSystemContent = scrollView.getChildAt(0);

        // bind rows
        rowOriginLat  = findViewById(R.id.row_origin_lat);
        rowScale      = findViewById(R.id.row_scale);
        rowAddE       = findViewById(R.id.row_add_east);
        rowAddN       = findViewById(R.id.row_add_north);
        rowZone       = findViewById(R.id.row_zone);
        rowHemisphere = findViewById(R.id.row_hemisphere);
        divScale     = findViewById(R.id.div_scale);
        divAddE      = findViewById(R.id.div_add_east);
        divAddN      = findViewById(R.id.div_add_north);
        divOriginLat  = findViewById(R.id.div_origin_lat);
        divHemisphereTop = findViewById(R.id.div_hemisphere_top);
        divHemisphereBottom = findViewById(R.id.div_hemisphere_bottom);
        // bind ellipsoid param fields
        etSemiMajor = findViewById(R.id.et_semi_major);
        etInvF      = findViewById(R.id.et_inv_f);

        // bind parameter panels
        panelSevenParam = findViewById(R.id.panel_seven_param);
        panelThreeParam = findViewById(R.id.panel_three_param);
        panelFourParam  = findViewById(R.id.panel_four_param);
        panelPlaneAdjustment = findViewById(R.id.panel_plane_adjustment);
        updateDatumPanelVisibility();
        updatePlanePanelVisibility();
        // bind height fit panels
        phWeighted = findViewById(R.id.panel_height_weighted);
        phPlane    = findViewById(R.id.panel_height_plane);
        phSurface  = findViewById(R.id.panel_height_surface);
        phVertical = findViewById(R.id.panel_height_vertical);
        updateHeightPanelVisibility();
        etCentralMeridian = findViewById(R.id.et_central_meridian);

        // 允许中央子午线手动输入，不自动填充
        enableCentralMeridianInput();

        // 给所有关闭图标绑定清空逻辑（坐标系面板）
        attachClearButtonsRecursive(coordinateSystemContent);
        // 点校正内容将在加载时绑定
        // 检查是否有待处理的投影参数计算
        checkPendingProjectionCalculation();

        // 设置TabLayout监听器
        setupTabLayout();

        if (scrollView != null) {
            scrollView.post(() -> {updateProjectionRows(); updateEllipsoidParams(); });
        }
    }

    private void setupListeners() {
        findViewById(R.id.card_plane_correction)
                .setOnClickListener(v -> showPlaneDialog());
        // 高程拟合弹窗
        findViewById(R.id.card_height_fit)
                .setOnClickListener(v -> showHeightFitDialog());
        // 设置整行点击事件，提高用户体验
        setupRowClickListeners();
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveCoordinateSystem());
        }
        // 获取中央子午线按钮 - 已移除RTK自动获取功能，用户需手动输入

        // 菜单与卡片 id 对应关系（已移除格网改正）
        int[] menuIds = new int[]{
                R.id.tv_menu_ellipsoid,
                R.id.tv_menu_projection,
                R.id.tv_menu_datum_transform,
                R.id.tv_menu_plane_correction,
                R.id.tv_menu_height_fit
        };

        int[] cardIds = new int[]{
                R.id.card_ellipsoid,
                R.id.card_touying,
                R.id.card_datum_transform,
                R.id.card_plane_correction,
                R.id.card_height_fit
        };

        // 默认选中第一项指示条
        View first = findViewById(R.id.tv_menu_ellipsoid);
        if (first != null) {
            first.setBackgroundResource(R.drawable.bg_menu_selected_new);
            lastSel = first;
        }

        // 统一设置点击事件
        for (int i = 0; i < menuIds.length; i++) {
            final int cardId = cardIds[i];
            View menuItem = findViewById(menuIds[i]);
            if (menuItem != null) {
                menuItem.setOnClickListener(v -> {
                    View target = findViewById(cardId);
                    if (target != null) {
                        scrollView.post(() -> scrollView.smoothScrollTo(0, target.getTop()));
                    }
                    // 切换指示条背景
                    if (lastSel != null) {
                        lastSel.setBackgroundResource(R.drawable.bg_menu_unselected_new);
                    }
                    v.setBackgroundResource(R.drawable.bg_menu_selected_new);
                    lastSel = v;
                });
            }
        }

        // 右侧滚动联动左侧菜单高亮
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if(leftMenu.getVisibility()!=View.VISIBLE) return; // 仅在坐标系内容时生效
            int scrollY = scrollView.getScrollY();
            int currentIndex = 0;
            for (int i = 0; i < cardIds.length; i++) {
                View card = findViewById(cardIds[i]);
                if(card==null) continue;
                // 若卡片顶部已滚过(<=20px)，则记录为当前; 否则停止遍历
                if(card.getTop() - scrollY <= 20){
                    currentIndex = i;
                }else{
                    break;
                }
            }
            View newSel = findViewById(menuIds[currentIndex]);
            if (newSel != null && newSel != lastSel) {
                if (lastSel != null) {
                    lastSel.setBackgroundResource(R.drawable.bg_menu_unselected_new);
                }
                newSel.setBackgroundResource(R.drawable.bg_menu_selected_new);
                lastSel = newSel;
            }
        });

        // 坐标系名称可以直接在EditText中输入
    }

    /**
     * 加载坐标系统数据到界面
     */
    private void loadCoordinateSystemData(long systemId) {
        executorService.execute(() -> {
            try {
                // 获取坐标系统数据
                CoordinateSystem cs = csRepo.getById(systemId);
                if (cs != null) {
                    runOnUiThread(() -> {
                        populateCoordinateSystemData(cs);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载坐标系统数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 加载已知点数据
     */
    private void loadKnownPointsData(long systemId) {
        executorService.execute(() -> {
            try {
                List<KnownPoint> knownPoints = kpRepo.getByCsSync(systemId);
                runOnUiThread(() -> {
                    if (knownPoints != null && !knownPoints.isEmpty()) {
                        populateKnownPointsData(knownPoints);

                        // 如果当前在点校正界面，立即刷新显示
                        if (pointCorrectionContent != null && pointCorrectionContent.getParent() != null) {
                            refreshKnownPointsDisplay();
                            // 确保第一个已知点被选中并显示数据
                            if (!knownPointValues.isEmpty()) {
                                java.util.List<String> sortedPointNames = new java.util.ArrayList<>(knownPointValues.keySet());
                                java.util.Collections.sort(sortedPointNames);
                                String firstPointName = sortedPointNames.get(0);
                                TextView firstPointView = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
                                if (firstPointView != null) {
                                    firstPointView.post(() -> selectKnownPoint(firstPointView));
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "未找到已知点数据", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载已知点数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 将已知点数据填充到界面
     */
    private void populateKnownPointsData(List<KnownPoint> knownPoints) {
        // 始终先填充内存数据结构（即使界面尚未初始化），避免丢失数据库数据
        knownPointValues.clear();
        measuredPointValues.clear();
        accuracyValues.clear();

        for (KnownPoint kp : knownPoints) {
            String pointName = kp.getName();

            // 存储数据
            String[] knownCoords = new String[]{
                    kp.getX() != null ? String.valueOf(kp.getX()) : "",
                    kp.getY() != null ? String.valueOf(kp.getY()) : "",
                    kp.getZ() != null ? String.valueOf(kp.getZ()) : ""
            };
            knownPointValues.put(pointName, knownCoords);

            String[] measuredCoords = new String[]{
                    kp.getMeasuredB() != null ? String.valueOf(kp.getMeasuredB()) : "",
                    kp.getMeasuredL() != null ? String.valueOf(kp.getMeasuredL()) : "",
                    kp.getMeasuredH() != null ? String.valueOf(kp.getMeasuredH()) : ""
            };
            measuredPointValues.put(pointName, measuredCoords);

            // 加载精度值
            double horizontalAccuracy = kp.getHorizontalAccuracy() != null ? kp.getHorizontalAccuracy() : 0.0;
            double elevationAccuracy = kp.getElevationAccuracy() != null ? kp.getElevationAccuracy() : 0.0;
            accuracyValues.put(pointName, new double[]{horizontalAccuracy, elevationAccuracy});
        }

        // 若界面尚未初始化，先返回，待界面创建后调用 refreshKnownPointsDisplay()
        if (pointCorrectionContent == null || containerKnownPoints == null) {
            return;
        }

        // 按名称排序创建界面
        java.util.List<String> sortedPointNames = new java.util.ArrayList<>(knownPointValues.keySet());
        java.util.Collections.sort(sortedPointNames);

        // 清空现有界面（保留静态的前两个点）
        TextView tv1 = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
        TextView tv2 = pointCorrectionContent.findViewById(R.id.tv_known_point_2);
        for (int i = containerKnownPoints.getChildCount() - 1; i >= 0; i--) {
            View child = containerKnownPoints.getChildAt(i);
            if (child != tv1 && child != tv2) {
                containerKnownPoints.removeViewAt(i);
            }
        }

        // 按排序后的顺序创建界面
        for (int i = 0; i < sortedPointNames.size(); i++) {
            String pointName = sortedPointNames.get(i);

            if (i < 2) {
                // 前两个点使用静态TextView
                final TextView pointView;
                if (i == 0) {
                    pointView = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
                } else if (i == 1) {
                    pointView = pointCorrectionContent.findViewById(R.id.tv_known_point_2);
                } else {
                    pointView = null;
                }

                if (pointView != null) {
                    pointView.setText(pointName);
                    pointView.setOnClickListener(v -> selectKnownPoint(pointView));
                    if (i == 0) {
                        // 默认选中第一个点并延迟执行，确保UI已完全加载
                        pointView.post(() -> selectKnownPoint(pointView));
                    }
                }
            } else {
                // 动态添加额外的点
                addKnownPointToContainer(pointName, i + 1);
            }
        }

        // 如果没有已知点，添加默认的两个点
        if (knownPoints.isEmpty()) {
            knownPointValues.put("已知点1", new String[]{"", "", ""});
            knownPointValues.put("已知点2", new String[]{"", "", ""});
            measuredPointValues.put("已知点1", new String[]{"", "", ""});
            measuredPointValues.put("已知点2", new String[]{"", "", ""});
        }
    }

    /**
     * 动态添加已知点到容器
     */
    private void addKnownPointToContainer(String pointName, int index) {
        if (containerKnownPoints == null) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        // TextView
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()));
        tv.setLayoutParams(tvLp);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()), 0, 0, 0);
        tv.setText(pointName);
        tv.setTextSize(16);
        tv.setBackgroundResource(R.drawable.bg_known_point_unselected);
        tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_point_unselected, 0, 0, 0);
        tv.setCompoundDrawablePadding(8);

        // 设置删除按钮为右侧的CompoundDrawable
        tv.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_point_unselected,  // 左侧图标
                0,                               // 顶部图标
                R.drawable.ic_menu_close_clear_cancel,       // 右侧图标（小叉号）
                0                                // 底部图标
        );

        // 选中逻辑：高亮该行
        tv.setOnClickListener(v -> selectKnownPoint(tv));

        // 设置删除按钮的点击事件
        tv.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                // 检查点击位置是否在右侧图标区域
                android.graphics.drawable.Drawable[] drawables = tv.getCompoundDrawables();
                if (drawables[2] != null && event.getX() > tv.getWidth() - tv.getCompoundDrawablePadding() -
                        drawables[2].getIntrinsicWidth()) {
                    // 删除对应的数据存储
                    String pointNameToDelete = tv.getText().toString();
                    knownPointValues.remove(pointNameToDelete);
                    measuredPointValues.remove(pointNameToDelete);

                    // 如果删除的是当前选中的点，清空选中状态
                    if(lastKnownSel == tv){
                        lastKnownSel = null;
                        if(etKnownN != null) etKnownN.setText("");
                        if(etKnownE != null) etKnownE.setText("");
                        if(etKnownH != null) etKnownH.setText("");
                    }

                    containerKnownPoints.removeView(row);
                    return true;
                }
            }
            return false;
        });

        row.addView(tv);
        containerKnownPoints.addView(row);
    }

    /**
     * 刷新已知点显示
     */
    private void refreshKnownPointsDisplay() {

        if (pointCorrectionContent == null) {
            Toast.makeText(this, "错误: pointCorrectionContent 为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (containerKnownPoints == null) {
            Toast.makeText(this, "错误: containerKnownPoints 为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 清空动态添加的视图（保留静态的前两个点）
        // 找到前两个静态视图
        TextView tv1 = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
        TextView tv2 = pointCorrectionContent.findViewById(R.id.tv_known_point_2);

        if (tv1 == null) {
            Toast.makeText(this, "错误: 找不到 tv_known_point_1", Toast.LENGTH_SHORT).show();
        }
        if (tv2 == null) {
            Toast.makeText(this, "错误: 找不到 tv_known_point_2", Toast.LENGTH_SHORT).show();
        }

        // 移除除了前两个静态视图之外的所有子视图
        for (int i = containerKnownPoints.getChildCount() - 1; i >= 0; i--) {
            View child = containerKnownPoints.getChildAt(i);
            if (child != tv1 && child != tv2) {
                containerKnownPoints.removeViewAt(i);
            }
        }

        // 重新添加已知点
        int index = 0;
        // 使用LinkedHashMap的keySet来保持插入顺序，或者按名称排序
        java.util.List<String> sortedPointNames = new java.util.ArrayList<>(knownPointValues.keySet());
        java.util.Collections.sort(sortedPointNames);

        for (String pointName : sortedPointNames) {

            if (index < 2) {
                // 前两个点使用静态TextView
                final TextView pointView;
                if (index == 0) {
                    pointView = tv1;
                } else if (index == 1) {
                    pointView = tv2;
                } else {
                    pointView = null;
                }

                if (pointView != null) {
                    pointView.setText(pointName);
                    pointView.setOnClickListener(v -> selectKnownPoint(pointView));
                    if (index == 0) {
                        // 默认选中第一个点并延迟执行，确保UI已完全加载
                        pointView.post(() -> selectKnownPoint(pointView));
                    }
                } else {
                    Toast.makeText(this, "错误: 静态视图为空，索引: " + index, Toast.LENGTH_SHORT).show();
                }
            } else {
                addKnownPointToContainer(pointName, index + 1);
            }
            index++;
        }
    }
    /**
     * 将坐标系统数据填充到界面
     */
    private void populateCoordinateSystemData(CoordinateSystem cs) {
        isLoadingFromDatabase = true; // 标记正在从数据库加载数据
        try {
            // 设置坐标系名称
            EditText tvCoordName = findViewById(R.id.tv_coord_name);
            if (tvCoordName != null) {
                tvCoordName.setText(cs.getName());
            }

            // 设置椭球参数
            setEllipsoidFromName(cs.getEllipsoidName());
            if (etSemiMajor != null) {
                etSemiMajor.setText(String.valueOf(cs.getSemiMajorAxis()));
            }
            if (etInvF != null) {
                etInvF.setText(String.valueOf(cs.getInverseFlattening()));
            }

            // 设置投影参数
            setProjectionFromName(cs.getProjectionName());
            if (etCentralMeridian != null) {
                // 只有当中央子午线不为0时才显示，否则保持空白
                if (cs.getCentralMeridian() != 0.0) {
                    etCentralMeridian.setText(String.format("%.8f", cs.getCentralMeridian()));
                } else {
                    etCentralMeridian.setText(""); // 保持空白
                }
                // 从数据库加载时，重置手动输入标记
                userManuallyEnteredCentralMeridian = false;
            }

            // 解析并设置投影参数JSON
            if (cs.getProjectionParams() != null && !cs.getProjectionParams().isEmpty()) {
                try {
                    org.json.JSONObject projJson = new org.json.JSONObject(cs.getProjectionParams());
                    setProjectionParameters(projJson);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }

            // 解析并设置基准转换参数
            if (cs.getDatumParams() != null && !cs.getDatumParams().isEmpty()) {
                try {
                    org.json.JSONObject datumJson = new org.json.JSONObject(cs.getDatumParams());
                    setDatumParameters(datumJson);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }

            // 解析并设置平面校正参数
            if (cs.getPlaneParams() != null && !cs.getPlaneParams().isEmpty()) {
                try {
                    org.json.JSONObject planeJson = new org.json.JSONObject(cs.getPlaneParams());
                    setPlaneParameters(planeJson);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }

            // 解析并设置高程拟合参数
            if (cs.getHeightParams() != null && !cs.getHeightParams().isEmpty()) {
                try {
                    org.json.JSONObject heightJson = new org.json.JSONObject(cs.getHeightParams());
                    setHeightParameters(heightJson);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }

            // 更新界面显示
            updateProjectionRows();
            updateEllipsoidParams();
            updateDatumPanelVisibility();
            updatePlanePanelVisibility();
            updateHeightPanelVisibility();

            // 强制刷新界面以确保所有参数都显示
            if (scrollView != null) {
                scrollView.post(() -> {
                    updateDatumPanelVisibility();
                    updatePlanePanelVisibility();
                    updateHeightPanelVisibility();

                    // 延迟再次刷新，确保所有参数都正确显示
                    scrollView.postDelayed(() -> {
                        updateDatumPanelVisibility();
                        updatePlanePanelVisibility();
                        updateHeightPanelVisibility();

                        // 更新显示文本
                        TextView tvDatum = findViewById(R.id.tv_datum_transform);
                        TextView tvPlane = findViewById(R.id.tv_plane_correction);
                        if (tvDatum != null) {
                            String[] datumTypes = {"无参数", "七参数"};
                            int idx = Math.min(selectedProjectionIndex, datumTypes.length - 1);
                            tvDatum.setText(datumTypes[idx]);
                        }
                        if (tvPlane != null) {
                            String[] planeTypes = {"无参数", "三参数", "四参数", "平面平差"};
                            tvPlane.setText(planeTypes[selectedPlaneIndex]);
                        }
                        updateHeightFitDisplay();
                    }, 500);
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "填充数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            isLoadingFromDatabase = false; // 加载完成后重置标志
        }
    }

    /**
     * 根据椭球名称设置椭球索引
     */
    private void setEllipsoidFromName(String ellipsoidName) {
        if (ellipsoidName == null) return;

        switch (ellipsoidName) {
            case "CGCS2000":
                selectedEllipsoidIndex = 0;
                break;
            case "WGS84":
                selectedEllipsoidIndex = 1;
                break;
            case "北京54":
                selectedEllipsoidIndex = 2;
                break;
            case "西安80":
                selectedEllipsoidIndex = 3;
                break;
        }

        // 更新椭球名称显示
        TextView tvEllipsoid = findViewById(R.id.tv_ellipsoid_name);
        if (tvEllipsoid != null) {
            tvEllipsoid.setText(ellipsoidName);
        }

        // 不自动填充中央子午线，等待用户点击"获取"按钮或手动输入
    }

    /**
     * 根据投影名称设置投影索引
     */
    private void setProjectionFromName(String projectionName) {
        if (projectionName == null) return;

        // 更新投影名称显示
        TextView tvProjection = findViewById(R.id.tv_projection_model);
        if (tvProjection != null) {
            tvProjection.setText(projectionName);
        }
    }
    /**
     * 设置投影参数
     */
    private void setProjectionParameters(org.json.JSONObject projJson) {
        try {
            // 设置中央子午线（如果JSON中有的话，优先使用JSON中的值）
            if (projJson.has("centralMeridian")) {
                if (etCentralMeridian != null) {
                    double centralMeridian = projJson.getDouble("centralMeridian");
                    // 只有当中央子午线不为0时才显示，否则保持空白
                    if (centralMeridian != 0.0) {
                        etCentralMeridian.setText(String.format("%.8f", centralMeridian));
                    } else {
                        etCentralMeridian.setText(""); // 保持空白
                    }
                    // 从数据库加载时，重置手动输入标记
                    userManuallyEnteredCentralMeridian = false;
                }
            }

            // 设置投影带号
            if (projJson.has("zone")) {
                EditText etZone = findViewById(R.id.et_utm_zone);
                if (etZone != null) {
                    etZone.setText(String.valueOf(projJson.getInt("zone")));
                }
            }

            // 设置原点纬度 - 使用行查找
            if (projJson.has("originLat")) {
                View rowOriginLat = findViewById(R.id.row_origin_lat);
                if (rowOriginLat instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) rowOriginLat;
                    if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                        EditText etOriginLat = (EditText) row.getChildAt(1);
                        etOriginLat.setText(String.valueOf(projJson.getDouble("originLat")));
                    }
                }
            }

            // 设置比例因子
            if (projJson.has("scaleFactor")) {
                EditText etScaleFactor = findViewById(R.id.et_scale_factor);
                if (etScaleFactor != null) {
                    etScaleFactor.setText(String.valueOf(projJson.getDouble("scaleFactor")));
                }
            }

            // 设置东偏移
            if (projJson.has("falseEasting")) {
                EditText etAddEast = findViewById(R.id.et_add_east);
                if (etAddEast != null) {
                    etAddEast.setText(String.valueOf(projJson.getDouble("falseEasting")));
                }
            }

            // 设置北偏移 - 使用行查找
            if (projJson.has("falseNorthing")) {
                View rowAddNorth = findViewById(R.id.row_add_north);
                if (rowAddNorth instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) rowAddNorth;
                    if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                        EditText etAddNorth = (EditText) row.getChildAt(1);
                        etAddNorth.setText(String.valueOf(projJson.getDouble("falseNorthing")));
                    }
                }
            }

            // 设置平均纬度 - 使用行查找
            if (projJson.has("averageLat")) {
                // 平均纬度在第6个LinearLayout（从0开始计数）
                ViewGroup projectionCard = findViewById(R.id.card_touying);
                if (projectionCard instanceof ViewGroup) {
                    ViewGroup cardContent = (ViewGroup) ((ViewGroup) projectionCard).getChildAt(0);
                    if (cardContent.getChildCount() > 6) {
                        View avgLatRow = cardContent.getChildAt(6);
                        if (avgLatRow instanceof ViewGroup) {
                            ViewGroup row = (ViewGroup) avgLatRow;
                            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                                EditText etAverageLat = (EditText) row.getChildAt(1);
                                etAverageLat.setText(String.valueOf(projJson.getDouble("averageLat")));
                            }
                        }
                    }
                }
            }

            // 设置投影面高 - 使用行查找
            if (projJson.has("projectionHeight")) {
                // 投影面高在第7个LinearLayout（从0开始计数）
                ViewGroup projectionCard = findViewById(R.id.card_touying);
                if (projectionCard instanceof ViewGroup) {
                    ViewGroup cardContent = (ViewGroup) ((ViewGroup) projectionCard).getChildAt(0);
                    if (cardContent.getChildCount() > 7) {
                        View projHeightRow = cardContent.getChildAt(7);
                        if (projHeightRow instanceof ViewGroup) {
                            ViewGroup row = (ViewGroup) projHeightRow;
                            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                                EditText etProjectionHeight = (EditText) row.getChildAt(1);
                                etProjectionHeight.setText(String.valueOf(projJson.getDouble("projectionHeight")));
                            }
                        }
                    }
                }
            }

        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置基准转换参数
     */
    private void setDatumParameters(org.json.JSONObject datumJson) {
        try {
            // 检查是否为七参数
            if (datumJson.has("dx") || datumJson.has("dy") || datumJson.has("dz") ||
                    datumJson.has("rx") || datumJson.has("ry") || datumJson.has("rz") || datumJson.has("scale")) {
                selectedProjectionIndex = 1; // 七参数

                // 设置七参数值
                if (datumJson.has("dx")) {
                    EditText etDx = findViewById(R.id.tv_sp_dx);
                    if (etDx != null) {
                        etDx.setText(String.valueOf(datumJson.getDouble("dx")));
                    }
                }
                if (datumJson.has("dy")) {
                    EditText etDy = findViewById(R.id.tv_sp_dy);
                    if (etDy != null) {
                        etDy.setText(String.valueOf(datumJson.getDouble("dy")));
                    }
                }
                if (datumJson.has("dz")) {
                    EditText etDz = findViewById(R.id.tv_sp_dz);
                    if (etDz != null) {
                        etDz.setText(String.valueOf(datumJson.getDouble("dz")));
                    }
                }
                if (datumJson.has("rx")) {
                    EditText etRx = findViewById(R.id.tv_sp_rx);
                    if (etRx != null) {
                        etRx.setText(String.valueOf(datumJson.getDouble("rx")));
                    }
                }
                if (datumJson.has("ry")) {
                    EditText etRy = findViewById(R.id.tv_sp_ry);
                    if (etRy != null) {
                        etRy.setText(String.valueOf(datumJson.getDouble("ry")));
                    }
                }
                if (datumJson.has("rz")) {
                    EditText etRz = findViewById(R.id.tv_sp_rz);
                    if (etRz != null) {
                        etRz.setText(String.valueOf(datumJson.getDouble("rz")));
                    }
                }
                if (datumJson.has("scale")) {
                    EditText etScale = findViewById(R.id.tv_sp_s);
                    if (etScale != null) {
                        etScale.setText(String.valueOf(datumJson.getDouble("scale")));
                    }
                }
            } else {
                selectedProjectionIndex = 0; // 无参数
            }

            // 更新基准转换显示
            TextView tvDatum = findViewById(R.id.tv_datum_transform);
            if (tvDatum != null) {
                String[] datumTypes = {"无参数", "七参数"};
                int idx = Math.min(selectedProjectionIndex, datumTypes.length - 1);
                tvDatum.setText(datumTypes[idx]);
            }

            // 只有在用户主动选择七参数时才强制设置平面校正和高程拟合为无参数
            // 从数据库加载时不应该强制覆盖
            if (selectedProjectionIndex == 1 && !isLoadingFromDatabase) {
                selectedPlaneIndex = 0; // 无参数
                selectedHeightFitIndex = 0; // 无参数
                updatePlanePanelVisibility();
                updateHeightPanelVisibility();
            }

        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置平面校正参数
     */
    private void setPlaneParameters(org.json.JSONObject planeJson) {
        try {
            // 检查参数类型
            if (planeJson.has("dx") && planeJson.has("dy") && planeJson.has("theta") && planeJson.has("k")) {
                selectedPlaneIndex = 2; // 四参数

                // 设置四参数值
                if (planeJson.has("dx")) {
                    EditText etDx = findViewById(R.id.tv_fp_dx);
                    if (etDx != null) {
                        etDx.setText(String.valueOf(planeJson.getDouble("dx")));
                    }
                }
                if (planeJson.has("dy")) {
                    EditText etDy = findViewById(R.id.tv_fp_dy);
                    if (etDy != null) {
                        etDy.setText(String.valueOf(planeJson.getDouble("dy")));
                    }
                }
                if (planeJson.has("theta")) {
                    EditText etTheta = findViewById(R.id.tv_fp_theta);
                    if (etTheta != null) {
                        etTheta.setText(String.valueOf(planeJson.getDouble("theta")));
                    }
                }
                if (planeJson.has("k")) {
                    EditText etK = findViewById(R.id.tv_fp_k);
                    if (etK != null) {
                        etK.setText(String.valueOf(planeJson.getDouble("k")));
                    }
                }
            } else if (planeJson.has("dx") && planeJson.has("dy") && planeJson.has("theta")) {
                selectedPlaneIndex = 1; // 三参数

                // 设置三参数值
                if (planeJson.has("dx")) {
                    EditText etDx = findViewById(R.id.tv_tp_dx);
                    if (etDx != null) {
                        etDx.setText(String.valueOf(planeJson.getDouble("dx")));
                    }
                }
                if (planeJson.has("dy")) {
                    EditText etDy = findViewById(R.id.tv_tp_dy);
                    if (etDy != null) {
                        etDy.setText(String.valueOf(planeJson.getDouble("dy")));
                    }
                }
                if (planeJson.has("theta")) {
                    EditText etTheta = findViewById(R.id.tv_tp_theta);
                    if (etTheta != null) {
                        etTheta.setText(String.valueOf(planeJson.getDouble("theta")));
                    }
                }
            } else if (planeJson.has("north_origin") && planeJson.has("east_origin") &&
                    planeJson.has("north_translation") && planeJson.has("east_translation") &&
                    planeJson.has("rotation_scale") && planeJson.has("scale")) {
                selectedPlaneIndex = 3; // 平面平差

                // 设置平面平差参数值
                if (planeJson.has("north_origin")) {
                    EditText etNorthOrigin = findViewById(R.id.tv_pa_north_origin);
                    if (etNorthOrigin != null) {
                        etNorthOrigin.setText(String.valueOf(planeJson.getDouble("north_origin")));
                    }
                }
                if (planeJson.has("east_origin")) {
                    EditText etEastOrigin = findViewById(R.id.tv_pa_east_origin);
                    if (etEastOrigin != null) {
                        etEastOrigin.setText(String.valueOf(planeJson.getDouble("east_origin")));
                    }
                }
                if (planeJson.has("north_translation")) {
                    EditText etNorthTranslation = findViewById(R.id.tv_pa_north_translation);
                    if (etNorthTranslation != null) {
                        etNorthTranslation.setText(String.valueOf(planeJson.getDouble("north_translation")));
                    }
                }
                if (planeJson.has("east_translation")) {
                    EditText etEastTranslation = findViewById(R.id.tv_pa_east_translation);
                    if (etEastTranslation != null) {
                        etEastTranslation.setText(String.valueOf(planeJson.getDouble("east_translation")));
                    }
                }
                if (planeJson.has("rotation_scale")) {
                    EditText etRotationScale = findViewById(R.id.tv_pa_rotation_scale);
                    if (etRotationScale != null) {
                        etRotationScale.setText(String.valueOf(planeJson.getDouble("rotation_scale")));
                    }
                }
                if (planeJson.has("scale")) {
                    EditText etScale = findViewById(R.id.tv_pa_scale);
                    if (etScale != null) {
                        etScale.setText(String.valueOf(planeJson.getDouble("scale")));
                    }
                }
            } else {
                selectedPlaneIndex = 0; // 无参数
            }

            // 更新平面校正显示
            TextView tvPlane = findViewById(R.id.tv_plane_correction);
            if (tvPlane != null) {
                String[] planeTypes = {"无参数", "三参数", "四参数", "平面平差"};
                tvPlane.setText(planeTypes[selectedPlaneIndex]);
            }

            // 如果是七参数模式，强制平面校正为无参数
            if (selectedProjectionIndex == 1 && !isLoadingFromDatabase) {
                selectedPlaneIndex = 0; // 无参数
                if (tvPlane != null) {
                    tvPlane.setText("无参数");
                }
                updatePlanePanelVisibility();
            }

        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置高程拟合参数
     */
    private void setHeightParameters(org.json.JSONObject heightJson) {
        try {
            // 检查参数类型 - 按照参数数量和特征来判断
            if (heightJson.has("a") && heightJson.has("b") && heightJson.has("c") &&
                    heightJson.has("d") && heightJson.has("e") && heightJson.has("f") &&
                    heightJson.has("n0") && heightJson.has("e0")) {
                // 曲面拟合：8个参数 (a,b,c,d,e,f,n0,e0)
                selectedHeightFitIndex = 3;

                EditText etA = findViewById(R.id.tv_hf_s_a);
                EditText etB = findViewById(R.id.tv_hf_s_b);
                EditText etC = findViewById(R.id.tv_hf_s_c);
                EditText etD = findViewById(R.id.tv_hf_s_d);
                EditText etE = findViewById(R.id.tv_hf_s_e);
                EditText etF = findViewById(R.id.tv_hf_s_f);
                EditText etN0 = findViewById(R.id.tv_hf_s_n0);
                EditText etE0 = findViewById(R.id.tv_hf_s_e0);

                if (etA != null) etA.setText(String.valueOf(heightJson.getDouble("a")));
                if (etB != null) etB.setText(String.valueOf(heightJson.getDouble("b")));
                if (etC != null) etC.setText(String.valueOf(heightJson.getDouble("c")));
                if (etD != null) etD.setText(String.valueOf(heightJson.getDouble("d")));
                if (etE != null) etE.setText(String.valueOf(heightJson.getDouble("e")));
                if (etF != null) etF.setText(String.valueOf(heightJson.getDouble("f")));
                if (etN0 != null) etN0.setText(String.valueOf(heightJson.getDouble("n0")));
                if (etE0 != null) etE0.setText(String.valueOf(heightJson.getDouble("e0")));

            } else if (heightJson.has("a") && heightJson.has("b") && heightJson.has("c") &&
                    heightJson.has("n0") && heightJson.has("e0")) {
                // 平面拟合：5个参数 (a,b,c,n0,e0)
                selectedHeightFitIndex = 2;

                EditText etA = findViewById(R.id.tv_hf_p_a);
                EditText etB = findViewById(R.id.tv_hf_p_b);
                EditText etC = findViewById(R.id.tv_hf_p_c);
                EditText etN0 = findViewById(R.id.tv_hf_p_n0);
                EditText etE0 = findViewById(R.id.tv_hf_p_e0);

                if (etA != null) etA.setText(String.valueOf(heightJson.getDouble("a")));
                if (etB != null) etB.setText(String.valueOf(heightJson.getDouble("b")));
                if (etC != null) etC.setText(String.valueOf(heightJson.getDouble("c")));
                if (etN0 != null) etN0.setText(String.valueOf(heightJson.getDouble("n0")));
                if (etE0 != null) etE0.setText(String.valueOf(heightJson.getDouble("e0")));

            } else if (heightJson.has("n0") && heightJson.has("e0") && heightJson.has("nslope") &&
                    heightJson.has("eslope") && heightJson.has("const")) {
                // 垂直偏差：5个参数 (n0,e0,nslope,eslope,const)
                selectedHeightFitIndex = 1;

                EditText etN0 = findViewById(R.id.tv_hf_v_n0);
                EditText etE0 = findViewById(R.id.tv_hf_v_e0);
                EditText etNslope = findViewById(R.id.tv_hf_v_nslope);
                EditText etEslope = findViewById(R.id.tv_hf_v_eslope);
                EditText etConst = findViewById(R.id.tv_hf_v_const);

                if (etN0 != null) etN0.setText(String.valueOf(heightJson.getDouble("n0")));
                if (etE0 != null) etE0.setText(String.valueOf(heightJson.getDouble("e0")));
                if (etNslope != null) etNslope.setText(String.valueOf(heightJson.getDouble("nslope")));
                if (etEslope != null) etEslope.setText(String.valueOf(heightJson.getDouble("eslope")));
                if (etConst != null) etConst.setText(String.valueOf(heightJson.getDouble("const")));

            } else if (heightJson.has("a") && !heightJson.has("b")) {
                // 加权平均：1个参数 (a)
                selectedHeightFitIndex = 4;

                EditText etA = findViewById(R.id.tv_hf_w_a);
                if (etA != null) etA.setText(String.valueOf(heightJson.getDouble("a")));
            }

            // 更新高程拟合显示
            updateHeightFitDisplay();

            // 如果是七参数模式，强制高程拟合为无参数
            if (selectedProjectionIndex == 1 && !isLoadingFromDatabase) {
                selectedHeightFitIndex = 0; // 无参数
                updateHeightFitDisplay();
            }

            // 如果是基准转换无参数模式，且高程拟合也是无参数，则自动改为加权平均
            if (selectedProjectionIndex == 0 && selectedHeightFitIndex == 0 && !isLoadingFromDatabase) {
                selectedHeightFitIndex = 4; // 改为加权平均
                updateHeightFitDisplay();
            }

        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
    }

    /** 椭球名称弹窗：无圆圈、背景高亮，底部保存/取消 */
    private void showEllipsoidDialog() {
        final String[] items = {"CGCS2000","WGS84","北京54","西安80"};
        final int[] tmp = {selectedEllipsoidIndex};               // 临时索引
        int width = getResources().getDisplayMetrics().widthPixels / 3;

        // 1. 纯文字列表，选中行灰背景
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                v.setBackgroundColor(pos==tmp[0] ? 0xFFE0E0E0 : 0x00000000);
                return v;
            }
        };

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("椭球名称")
                .setAdapter(adapter, null)        // 列表交互自己处理
                .setPositiveButton("保存", (d,w) -> {
                    selectedEllipsoidIndex = tmp[0];
                    ((TextView)findViewById(R.id.tv_ellipsoid_name))
                            .setText(items[selectedEllipsoidIndex]);
                    updateProjectionRows();
                    updateEllipsoidParams();

                    // 不自动填充中央子午线，等待用户操作
                })
                .setNegativeButton("取消", null)
                .create();

        dlg.show();
        styleDialog(dlg);

        // 2. 行点击 → 更新 tmp 并刷新背景
        ListView lv = dlg.getListView();
        lv.setOnItemClickListener((p,v,pos,id)->{
            tmp[0] = pos;
            adapter.notifyDataSetChanged();
        });

        // 3. 调整宽高与按钮颜色
        Window win = dlg.getWindow();
        if (win != null) {
            win.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
            win.setGravity(Gravity.END);
            // 沉浸式隐藏系统栏
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  |
                            View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }
    /** 根据是否为 WGS84 切换行可见 */
    private void updateEllipsoidParams(){
        if(etSemiMajor==null||etInvF==null) return;
        switch(selectedEllipsoidIndex){
            case 0: // CGCS2000
                etSemiMajor.setText("6378137");
                etInvF.setText("298.257222101");
                break;
            case 1: // WGS84
                etSemiMajor.setText("6378137");
                etInvF.setText("298.257223563");
                break;
            case 2: // 北京54
                etSemiMajor.setText("6378245");
                etInvF.setText("298.3");
                break;
            case 3: // 西安80
                etSemiMajor.setText("6378140");
                etInvF.setText("298.257");
                break;
        }

        // 默认选择北半球
        RadioButton rbNorth = findViewById(R.id.rb_north);
        if (rbNorth != null) {
            rbNorth.setChecked(true);
        }
    }

    private void updateProjectionRows() {
        boolean wgs = selectedEllipsoidIndex == 1;      // 1 = WGS84
        int S = View.VISIBLE, H = View.GONE;

        // 行
        rowOriginLat.setVisibility (wgs ? H : S);
        rowScale.setVisibility     (wgs ? H : S);
        rowAddE.setVisibility      (wgs ? H : S);
        rowAddN.setVisibility      (wgs ? H : S);
        // 投影带号仅在 UTM 投影（WGS84）下输入，高斯投影禁止使用带号
        rowZone.setVisibility(wgs ? S : H);
        // 半球选项仅在 UTM 投影下需要，默认北半球
        rowHemisphere.setVisibility(wgs ? S : H);

        // 分隔线
        divOriginLat.setVisibility (wgs ? H : S);
        divScale.setVisibility     (wgs ? H : S);
        divAddE.setVisibility      (wgs ? H : S);
        divAddN.setVisibility      (wgs ? H : S);
        divHemisphereTop.setVisibility(wgs ? S : H);
        divHemisphereBottom.setVisibility(wgs ? S : H);

        // 根据椭球类型自动设置对应的投影类型
        // 确保只有4种组合：BJ54+高斯、WGS84+UTM、Xian80+高斯、CGCS2000+高斯
        TextView tv = findViewById(R.id.tv_projection_model);
        if(tv!=null){
            if(wgs){
                // WGS84 强制使用UTM投影
                tv.setText("UTM投影");
                
                // 如果有中央子午线，计算对应的UTM带号
                if (etCentralMeridian != null && !etCentralMeridian.getText().toString().trim().isEmpty()) {
                    try {
                        double cm = Double.parseDouble(etCentralMeridian.getText().toString().trim());
                        if (!Double.isNaN(cm) && !Double.isInfinite(cm)) {
                            int zone = (int)((cm + 183) / 6);
                            if (zone >= 1 && zone <= 60) {
                                View rowZone = findViewById(R.id.row_zone);
                                if (rowZone instanceof ViewGroup) {
                                    ViewGroup row = (ViewGroup) rowZone;
                                    if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                                        EditText etZone = (EditText) row.getChildAt(1);
                                        etZone.setText(String.valueOf(zone));
                                        etZone.setEnabled(false);
                                        etZone.setBackgroundColor(0xFFF0F0F0);
                                    }
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }else{
                // 非WGS84椭球，强制使用高斯-克吕格投影（统一3度带）
                switch (selectedEllipsoidIndex) {
                    case 0: // CGCS2000
                        tv.setText("高斯投影");
                        break;
                    case 2: // 北京54
                        tv.setText("高斯投影");
                        break;
                    case 3: // 西安80
                        tv.setText("高斯投影");
                        break;
                    default:
                        tv.setText("高斯投影");
                        break;
                }
            }
        }

        // 设置投影参数的初始值并禁用编辑
        setProjectionParameterInitialValuesAndDisable();

        RadioButton rbNorth = findViewById(R.id.rb_north);
        if (rbNorth != null) {
            rbNorth.setChecked(true);
        }
    }

    /**
     * 设置投影参数的初始值并禁用编辑
     */
    private void setProjectionParameterInitialValuesAndDisable() {
        boolean wgs = selectedEllipsoidIndex == 1;      // 1 = WGS84

        if (wgs) {
            // UTM投影的标准参数
            setEditTextInRow(R.id.row_origin_lat, "0.0000000", false);
            setEditTextInRow(R.id.row_scale, "0.9996", false);
            setEditTextInRow(R.id.row_add_east, "500000", false);
            setEditTextInRow(R.id.row_add_north, "0", false);

            // 默认选择北半球
            RadioButton rbNorth = findViewById(R.id.rb_north);
            if (rbNorth != null) {
                rbNorth.setChecked(true);
            }
        } else {
            // 高斯投影的标准参数
            setEditTextInRow(R.id.row_origin_lat, "0.0000000", false);
            setEditTextInRow(R.id.row_scale, "1.0000", false);
            setEditTextInRow(R.id.row_add_east, "500000", false);
            setEditTextInRow(R.id.row_add_north, "0", false);
        }
    }

    /**
     * 禁用投影参数的编辑，因为它们是自动计算的
     */
    private void disableProjectionParameterEditing() {
        // 禁用原点纬度编辑
        View rowOriginLat = findViewById(R.id.row_origin_lat);
        if (rowOriginLat instanceof ViewGroup) {
            ViewGroup row = (ViewGroup) rowOriginLat;
            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                EditText etOriginLat = (EditText) row.getChildAt(1);
                etOriginLat.setEnabled(false);
                etOriginLat.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }

        // 禁用比例因子编辑
        EditText etScaleFactor = findViewById(R.id.et_scale_factor);
        if (etScaleFactor != null) {
            etScaleFactor.setEnabled(false);
            etScaleFactor.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        // 禁用东偏移编辑
        EditText etAddEast = findViewById(R.id.et_add_east);
        if (etAddEast != null) {
            etAddEast.setEnabled(false);
            etAddEast.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        // 禁用北偏移编辑
        View rowAddNorth = findViewById(R.id.row_add_north);
        if (rowAddNorth instanceof ViewGroup) {
            ViewGroup row = (ViewGroup) rowAddNorth;
            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                EditText etAddNorth = (EditText) row.getChildAt(1);
                etAddNorth.setEnabled(false);
                etAddNorth.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }
    }

    /**
     * 根据当前设置自动计算中央子午线。
     * WGS-84 使用 UTM 带号公式 (zone * 6 - 183)。
     * 其它椭球返回默认 117°。
     */

    /**
     * 根据当前选择的坐标系统计算中央子午线
     * 
     * @param lon 经度（度）
     * @param lat 纬度（度）
     * @return 中央子午线计算结果
     */
    private CentralMeridianCalculation.BaseResult calculateCentralMeridianByCoordinateSystem(
            double lon, double lat) {
        
        // 检查投影类型（从UI中获取）
        TextView tvProjection = findViewById(R.id.tv_projection_model);
        boolean isUTM = tvProjection != null && 
                        tvProjection.getText() != null && 
                        tvProjection.getText().toString().contains("UTM");

        // 根据椭球类型和投影类型选择计算方法
        switch (selectedEllipsoidIndex) {
            case 1: // WGS84
                if (isUTM) {
                    // WGS84 + UTM投影
                    return CentralMeridianCalculation.calculateForUTM(lon, lat);
                } else {
                    // WGS84 + 高斯投影（统一使用3度带）
                    return CentralMeridianCalculation.calculateForGaussKruger3Degree(lon, lat);
                }

            case 0: // CGCS2000
                // CGCS2000高斯投影（统一使用3度带）
                return CentralMeridianCalculation.calculateForGaussKruger3Degree(lon, lat);

            case 2: // 北京54
                // 北京54高斯投影（统一使用3度带）
                return CentralMeridianCalculation.calculateForGaussKruger3Degree(lon, lat);

            case 3: // 西安80
                // 西安80高斯投影（统一使用3度带）
                return CentralMeridianCalculation.calculateForGaussKruger3Degree(lon, lat);

            default:
                // 默认使用高斯3度带
                return CentralMeridianCalculation.calculateForGaussKruger3Degree(lon, lat);
        }
    }

    /**
     * 显示带号选择对话框，让用户选择偏移量较小的带号
     */
    private void showZoneSelectionDialog(double lat, double lon, ProjectionInfo currentProjection) {
        // 计算多个带号选项
        List<ProjectionOption> options = new ArrayList<>();

        // 当前选择的带号
        int currentZone = currentProjection.zone;
        double currentOffset = 500000.0; // 标准偏移量

        // 3度带选项（统一使用3度带）
        int zone3 = (int) (lon / 3) + 1;
        double centralMeridian3 = zone3 * 3;
        double distanceFromCM3 = Math.abs(lon - centralMeridian3);
        double offset3 = 500000.0; // 标准偏移量（米）
        options.add(new ProjectionOption("GAUSS3", zone3, centralMeridian3, distanceFromCM3, offset3));

        // 相邻带号选项（如果偏移量更小）
        if (currentZone > 1) {
            int zonePrev = currentZone - 1;
            double centralMeridianPrev = zonePrev * 3;
            double distanceFromCMPrev = Math.abs(lon - centralMeridianPrev);
            double offsetPrev = 500000.0; // 标准偏移量（米）
            options.add(new ProjectionOption("GAUSS3", zonePrev, centralMeridianPrev, distanceFromCMPrev, offsetPrev));
        }

        if (currentZone < 60) {
            int zoneNext = currentZone + 1;
            double centralMeridianNext = zoneNext * 3;
            double distanceFromCMNext = Math.abs(lon - centralMeridianNext);
            double offsetNext = 500000.0; // 标准偏移量（米）
            options.add(new ProjectionOption("GAUSS3", zoneNext, centralMeridianNext, distanceFromCMNext, offsetNext));
        }

        // 按偏移量排序
        options.sort((a, b) -> Double.compare(a.offset, b.offset));

        // 构建选项文本
        StringBuilder optionsText = new StringBuilder();
        optionsText.append("检测到当前带号偏移量较大，建议选择偏移量较小的选项：\n\n");

        for (int i = 0; i < Math.min(options.size(), 5); i++) {
            ProjectionOption option = options.get(i);
            optionsText.append(String.format("%d. %s Zone %d\n", i + 1, option.type, option.zone));
            optionsText.append(String.format("   中央子午线: %.1f°\n", option.centralMeridian));
            optionsText.append(String.format("   距离: %.3f°\n", option.distanceFromCM));
            optionsText.append(String.format("   偏移量: %.0f米\n\n", option.offset));
        }

        optionsText.append("请选择带号（推荐选择偏移量最小的选项）：");

        // 取消弹窗：直接采用推荐结果
        ProjectionOption bestOption = options.get(0);
        ProjectionInfo newProjection = new ProjectionInfo(
                bestOption.type, bestOption.zone, bestOption.centralMeridian, "CGCS2000"
        );
        applyProjectionAndUpdateUI(newProjection, "自动", 0, 0);
    }
    /**
     * 显示手动带号选择对话框
     */
    private void showManualZoneSelectionDialog(List<ProjectionOption> options) {
        // 不再显示手动选择弹窗，保留方法以兼容调用方
        if (options == null || options.isEmpty()) return;
        ProjectionOption selectedOption = options.get(0);
        ProjectionInfo newProjection = new ProjectionInfo(
                selectedOption.type, selectedOption.zone, selectedOption.centralMeridian, "CGCS2000"
        );
        applyProjectionAndUpdateUI(newProjection, "手动", 0, 0);
    }

    /**
     * 手动优化带号选择，减少东坐标偏移量
     * 已移除RTK自动获取功能，需要手动输入坐标
     */
    private void optimizeZoneSelection() {
        // 已移除RTK自动获取功能
        Toast.makeText(this, "请手动输入坐标进行优化", Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示带号优化建议对话框
     */
    private void showZoneOptimizationDialog(List<ProjectionOption> options, double lat, double lon) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("当前位置: %.6f°E, %.6f°N\n\n", lon, lat));
        message.append("带号优化建议（按偏移量从小到大排序）：\n\n");

        for (int i = 0; i < Math.min(options.size(), 6); i++) {
            ProjectionOption option = options.get(i);
            message.append(String.format("%d. %s Zone %d\n", i + 1, option.type, option.zone));
            message.append(String.format("   中央子午线: %.1f°\n", option.centralMeridian));
            message.append(String.format("   距离: %.3f°\n", option.distanceFromCM));
            message.append(String.format("   偏移量: %.0f米\n", option.offset));

            // 标记推荐选项
            if (i == 0) {
                message.append("   ⭐ 推荐（偏移量最小）\n");
            } else if (option.offset < 20000000) {
                message.append("   ✅ 偏移量适中\n");
            } else {
                message.append("   ⚠️ 偏移量较大\n");
            }
            message.append("\n");
        }

        message.append("选择偏移量较小的带号可以：\n");
        message.append("• 减少东坐标的数值大小\n");
        message.append("• 提高坐标的直观性\n");
        message.append("• 减少计算误差\n");

        new AlertDialog.Builder(this)
                .setTitle("带号优化建议")
                .setMessage(message.toString())
                .setPositiveButton("使用推荐选项", (dialog, which) -> {
                    ProjectionOption bestOption = options.get(0);
                    ProjectionInfo newProjection = new ProjectionInfo(
                            bestOption.type, bestOption.zone, bestOption.centralMeridian, "CGCS2000"
                    );
                    applyProjectionAndUpdateUI(newProjection, "手动优化", 0, 0);
                })
                .setNegativeButton("手动选择", (dialog, which) -> {
                    showManualZoneSelectionDialog(options);
                })
                .setNeutralButton("取消", null)
                .show();
    }

    /**
     * 应用投影参数并更新UI
     */
    private void applyProjectionAndUpdateUI(ProjectionInfo projection, String source, double lon, double lat) {
        // 更新UI
        etCentralMeridian.setText(String.format("%.8f", projection.centralMeridian));

        // 保存投影参数到数据库
        saveProjectionParameters(projection.type, projection.zone,
                projection.centralMeridian, projection.ellipsoid);
    }

    /**
     * 投影选项类，用于智能选择算法
     */
    private static class ProjectionOption {
        String type;
        int zone;
        double centralMeridian;
        double distanceFromCM;
        double offset;

        ProjectionOption(String type, int zone, double centralMeridian, double distanceFromCM, double offset) {
            this.type = type;
            this.zone = zone;
            this.centralMeridian = centralMeridian;
            this.distanceFromCM = distanceFromCM;
            this.offset = offset;
        }
    }
    /**
     * 根据单个位置计算最佳投影方法（智能选择）
     * 优化版本：减少东坐标偏移量，优先选择偏移量较小的带号
     */
    private ProjectionInfo calculateOptimalProjectionForLocation(double lat, double lon) {

        // 验证输入参数的有效性
        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isInfinite(lat) || Double.isInfinite(lon)) {
            // 如果输入无效，返回默认值
            return new ProjectionInfo("GAUSS3", 38, 114.0, "CGCS2000");
        }

        // 智能选择逻辑：优先考虑中国常用的投影系统
        if (lon >= 70 && lon <= 140 && lat >= 10 && lat <= 55) {
            // 中国境内，优先使用CGCS2000高斯-克吕格投影

            // 计算多个带号选项，考虑偏移量影响
            List<ProjectionOption> options = new ArrayList<>();

            // 3度带选项
            int zone3 = (int) (lon / 3) + 1;
            double centralMeridian3 = zone3 * 3;
            double distanceFromCM3 = Math.abs(lon - centralMeridian3);
            double offset3 = 500000.0; // 标准偏移量（米）
            options.add(new ProjectionOption("GAUSS3", zone3, centralMeridian3, distanceFromCM3, offset3));

            // 6度带选项
            int zone6 = (int) ((lon + 3) / 6) + 1;
            double centralMeridian6 = zone6 * 6 - 3;
            double distanceFromCM6 = Math.abs(lon - centralMeridian6);
            double offset6 = 500000.0; // 标准偏移量（米）
            options.add(new ProjectionOption("GAUSS6", zone6, centralMeridian6, distanceFromCM6, offset6));

            // 尝试找到偏移量更小的相邻带号
            // 检查zone3-1（如果存在）
            if (zone3 > 1) {
                int zone3Prev = zone3 - 1;
                double centralMeridian3Prev = zone3Prev * 3;
                double distanceFromCM3Prev = Math.abs(lon - centralMeridian3Prev);
                double offset3Prev = 500000.0;
                options.add(new ProjectionOption("GAUSS3", zone3Prev, centralMeridian3Prev, distanceFromCM3Prev, offset3Prev));
            }

            // 检查zone3+1（如果存在）
            if (zone3 < 60) {
                int zone3Next = zone3 + 1;
                double centralMeridian3Next = zone3Next * 3;
                double distanceFromCM3Next = Math.abs(lon - centralMeridian3Next);
                double offset3Next = 500000.0;
                options.add(new ProjectionOption("GAUSS3", zone3Next, centralMeridian3Next, distanceFromCM3Next, offset3Next));
            }

            // 验证计算结果的有效性
            for (ProjectionOption option : options) {
                if (Double.isNaN(option.centralMeridian) || Double.isInfinite(option.centralMeridian)) {
                    options.remove(option);
                }
            }

            if (options.isEmpty()) {
                // 如果所有选项都无效，返回默认值
                return new ProjectionInfo("GAUSS3", 38, 114.0, "CGCS2000");
            }

            // 智能选择规则（优化版本）：
            // 1. 优先选择距离中央子午线最近的选项
            // 2. 在距离相近的情况下（差距<0.5°），优先选择偏移量较小的选项
            // 3. 如果6度带距离更近且偏移量明显更小，选择6度带

            ProjectionOption bestOption = options.get(0);
            double minScore = Double.MAX_VALUE;

            for (ProjectionOption option : options) {
                // 综合评分：距离权重70%，偏移量权重30%
                double distanceScore = option.distanceFromCM * 0.7;
                double offsetScore = (option.offset / 1000000.0) * 0.3; // 归一化偏移量
                double totalScore = distanceScore + offsetScore;

                if (totalScore < minScore) {
                    minScore = totalScore;
                    bestOption = option;
                }
            }


            // 如果偏移量过大，提示用户考虑手动选择
            if (bestOption.offset > 20000000) { // 超过2000万米
                Log.w(TAG_PROJECTION_CALC, "选择的带号" + bestOption.zone + "偏移量较大，建议手动选择");
            }

            return new ProjectionInfo(bestOption.type, bestOption.zone, bestOption.centralMeridian, "CGCS2000");

        } else {
            // 中国境外或特殊情况，使用UTM投影
            int zone = (int) ((lon + 180) / 6) + 1;
            double centralMeridian = zone * 6 - 183;

            // 验证UTM计算结果
            if (Double.isNaN(centralMeridian) || Double.isInfinite(centralMeridian)) {
                return new ProjectionInfo("UTM", 50, 117.0, "WGS84");
            }

            return new ProjectionInfo("UTM", zone, centralMeridian, "WGS84");
        }
    }

    // 统一设置弹窗标题和按钮样式
    private void styleDialog(AlertDialog dlg){
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        View tv = dlg.findViewById(titleId);
        if(tv instanceof TextView){
            TextView t=(TextView)tv;
            t.setGravity(Gravity.CENTER);
            t.setTypeface(null, Typeface.BOLD);
        }
        Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neg = dlg.getButton(AlertDialog.BUTTON_NEGATIVE);
        if(pos!=null){ pos.setBackgroundColor(0xFF000000); pos.setTextColor(0xFFFFFFFF); }
        if(neg!=null){ neg.setBackgroundColor(0xFFFFFFFF); neg.setTextColor(0xFF000000); }
        if(pos!=null && neg!=null){
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f);
            pos.setLayoutParams(lp);
            neg.setLayoutParams(lp);
        }
    }

    private void showDatumDialog() {
        final String[] items = {"无参数", "七参数"};
        final int[] tmp = {selectedProjectionIndex};
        int width = getResources().getDisplayMetrics().widthPixels / 3;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                v.setBackgroundColor(pos == tmp[0] ? 0xFFE0E0E0 : 0x00000000);
                return v;
            }
        };

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("基准转换")
                .setAdapter(adapter, null)
                .setPositiveButton("保存", (d,w)->{
                    selectedProjectionIndex = tmp[0];
                    ((TextView)findViewById(R.id.tv_datum_transform))
                            .setText(items[selectedProjectionIndex]);
                    updateDatumPanelVisibility();

                    // 当选择七参数时，自动设置高程拟合为无参数
                    if (selectedProjectionIndex == 1) { // 七参数
                        selectedHeightFitIndex = 0; // 无参数
                        updateHeightFitDisplay();

                        // 当选择七参数时，自动设置平面校正为无参数
                        selectedPlaneIndex = 0; // 无参数
                        ((TextView)findViewById(R.id.tv_plane_correction))
                                .setText("无参数");
                        updatePlanePanelVisibility();
                    }

                    // 当选择无参数时，如果高程拟合也是无参数，则自动改为加权平均
                    if (selectedProjectionIndex == 0) { // 无参数
                        if (selectedHeightFitIndex == 0) {
                            selectedHeightFitIndex = 4; // 改为加权平均
                            updateHeightFitDisplay();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        styleDialog(dlg);

        dlg.getListView().setOnItemClickListener(
                (p,v,pos,id)->{ tmp[0]=pos; adapter.notifyDataSetChanged(); });

        Window win = dlg.getWindow();
        if (win != null) {
            win.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
            win.setGravity(Gravity.END);
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void showHeightFitDialog() {
        // 检查是否为七参数模式
        if (selectedProjectionIndex == 1) { // 七参数
            return;
        }

        // 检查基准转换是否为无参数，如果是则不允许高程拟合选择无参数
        if (selectedProjectionIndex == 0) { // 基准转换选择无参数
            // 如果当前高程拟合也是无参数，则强制改为加权平均
            if (selectedHeightFitIndex == 0) {
                selectedHeightFitIndex = 4; // 改为加权平均
            }
        }

        final String[] items = {"无参数", "垂直平差", "平面拟合", "曲面拟合", "加权平均"};
        // 索引直接对应：0=无参数, 1=垂直平差, 2=平面拟合, 3=曲面拟合, 4=加权平均
        final int[] tmp = {selectedHeightFitIndex};
        int width = getResources().getDisplayMetrics().widthPixels / 3;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                // 如果基准转换选择无参数，则禁用"无参数"选项
                if (selectedProjectionIndex == 0 && pos == 0) {
                    v.setBackgroundColor(0xFFCCCCCC); // 灰色背景表示禁用
                    v.setEnabled(false);
                } else {
                    v.setBackgroundColor(pos == tmp[0] ? 0xFFE0E0E0 : 0x00000000);
                    v.setEnabled(true);
                }
                return v;
            }
        };

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("高程拟合")
                .setAdapter(adapter, null)
                .setPositiveButton("保存", (d,w) -> {
                    // 索引直接对应：0=无参数, 1=垂直平差, 2=平面拟合, 3=曲面拟合, 4=加权平均
                    selectedHeightFitIndex = tmp[0];
                    ((TextView)findViewById(R.id.tv_height_fit))
                            .setText(items[tmp[0]]);
                    updateHeightPanelVisibility();
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        styleDialog(dlg);
        dlg.getListView().setOnItemClickListener((p,v,pos,id)->{
            // 如果基准转换选择无参数，则不允许选择"无参数"选项
            if (selectedProjectionIndex == 0 && pos == 0) {
                return;
            }
            tmp[0] = pos;
            adapter.notifyDataSetChanged();
        });

        Window win = dlg.getWindow();
        if (win != null) {
            win.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
            win.setGravity(Gravity.END);
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    /**
     * 显示高斯投影智能选择说明
     */
    private void showProjectionModelSelection() {
        String ellipsoid = getSelectedEllipsoidName();

        String message = "选择投影模型\n\n" +
                "投影模型用于将地理坐标（经纬度）转换为平面坐标（XY）\n\n" +
                "当前椭球: " + ellipsoid + "\n" +
                "推荐投影模型:\n\n" +
                "🌐 高斯-克吕格投影\n" +
                "• 适用于: CGCS2000、北京54、西安80\n" +
                "• 特点: 保形投影，适合中小范围测量\n" +
                "• 分带: 3度带或6度带\n\n" +
                "🗺️ UTM投影\n" +
                "• 适用于: WGS84\n" +
                "• 特点: 通用横墨卡托投影\n" +
                "• 分带: 6度带，全球通用\n\n" +
                "选择投影模型后，将引导您确认控制点数据";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("投影模型选择");
        builder.setMessage(message);

        if ("CGCS2000".equals(ellipsoid)) {
            // 当椭球为CGCS2000时，仅允许高斯投影
            builder.setPositiveButton("高斯投影", (d, w) -> {
                selectProjectionModel("Gauss", "高斯投影");
                checkControlPointsAndProceed("Gauss");
            });
        } else if ("WGS84".equals(ellipsoid)) {
            // WGS84推荐UTM
            builder.setPositiveButton("UTM投影", (d, w) -> {
                selectProjectionModel("UTM", "UTM投影 (通用横墨卡托)");
                checkControlPointsAndProceed("UTM");
            });
        } else {
            // 其他椭球默认仅提供高斯
            builder.setPositiveButton("高斯投影", (d, w) -> {
                selectProjectionModel("Gauss", "高斯投影");
                checkControlPointsAndProceed("Gauss");
            });
        }

        builder.setNeutralButton("取消", null);
        builder.show();
    }

    /**
     * 检查控制点数据并决定下一步操作
     */
    private void checkControlPointsAndProceed(String projectionType) {
        // 直接进行投影参数计算，不再弹窗检查坐标系名称是否存在
        calculateProjectionParameters(projectionType);
    }

    // 保存待处理的投影类型
    private String pendingProjectionType = null;

    /**
     * 跳转到点校正标签页
     */
    private void switchToPointCorrectionTab() {
        // 切换到点校正标签页
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        if (tabLayout != null && tabLayout.getTabCount() > 1) {
            TabLayout.Tab pointCorrectionTab = tabLayout.getTabAt(1); // 假设点校正是第二个标签
            if (pointCorrectionTab != null) {
                pointCorrectionTab.select();
                Toast.makeText(this, "请在点校正界面输入控制点数据", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 检查是否有待处理的投影参数计算
     */
    private void checkPendingProjectionCalculation() {
        if (pendingProjectionType != null) {
            // 检查当前是否在点校正界面
            TabLayout tabLayout = findViewById(R.id.tab_layout);
            if (tabLayout != null && tabLayout.getSelectedTabPosition() == 1) {
                // 在点校正界面，检查是否有足够的控制点
                List<ControlPoint> controlPoints = getControlPointsForCurrentProject();
                if (controlPoints != null && controlPoints.size() >= 2) {
                    // 有足够的控制点，询问是否开始计算
                    showPendingCalculationDialog();
                }
            }
        }
    }

    /**
     * 显示待处理计算的对话框
     */
    private void showPendingCalculationDialog() {
        String projectionName = "UTM".equals(pendingProjectionType) ? "UTM投影" : "高斯-克吕格投影";

        String message = "检测到您之前选择了 " + projectionName + "\n\n" +
                "现在已有足够的控制点数据，是否开始计算投影参数？";

        new AlertDialog.Builder(this)
                .setTitle("继续投影参数计算")
                .setMessage(message)
                .setPositiveButton("开始计算", (d, w) -> {
                    String projectionType = pendingProjectionType;
                    pendingProjectionType = null; // 清除待处理状态

                    // 切换回参数设置界面
                    TabLayout tabLayout = findViewById(R.id.tab_layout);
                    if (tabLayout != null && tabLayout.getTabCount() > 0) {
                        TabLayout.Tab parameterTab = tabLayout.getTabAt(0);
                        if (parameterTab != null) {
                            parameterTab.select();
                        }
                    }

                    // 开始计算投影参数
                    calculateProjectionParameters(projectionType);
                })
                .setNegativeButton("稍后计算", (d, w) -> {
                    pendingProjectionType = null; // 清除待处理状态
                })
                .show();
    }

    /**
     * 选择投影模型并更新UI显示
     */
    private void selectProjectionModel(String modelType, String modelName) {
        // 更新投影模型显示
        TextView tvProjectionModel = findViewById(R.id.tv_projection_model);
        if (tvProjectionModel != null) {
            tvProjectionModel.setText(modelName);
        }
    }

    /**
     * 根据选择的投影模型计算参数
     */
    private void calculateProjectionParameters(String projectionType) {

        try {
            // 1. 获取控制点数据
            List<ControlPoint> controlPoints = getControlPointsForCurrentProject();

            if (controlPoints == null || controlPoints.size() < 2) {
                // 控制点不足时，显示提示但不阻止计算
                Toast.makeText(this, "控制点不足，将使用默认参数进行计算", Toast.LENGTH_SHORT).show();
                // 可以在这里设置默认的控制点或继续使用现有数据
            }

            // 2. 验证坐标系统
            String ellipsoid = getSelectedEllipsoidName();
            if (!validateCoordinateSystem(controlPoints, ellipsoid)) {
                Toast.makeText(this, "控制点坐标验证失败，请检查坐标范围", Toast.LENGTH_LONG).show();
                return;
            }

            // 3. 确定投影区域
            ProjectionRegion region = determineProjectionRegion(controlPoints);

            // 4. 根据投影类型计算参数
            OptimalProjectionParams optimalParams;
            if ("UTM".equals(projectionType)) {
                optimalParams = calculateUTMProjectionParameters(controlPoints, region);
            } else {
                optimalParams = calculateGaussProjectionParameters(controlPoints, region);
            }

            // 5. 计算投影面高
            double projectionHeight = calculateProjectionHeight(controlPoints);

            // 6. 验证已知平面坐标（如果有的话）
            validateKnownPlaneCoordinates(controlPoints, optimalParams);

            // 7. 自动设置UI参数
            setGaussProjectionParametersToUI(optimalParams, projectionHeight, region);

            // 8. 显示计算结果
            showCalculationResults(optimalParams, projectionHeight, region);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "投影参数计算失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }



    /**
     * 设置整行点击事件，提高用户体验
     */
    private void setupRowClickListeners() {
        // 椭球名称行点击事件
        setupEllipsoidRowClick();

        // 投影模型行点击事件
        setupProjectionRowClick();

        // 基准转换行点击事件
        setupDatumRowClick();
    }

    /**
     * 设置椭球名称行点击事件
     */
    private void setupEllipsoidRowClick() {
        // 方法1: 尝试通过ID查找整行
        View rowEllipsoid = findViewById(R.id.row_ellipsoid_name);
        if (rowEllipsoid != null) {
            rowEllipsoid.setOnClickListener(v -> showEllipsoidDialog());
            return;
        }

        // 方法2: 通过TextView查找父布局
        TextView tvEllipsoid = findViewById(R.id.tv_ellipsoid_name);
        if (tvEllipsoid != null && tvEllipsoid.getParent() instanceof ViewGroup) {
            ViewGroup parentRow = (ViewGroup) tvEllipsoid.getParent();
            parentRow.setOnClickListener(v -> showEllipsoidDialog());
            // 添加点击效果
            parentRow.setBackground(getDrawable(android.R.drawable.list_selector_background));
        }
    }

    /**
     * 设置投影模型行点击事件
     * 已移除点击弹出弹窗功能，投影模型现在通过椭球选择自动确定
     */
    private void setupProjectionRowClick() {
        // 投影模型行不再可点击，投影类型通过椭球选择自动确定
        // 移除所有点击事件和点击效果
    }
    /**
     * 设置基准转换行点击事件
     */
    private void setupDatumRowClick() {
        // 方法1: 尝试通过ID查找整行
        View rowDatum = findViewById(R.id.row_datum_transform);
        if (rowDatum != null) {
            rowDatum.setOnClickListener(v -> showDatumDialog());
            return;
        }

        // 方法2: 通过TextView查找父布局
        TextView tvDatum = findViewById(R.id.tv_datum_transform);
        if (tvDatum != null && tvDatum.getParent() instanceof ViewGroup) {
            ViewGroup parentRow = (ViewGroup) tvDatum.getParent();
            parentRow.setOnClickListener(v -> showDatumDialog());
            // 添加点击效果
            parentRow.setBackground(getDrawable(android.R.drawable.list_selector_background));
        }
    }

    /**
     * 获取当前经度用于演示
     */
    private String getCurrentLongitudeForDemo() {
        try {
            // 从中央子午线输入框获取
            if (etCentralMeridian != null && !etCentralMeridian.getText().toString().trim().isEmpty()) {
                try {
                    double cm = Double.parseDouble(etCentralMeridian.getText().toString().trim());
                    return String.format("%.6f", cm);
                } catch (NumberFormatException ignored) {}
            }

            return "120.790497"; // 默认示例经度
        } catch (Exception e) {
            return "120.790497"; // 默认示例经度
        }
    }
    /**
     * 获取高斯投影选择原因说明
     */
    private String getGaussSelectionReason(String longitude) {
        try {
            double lon = Double.parseDouble(longitude);

            // 模拟当前选择的投影类型
            String currentProjection = "";
            TextView tvProjection = findViewById(R.id.tv_projection_model);
            if (tvProjection != null) {
                currentProjection = tvProjection.getText().toString();
            }

            if (currentProjection.contains("3度带")) {
                int zone = (int) (lon / 3) + 1;
                double centralMeridian = zone * 3;
                return String.format("当前经度: %s°\n" +
                                "选择结果: 高斯3度带投影\n" +
                                "投影带号: Zone %d\n" +
                                "中央子午线: %.0f°E\n" +
                                "原因: 适合小范围高精度测量",
                        longitude, zone, centralMeridian);
            } else if (currentProjection.contains("6度带")) {
                int zone = (int) ((lon + 3) / 6) + 1;
                double centralMeridian = zone * 6 - 3;
                return String.format("当前经度: %s°\n" +
                                "选择结果: 高斯6度带投影\n" +
                                "投影带号: Zone %d\n" +
                                "中央子午线: %.0f°E\n" +
                                "原因: 平衡精度和覆盖范围",
                        longitude, zone, centralMeridian);
            } else if (currentProjection.contains("UTM")) {
                int zone = (int) ((lon + 180) / 6) + 1;
                double centralMeridian = zone * 6 - 183;
                return String.format("当前经度: %s°\n" +
                                "选择结果: UTM投影\n" +
                                "投影带号: Zone %d\n" +
                                "中央子午线: %.0f°E\n" +
                                "原因: 适合大范围跨带测量",
                        longitude, zone, centralMeridian);
            } else {
                return String.format("当前经度: %s°\n" +
                        "系统将根据测量范围自动选择最佳投影类型", longitude);
            }
        } catch (Exception e) {
            return "系统将根据实际测量位置自动选择最佳投影类型";
        }
    }

    private void showPlaneDialog() {
        // 检查是否为七参数模式
        if (selectedProjectionIndex == 1) { // 七参数
            return;
        }

        final String[] items = {"无参数", "三参数", "四参数", "平面平差"};
        final int[] tmp = {selectedPlaneIndex};
        int width = getResources().getDisplayMetrics().widthPixels / 3;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                v.setBackgroundColor(pos == tmp[0] ? 0xFFE0E0E0 : 0x00000000);
                return v;
            }
        };

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("平面校正")
                .setAdapter(adapter, null)
                .setPositiveButton("保存", (d,w)->{
                    selectedPlaneIndex = tmp[0];
                    ((TextView)findViewById(R.id.tv_plane_correction))
                            .setText(items[selectedPlaneIndex]);
                    updatePlanePanelVisibility();
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        styleDialog(dlg);

        dlg.getListView().setOnItemClickListener(
                (p,v,pos,id)->{ tmp[0]=pos; adapter.notifyDataSetChanged(); });

        Window win = dlg.getWindow();
        if (win != null) {
            win.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
            win.setGravity(Gravity.END);
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void setupTabLayout() {
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int position = tab.getPosition();
                    if (position == 0) {
                        // 坐标系标签页
                        showCoordinateSystemContent();
                    } else if (position == 1) {
                        // 点校正标签页 - 检查坐标系是否已保存
                        if (currentCsId <= 0) {
                            // 坐标系未保存，显示提示并阻止切换
                            new AlertDialog.Builder(ParameterSettingsActivity.this)
                                    .setTitle("提示")
                                    .setMessage("请先在坐标系界面保存坐标系统，保存后才能进入点校正界面")
                                    .setPositiveButton("确定", null)
                                    .show();
                            // 切换回坐标系标签页
                            tabLayout.selectTab(tabLayout.getTabAt(0));
                        } else {
                            // 坐标系已保存，允许进入点校正
                            showPointCorrectionContent();
                        }
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                    // 离开点校正标签页前自动保存
                    if (tab != null && tab.getPosition() == 1 && !isLoadingFromDatabase) {
                        saveKnownPoints();
                    }
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }
    }

    private void showCoordinateSystemContent() {
        // 显示左侧菜单
        leftMenu.setVisibility(View.VISIBLE);

        // 显示保存按钮（用于保存坐标系参数）
        btnSave.setVisibility(View.VISIBLE);

        // 恢复原始的坐标系内容
        scrollView.removeAllViews();
        scrollView.addView(coordinateSystemContent);
    }

    private void showPointCorrectionContent() {

        // 隐藏左侧菜单
        leftMenu.setVisibility(View.GONE);

        // 隐藏保存按钮（点校正界面有自己的保存按钮）
        btnSave.setVisibility(View.GONE);

        // 加载点校正布局
        if (pointCorrectionContent == null) {
            LayoutInflater inflater = LayoutInflater.from(this);
            pointCorrectionContent = inflater.inflate(R.layout.layout_point_correction, null);
            setupPointCorrectionListeners();
            attachClearButtonsRecursive(pointCorrectionContent);

            // 如果是编辑模式且已有已知点数据，重新填充界面
            if (currentCsId > 0 && !knownPointValues.isEmpty()) {
                refreshKnownPointsDisplay();
                // 确保第一个已知点被选中并显示数据
                if (!knownPointValues.isEmpty()) {
                    java.util.List<String> sortedPointNames = new java.util.ArrayList<>(knownPointValues.keySet());
                    java.util.Collections.sort(sortedPointNames);
                    String firstPointName = sortedPointNames.get(0);
                    TextView firstPointView = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
                    if (firstPointView != null) {
                        firstPointView.post(() -> selectKnownPoint(firstPointView));
                    } else {
                        Toast.makeText(this, "警告: 找不到第一个已知点视图", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "非编辑模式或没有已知点数据", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 如果界面已存在，检查是否需要刷新数据
            if (currentCsId > 0 && !knownPointValues.isEmpty()) {
                refreshKnownPointsDisplay();
                // 确保第一个已知点被选中并显示数据
                if (!knownPointValues.isEmpty()) {
                    java.util.List<String> sortedPointNames = new java.util.ArrayList<>(knownPointValues.keySet());
                    java.util.Collections.sort(sortedPointNames);
                    String firstPointName = sortedPointNames.get(0);
                    TextView firstPointView = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
                    if (firstPointView != null) {
                        firstPointView.post(() -> selectKnownPoint(firstPointView));
                    }
                }
            } else if (currentCsId > 0 && knownPointValues.isEmpty()) {
                // 已进入点校正，但内存没有点数据，则主动加载数据库
                loadKnownPointsData(currentCsId);
            }
        }

        scrollView.removeAllViews();
        scrollView.addView(pointCorrectionContent);
    }

    private void setupPointCorrectionListeners() {
        if (pointCorrectionContent == null) return;

        // container for known points
        containerKnownPoints = pointCorrectionContent.findViewById(R.id.container_known_points);
        if(containerKnownPoints!=null){
            knownPointCount = containerKnownPoints.getChildCount();

            // 只有在非编辑模式下才初始化默认已知点
            if(knownPointValues.isEmpty()){
                // 动态初始化所有已知点的数据存储
                for(int i = 0; i < containerKnownPoints.getChildCount(); i++){
                    View child = containerKnownPoints.getChildAt(i);
                    String pointName = "";

                    if(child instanceof LinearLayout){
                        if(((LinearLayout)child).getChildCount() > 0 &&
                                ((LinearLayout)child).getChildAt(0) instanceof TextView){
                            TextView tv = (TextView) ((LinearLayout)child).getChildAt(0);
                            String text = tv.getText().toString();
                            if(text.startsWith("已知点")){
                                pointName = text;
                            }
                        }
                    } else if(child instanceof TextView) {
                        TextView tv = (TextView) child;
                        String text = tv.getText().toString();
                        if(text.startsWith("控制点")){
                            pointName = text;
                        }
                    }

                    if(!pointName.isEmpty()){
                        knownPointValues.put(pointName, new String[]{"", "", ""});
                    }
                }

                // 如果没有找到已知点，初始化默认的两个点
                if(knownPointValues.isEmpty()){
                    knownPointValues.put("控制点1", new String[]{"", "", ""});
                    knownPointValues.put("控制点2", new String[]{"", "", ""});
                }
            }
        }

        // 设置坐标类型Spinner
        Spinner spinnerCoordType = pointCorrectionContent.findViewById(R.id.spinner_coordinate_type);
        if (spinnerCoordType != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.coordinate_types, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCoordType.setAdapter(adapter);

            // 设置选择监听器
            spinnerCoordType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateCoordinateLabels(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        // 已知点列表点击事件
        TextView knownPoint1 = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
        TextView knownPoint2 = pointCorrectionContent.findViewById(R.id.tv_known_point_2);

        if (knownPoint1 != null) {
            knownPoint1.setOnClickListener(v -> selectKnownPoint(knownPoint1));
            // 如果有数据库数据，稍后在 refreshKnownPointsDisplay/populateKnownPointsData 中会默认选中
            // 否则首次进入时默认选中第一个静态项
            if (knownPointValues.isEmpty()) {
                selectKnownPoint(knownPoint1);
            }
        }
        if (knownPoint2 != null) {
            knownPoint2.setOnClickListener(v -> selectKnownPoint(knownPoint2));
        }

        // 计算按钮
        Button btnCalcPoint = pointCorrectionContent.findViewById(R.id.btn_calc_point);
        if(btnCalcPoint != null){
            btnCalcPoint.setOnClickListener(v -> {
                // 计算前先保存，以便使用数据库中的最新点数据
                if (!isLoadingFromDatabase) {
                    saveKnownPoints();
                }
                showKnownPointsDialog();
            });
        }

        // 添加点按钮
        Button btnAddPoint = pointCorrectionContent.findViewById(R.id.btn_add_point);
        if (btnAddPoint != null) {
            btnAddPoint.setOnClickListener(v -> addKnownPoint());
        }



        // 重置按钮
        TextView btnResetKnownPoint = pointCorrectionContent.findViewById(R.id.btn_reset_known_point);
        TextView btnResetMeasuredPoint = pointCorrectionContent.findViewById(R.id.btn_reset_measured_point);

        if (btnResetKnownPoint != null) {
            btnResetKnownPoint.setOnClickListener(v -> {
                // 重置已知点坐标输入框
                EditText etKnownN = pointCorrectionContent.findViewById(R.id.et_known_n);
                EditText etKnownE = pointCorrectionContent.findViewById(R.id.et_known_e);
                EditText etKnownH = pointCorrectionContent.findViewById(R.id.et_known_h);

                if (etKnownN != null) etKnownN.setText("");
                if (etKnownE != null) etKnownE.setText("");
                if (etKnownH != null) etKnownH.setText("");
            });
        }

        if (btnResetMeasuredPoint != null) {
            btnResetMeasuredPoint.setOnClickListener(v -> {
                // 重置测量点坐标输入框
                EditText etMeasuredB = pointCorrectionContent.findViewById(R.id.et_measured_b);
                EditText etMeasuredL = pointCorrectionContent.findViewById(R.id.et_measured_l);
                EditText etMeasuredH = pointCorrectionContent.findViewById(R.id.et_measured_h);

                if (etMeasuredB != null) etMeasuredB.setText("");
                if (etMeasuredL != null) etMeasuredL.setText("");
                if (etMeasuredH != null) etMeasuredH.setText("");
            });
        }

        // 保存按钮
        Button btnSavePoint = pointCorrectionContent.findViewById(R.id.btn_save_point);
        if (btnSavePoint != null) {
            btnSavePoint.setOnClickListener(v -> saveCoordinateSystemAndKnownPoints());
        }

        // 采集按钮和导入按钮已移除 - 用户需手动输入测量点坐标
        }
    
    
    private TextView lastKnownSel;
    private EditText etKnownN, etKnownE, etKnownH;
    private java.util.Map<String, String[]> knownPointValues = new java.util.HashMap<>();
    private java.util.Map<String, String[]> measuredPointValues = new java.util.HashMap<>();
    private java.util.Map<String, double[]> accuracyValues = new java.util.HashMap<>(); // 存储精度值

    private void selectKnownPoint(TextView selected) {
        // 保存上一点输入（若已存在）
        if(lastKnownSel!=null){
            String lastPointName = lastKnownSel.getText().toString();

            // 确保输入框引用已初始化
            if(etKnownN==null){
                etKnownN = pointCorrectionContent.findViewById(R.id.et_known_n);
                etKnownE = pointCorrectionContent.findViewById(R.id.et_known_e);
                etKnownH = pointCorrectionContent.findViewById(R.id.et_known_h);
            }

            if(etKnownN!=null&&etKnownE!=null&&etKnownH!=null){
                knownPointValues.put(lastPointName, new String[]{
                        etKnownN.getText().toString(),
                        etKnownE.getText().toString(),
                        etKnownH.getText().toString()
                });
            }
            // 同步测量值
            EditText etMeasuredB = pointCorrectionContent.findViewById(R.id.et_measured_b);
            EditText etMeasuredL = pointCorrectionContent.findViewById(R.id.et_measured_l);
            EditText etMeasuredH = pointCorrectionContent.findViewById(R.id.et_measured_h);
            if(etMeasuredB!=null && etMeasuredL!=null && etMeasuredH!=null){
                measuredPointValues.put(lastPointName, new String[]{
                        etMeasuredB.getText().toString(),
                        etMeasuredL.getText().toString(),
                        etMeasuredH.getText().toString()
                });
            }
        }

        // 重置所有已知点的选中状态
        resetAllKnownPointsSelection();

        // 设置当前选中
        selected.setBackgroundResource(R.drawable.bg_known_point_selected);
        // 保存当前右侧图标状态（叉号）
        android.graphics.drawable.Drawable rightDrawable = selected.getCompoundDrawables()[2];
        selected.setCompoundDrawablesWithIntrinsicBounds(
                getResources().getDrawable(R.drawable.ic_point_selected),
                null,
                rightDrawable,
                null
        );
        lastKnownSel = selected;

        // 获取输入框引用
        if(etKnownN==null){
            etKnownN = pointCorrectionContent.findViewById(R.id.et_known_n);
            etKnownE = pointCorrectionContent.findViewById(R.id.et_known_e);
            etKnownH = pointCorrectionContent.findViewById(R.id.et_known_h);
        }

        // 加载当前点的已有数据
        String currentPointName = selected.getText().toString();
        String[] existingValues = knownPointValues.get(currentPointName);

        if(existingValues != null && existingValues.length >= 3){
            if(etKnownN!=null){
                etKnownN.setText(existingValues[0] != null ? existingValues[0] : "");
            }
            if(etKnownE!=null){ etKnownE.setText(existingValues[1] != null ? existingValues[1] : ""); }
            if(etKnownH!=null){ etKnownH.setText(existingValues[2] != null ? existingValues[2] : ""); }
        } else {
            if(etKnownN!=null){ etKnownN.setText(""); }
            if(etKnownE!=null){ etKnownE.setText(""); }
            if(etKnownH!=null){ etKnownH.setText(""); }
            Toast.makeText(this, "未找到已知点数据: " + currentPointName, Toast.LENGTH_SHORT).show();
        }

        // 加载测量坐标
        EditText etMeasuredB = pointCorrectionContent.findViewById(R.id.et_measured_b);
        EditText etMeasuredL = pointCorrectionContent.findViewById(R.id.et_measured_l);
        EditText etMeasuredH = pointCorrectionContent.findViewById(R.id.et_measured_h);
        String[] mVals = measuredPointValues.get(currentPointName);
        if(mVals!=null && mVals.length>=3){
            if(etMeasuredB!=null){ etMeasuredB.setText(mVals[0]!=null?mVals[0]:""); }
            if(etMeasuredL!=null){ etMeasuredL.setText(mVals[1]!=null?mVals[1]:""); }
            if(etMeasuredH!=null){ etMeasuredH.setText(mVals[2]!=null?mVals[2]:""); }
        }else{
            if(etMeasuredB!=null){ etMeasuredB.setText(""); }
            if(etMeasuredL!=null){ etMeasuredL.setText(""); }
            if(etMeasuredH!=null){ etMeasuredH.setText(""); }
        }
    }
    /**
     * 🔧 直接保存三参数到数据库（不依赖界面更新）
     */
    private void saveThreeParameterToDatabase(ThreeParameterTransform threeParam) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存三参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                org.json.JSONObject planeJson = new org.json.JSONObject();
                try {
                    planeJson.put("type", 1); // 三参数
                    planeJson.put("dx", threeParam.getDx());
                    planeJson.put("dy", threeParam.getDy());
                    planeJson.put("theta", threeParam.getTheta()); // 注意：保存弧度值

                    cs.setPlaneParams(planeJson.toString());
                    csRepo.update(cs);

                    Log.d("ParameterSettings", "✓ 三参数已保存到数据库: dx=" + threeParam.getDx() + 
                            ", dy=" + threeParam.getDy() + ", theta=" + Math.toDegrees(threeParam.getTheta()) + "°");

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "三参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存三参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 🔧 直接保存四参数到数据库（不依赖界面更新）
     */
    private void saveFourParameterToDatabase(FourParameterSolver.FourParameterTransform fourParam) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存四参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                org.json.JSONObject planeJson = new org.json.JSONObject();
                try {
                    planeJson.put("type", 2); // 四参数
                    planeJson.put("dx", fourParam.getDx());
                    planeJson.put("dy", fourParam.getDy());
                    planeJson.put("theta", fourParam.getTheta()); // 注意：保存弧度值
                    planeJson.put("k", fourParam.getK());

                    cs.setPlaneParams(planeJson.toString());
                    csRepo.update(cs);

                    Log.d("ParameterSettings", "✓ 四参数已保存到数据库: dx=" + fourParam.getDx() + 
                            ", dy=" + fourParam.getDy() + ", theta=" + Math.toDegrees(fourParam.getTheta()) + 
                            "°, k=" + fourParam.getK());

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "四参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存四参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 🔧 直接保存七参数到数据库（不依赖界面更新）
     */
    private void saveSevenParameterToDatabase(SevenParameterTransform sevenParam) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存七参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                org.json.JSONObject datumJson = new org.json.JSONObject();
                try {
                    datumJson.put("type", 1); // 七参数
                    datumJson.put("dx", sevenParam.getTx());
                    datumJson.put("dy", sevenParam.getTy());
                    datumJson.put("dz", sevenParam.getTz());
                    datumJson.put("rx", sevenParam.getWx());
                    datumJson.put("ry", sevenParam.getWy());
                    datumJson.put("rz", sevenParam.getWz());
                    datumJson.put("scale", sevenParam.getM());

                    cs.setDatumParams(datumJson.toString());
                    csRepo.update(cs);

                    Log.d("ParameterSettings", "✓ 七参数已保存到数据库: dx=" + sevenParam.getTx() + 
                            ", dy=" + sevenParam.getTy() + ", dz=" + sevenParam.getTz() + 
                            ", scale=" + sevenParam.getM());

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "七参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存七参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 🔧 直接保存平面平差参数到数据库（不依赖界面更新）
     */
    private void savePlaneAdjustmentToDatabase(PlaneAdjustmentTransform planeAdjustment) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存平面平差参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                org.json.JSONObject planeJson = new org.json.JSONObject();
                try {
                    planeJson.put("type", 3); // 平面平差
                    planeJson.put("north_origin", planeAdjustment.getNorthOrigin());
                    planeJson.put("east_origin", planeAdjustment.getEastOrigin());
                    planeJson.put("north_translation", planeAdjustment.getNorthTranslation());
                    planeJson.put("east_translation", planeAdjustment.getEastTranslation());
                    planeJson.put("rotation_scale", planeAdjustment.getRotationScale());
                    planeJson.put("scale", planeAdjustment.getScale());
                    planeJson.put("rmse", planeAdjustment.getRmse());

                    cs.setPlaneParams(planeJson.toString());
                    csRepo.update(cs);

                    Log.d("ParameterSettings", "✓ 平面平差参数已保存到数据库: north_origin=" + planeAdjustment.getNorthOrigin() + 
                            ", east_origin=" + planeAdjustment.getEastOrigin() + ", rmse=" + planeAdjustment.getRmse());

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "平面平差参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存平面平差参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 🔧 保存计算出的参数到数据库 - 已废弃
     * 
     * ⚠️ 所有参数现在都通过专门的方法直接从计算对象保存，不再从界面读取
     * - 平面校正参数：saveThreeParameterToDatabase() / saveFourParameterToDatabase()
     * - 基准转换参数：saveSevenParameterToDatabase()
     * - 高程拟合参数：saveVerticalAdjustmentParameters() / savePlaneFittingParameters() 等
     * 
     * 保留此方法是为了兼容性，但实际上不再执行任何操作
     */
    @Deprecated
    private void saveCalculatedParametersToDatabase() {
        // 🔧 此方法已废弃，所有参数都通过专门的方法直接保存
        // 不再从界面读取参数，避免：
        // 1. 竞态条件（界面异步更新）
        // 2. 单位错误（角度/弧度混淆）
        // 3. 参数覆盖（错误值覆盖正确值）
        
        Log.d("ParameterSettings", "saveCalculatedParametersToDatabase() 已废弃，所有参数已通过专门方法保存");
    }
    private void resetAllKnownPointsSelection() {
        if(containerKnownPoints == null) return;

        // 遍历所有已知点并设置为未选中状态
        for(int i = 0; i < containerKnownPoints.getChildCount(); i++){
            View child = containerKnownPoints.getChildAt(i);
            TextView pointTextView = null;

            if(child instanceof LinearLayout){
                // 动态添加的已知点（包含TextView和删除按钮的LinearLayout）
                LinearLayout row = (LinearLayout) child;
                if(row.getChildCount() > 0 && row.getChildAt(0) instanceof TextView){
                    TextView tv = (TextView) row.getChildAt(0);
                    String text = tv.getText().toString();
                    // 只处理以"已知点"开头的TextView
                    if(text.startsWith("已知点")){
                        pointTextView = tv;
                    }
                }
            } else if(child instanceof TextView) {
                // 布局文件中定义的已知点（直接的TextView）
                TextView tv = (TextView) child;
                String text = tv.getText().toString();
                // 只处理以"已知点"开头的TextView
                if(text.startsWith("已知点")){
                    pointTextView = tv;
                }
            }

            if(pointTextView != null){
                pointTextView.setBackgroundResource(R.drawable.bg_known_point_unselected);
                // 保存当前右侧图标状态（叉号）
                android.graphics.drawable.Drawable rightDrawable = pointTextView.getCompoundDrawables()[2];
                pointTextView.setCompoundDrawablesWithIntrinsicBounds(
                        getResources().getDrawable(R.drawable.ic_point_unselected),
                        null,
                        rightDrawable,
                        null
                );
            }
        }
    }

    private void addKnownPoint(){
        if(containerKnownPoints==null) return;

        int nextIndex = 3; // 从 3 开始
        for(int i=0;i<containerKnownPoints.getChildCount();i++){
            View child = containerKnownPoints.getChildAt(i);
            if(child instanceof LinearLayout){
                TextView t = (TextView) ((LinearLayout)child).getChildAt(0);
                String txt = t.getText().toString();
                if(txt.startsWith("控制点")){
                    try{
                        int n = Integer.parseInt(txt.replace("控制点",""));
                        if(n>=nextIndex) nextIndex = n+1;
                    }catch (Exception ignored){}
                }
            }
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,48,getResources().getDisplayMetrics());
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        // TextView
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,48,getResources().getDisplayMetrics()));
        tv.setLayoutParams(tvLp);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,16,getResources().getDisplayMetrics()),0,0,0);
        tv.setText(String.format("控制点%d", nextIndex));
        tv.setTextSize(16);
        tv.setBackgroundResource(R.drawable.bg_known_point_unselected);
        tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_point_unselected,0,0,0);
        tv.setCompoundDrawablePadding(8);

        // 设置删除按钮为右侧的CompoundDrawable
        tv.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_point_unselected,  // 左侧图标
                0,                               // 顶部图标
                R.drawable.ic_menu_close_clear_cancel,       // 右侧图标（小叉号）
                0                                // 底部图标
        );

        // 选中逻辑：高亮该行
        tv.setOnClickListener(v -> selectKnownPoint(tv));

        // 设置删除按钮的点击事件
        tv.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                // 检查点击位置是否在右侧图标区域
                android.graphics.drawable.Drawable[] drawables = tv.getCompoundDrawables();
                if (drawables[2] != null && event.getX() > tv.getWidth() - tv.getCompoundDrawablePadding() -
                        drawables[2].getIntrinsicWidth()) {
                    // 删除对应的数据存储
                    String pointName = tv.getText().toString();
                    knownPointValues.remove(pointName);
                    measuredPointValues.remove(pointName);

                    // 如果删除的是当前选中的点，清空选中状态
                    if(lastKnownSel == tv){
                        lastKnownSel = null;
                        if(etKnownN != null) etKnownN.setText("");
                        if(etKnownE != null) etKnownE.setText("");
                        if(etKnownH != null) etKnownH.setText("");
                    }

                    containerKnownPoints.removeView(row);
                    return true;
                }
            }
            return false;
        });

        row.addView(tv);

        // 初始化新点的数据存储
        String newPointName = String.format("控制点%d", nextIndex);
        knownPointValues.put(newPointName, new String[]{"", "", ""});
        measuredPointValues.put(newPointName, new String[]{"", "", ""});

        // 找到最后一个控制点的位置，在其后插入
        int insertPos = 0;
        for(int i = 0; i < containerKnownPoints.getChildCount(); i++){
            View child = containerKnownPoints.getChildAt(i);
            if(child instanceof LinearLayout){
                TextView t = (TextView) ((LinearLayout)child).getChildAt(0);
                String txt = t.getText().toString();
                if(txt.startsWith("控制点")){
                    insertPos = i + 1; // 在最后一个控制点之后插入
                }
            } else if(child instanceof TextView) {
                TextView t = (TextView) child;
                String txt = t.getText().toString();
                if(txt.startsWith("控制点")){
                    insertPos = i + 1; // 在最后一个控制点之后插入
                }
            }
        }
        containerKnownPoints.addView(row, insertPos);
    }

    private void updateDatumPanelVisibility(){
        if(panelSevenParam!=null){
            panelSevenParam.setVisibility(selectedProjectionIndex==1? View.VISIBLE: View.GONE);
        }
    }

    private void updatePlanePanelVisibility(){
        // 0=无参数 1=三参数 2=四参数 3=平面平差
        if(panelThreeParam!=null) panelThreeParam.setVisibility(selectedPlaneIndex==1? View.VISIBLE: View.GONE);
        if(panelFourParam!=null) panelFourParam.setVisibility(selectedPlaneIndex==2? View.VISIBLE: View.GONE);
        if(panelPlaneAdjustment!=null) panelPlaneAdjustment.setVisibility(selectedPlaneIndex==3? View.VISIBLE: View.GONE);
    }

    // ─────────────────────────── 数据库保存方法 ───────────────────────────
    private void saveCoordinateSystem(){
        // 构建名称：椭球 + 时间戳
        String[] ellipsoidArr = {"CGCS2000","WGS84","北京54","西安80"};
        String ellipsoid = ellipsoidArr[selectedEllipsoidIndex];

        // 从UI推断投影类型：合并高斯3/6为“GAUSS”，UTM保持不变
        String projection = null;
        TextView tvProjection = findViewById(R.id.tv_projection_model);
        if (tvProjection != null && tvProjection.getText() != null) {
            String projText = tvProjection.getText().toString();
            if (projText.contains("UTM")) {
                projection = "UTM";
            } else if (projText.contains("高斯")) {
                projection = "GAUSS"; // 不再区分3/6带，不保存带号
            }
        }
        if (projection == null) {
            // 回退：统一使用高斯投影
            projection = "GAUSS";
        }

        // 获取中央子午线值（从UI读取）
        Double centralMeridian = null;
        String cmStr = etCentralMeridian != null ? etCentralMeridian.getText().toString().trim() : "";
        if (!cmStr.isEmpty()) {
            try {
                centralMeridian = Double.parseDouble(cmStr);
            } catch (NumberFormatException e) {
                // 解析失败
            }
        }
        Integer zone = null;

        // WGS84 + UTM: 需要带号与中央子午线
        if ("WGS84".equals(ellipsoid) && "UTM".equals(projection)) {
            // 优先读取UI中的带号，否则由中央子午线反算
            EditText etZone = findViewById(R.id.et_utm_zone);
            if (etZone != null && etZone.getText() != null && !etZone.getText().toString().trim().isEmpty()) {
                try { zone = Integer.parseInt(etZone.getText().toString().trim()); } catch (Exception ignore) {}
            }
            if (zone == null && centralMeridian != null) {
                zone = (int)Math.floor((centralMeridian + 183) / 6.0);
            }
        } else {
            // 高斯（含WGS84/CGCS2000）：不保存带号
            zone = null;
        }
        String name;
        EditText tvCoordName = findViewById(R.id.tv_coord_name);
        if(tvCoordName!=null && tvCoordName.getText()!=null && !tvCoordName.getText().toString().trim().isEmpty()){
            name = tvCoordName.getText().toString().trim();
        }else{
            // 名称为空时显示提示并返回，不允许保存
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("请输入坐标系统名称")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        // 检查坐标系名称是否已存在
        boolean nameExists;
        if (currentCsId > 0) {
            // 编辑模式：检查名称是否已存在（排除当前记录）
            nameExists = csRepo.isNameExistsExcludingId(name, currentCsId);
        } else {
            // 新建模式：检查名称是否已存在
            nameExists = csRepo.isNameExists(name);
        }

        if (nameExists) {
            // 名称已存在时显示提示并返回，不允许保存
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("坐标系名称 \"" + name + "\" 已存在，请使用其他名称")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        // 组装投影参数（包含中央子午线）
        org.json.JSONObject projJson = new org.json.JSONObject();
        try {
            projJson.put("centralMeridian", centralMeridian);
            projJson.put("zone", zone);
            // 其它投影参数
            projJson.put("originLat", getDoubleFromRow(R.id.row_origin_lat,1));
            projJson.put("scaleFactor", getDouble(R.id.et_scale_factor)!=null? getDouble(R.id.et_scale_factor): getDoubleFromRow(R.id.row_scale,1));
            projJson.put("addEast", getDouble(R.id.et_add_east)!=null? getDouble(R.id.et_add_east): getDoubleFromRow(R.id.row_add_east,1));
            projJson.put("addNorth", getDoubleFromRow(R.id.row_add_north,1));
        } catch (org.json.JSONException ignored) {}
        String projectionParams = projJson.toString();

        String[] datumTypeArr = {"NONE","SEVEN"};
        String datumType = datumTypeArr[Math.min(selectedProjectionIndex, datumTypeArr.length-1)];
        org.json.JSONObject datumJson = new org.json.JSONObject();
        // 收集七参数详细数据（7个参数）
        try {
            datumJson.put("type", selectedProjectionIndex);
            if(selectedProjectionIndex==1){ // 七参数面板
                Double dx = getDouble(R.id.tv_sp_dx);
                Double dy = getDouble(R.id.tv_sp_dy);
                Double dz = getDouble(R.id.tv_sp_dz);
                Double rx = getDouble(R.id.tv_sp_rx);
                Double ry = getDouble(R.id.tv_sp_ry);
                Double rz = getDouble(R.id.tv_sp_rz);
                Double scale = getDouble(R.id.tv_sp_s);
                // 确保所有参数都保存，即使为null也保存为0
                datumJson.put("dx", dx != null ? dx : 0.0);
                datumJson.put("dy", dy != null ? dy : 0.0);
                datumJson.put("dz", dz != null ? dz : 0.0);
                datumJson.put("rx", rx != null ? rx : 0.0);
                datumJson.put("ry", ry != null ? ry : 0.0);
                datumJson.put("rz", rz != null ? rz : 0.0);
                datumJson.put("scale", scale != null ? scale : 1.0);
            }
        }catch(org.json.JSONException ignored){}
        String datumParams = datumJson.toString();

        org.json.JSONObject planeJson = new org.json.JSONObject();
        try {
            // 如果是七参数模式，强制平面校正为无参数
            int planeTypeToSave = selectedProjectionIndex == 1 ? 0 : selectedPlaneIndex;
            planeJson.put("type", planeTypeToSave);

            if(planeTypeToSave==1){ // 三参数 - 存储3个参数
                Double dx = getDouble(R.id.tv_tp_dx);
                Double dy = getDouble(R.id.tv_tp_dy);
                Double theta = getDouble(R.id.tv_tp_theta);
                planeJson.put("dx", dx != null ? dx : 0.0);
                planeJson.put("dy", dy != null ? dy : 0.0);
                planeJson.put("theta", theta != null ? theta : 0.0);
            }else if(planeTypeToSave==2){ // 四参数 - 存储4个参数
                Double dx = getDouble(R.id.tv_fp_dx);
                Double dy = getDouble(R.id.tv_fp_dy);
                Double theta = getDouble(R.id.tv_fp_theta);
                Double k = getDouble(R.id.tv_fp_k);
                planeJson.put("dx", dx != null ? dx : 0.0);
                planeJson.put("dy", dy != null ? dy : 0.0);
                planeJson.put("theta", theta != null ? theta : 0.0);
                planeJson.put("k", k != null ? k : 1.0);
            }else if(planeTypeToSave==3){ // 平面平差 - 存储6个参数
                Double northOrigin = getDouble(R.id.tv_pa_north_origin);
                Double eastOrigin = getDouble(R.id.tv_pa_east_origin);
                Double northTranslation = getDouble(R.id.tv_pa_north_translation);
                Double eastTranslation = getDouble(R.id.tv_pa_east_translation);
                Double rotationScale = getDouble(R.id.tv_pa_rotation_scale);
                Double scale = getDouble(R.id.tv_pa_scale);
                planeJson.put("north_origin", northOrigin != null ? northOrigin : 0.0);
                planeJson.put("east_origin", eastOrigin != null ? eastOrigin : 0.0);
                planeJson.put("north_translation", northTranslation != null ? northTranslation : 0.0);
                planeJson.put("east_translation", eastTranslation != null ? eastTranslation : 0.0);
                planeJson.put("rotation_scale", rotationScale != null ? rotationScale : 0.0);
                planeJson.put("scale", scale != null ? scale : 1.0);
            }
        } catch(org.json.JSONException ignored){}
        String planeParams = planeJson.toString();

        org.json.JSONObject heightJson = new org.json.JSONObject();
        try {
            heightJson.put("type", selectedHeightFitIndex);
            switch(selectedHeightFitIndex){
                case 1: // 垂直偏差: 存储5个参数 n0,e0,nslope,eslope,const
                    Double n0 = getDouble(R.id.tv_hf_v_n0);
                    Double e0 = getDouble(R.id.tv_hf_v_e0);
                    Double nslope = getDouble(R.id.tv_hf_v_nslope);
                    Double eslope = getDouble(R.id.tv_hf_v_eslope);
                    Double constVal = getDouble(R.id.tv_hf_v_const);
                    heightJson.put("n0", n0 != null ? n0 : 0.0);
                    heightJson.put("e0", e0 != null ? e0 : 0.0);
                    heightJson.put("nslope", nslope != null ? nslope : 0.0);
                    heightJson.put("eslope", eslope != null ? eslope : 0.0);
                    heightJson.put("const", constVal != null ? constVal : 0.0);
                    break;
                case 2: // 平面拟合: 存储5个参数 a,b,c,n0,e0
                    Double a = getDouble(R.id.tv_hf_p_a);
                    Double b = getDouble(R.id.tv_hf_p_b);
                    Double c = getDouble(R.id.tv_hf_p_c);
                    Double pn0 = getDouble(R.id.tv_hf_p_n0);
                    Double pe0 = getDouble(R.id.tv_hf_p_e0);
                    heightJson.put("a", a != null ? a : 0.0);
                    heightJson.put("b", b != null ? b : 0.0);
                    heightJson.put("c", c != null ? c : 0.0);
                    heightJson.put("n0", pn0 != null ? pn0 : 0.0);
                    heightJson.put("e0", pe0 != null ? pe0 : 0.0);
                    break;
                case 3: // 曲面拟合: 存储8个参数 a-f,n0,e0
                    Double sa = getDouble(R.id.tv_hf_s_a);
                    Double sb = getDouble(R.id.tv_hf_s_b);
                    Double sc = getDouble(R.id.tv_hf_s_c);
                    Double sd = getDouble(R.id.tv_hf_s_d);
                    Double se = getDouble(R.id.tv_hf_s_e);
                    Double sf = getDouble(R.id.tv_hf_s_f);
                    Double sn0 = getDouble(R.id.tv_hf_s_n0);
                    Double se0 = getDouble(R.id.tv_hf_s_e0);
                    heightJson.put("a", sa != null ? sa : 0.0);
                    heightJson.put("b", sb != null ? sb : 0.0);
                    heightJson.put("c", sc != null ? sc : 0.0);
                    heightJson.put("d", sd != null ? sd : 0.0);
                    heightJson.put("e", se != null ? se : 0.0);
                    heightJson.put("f", sf != null ? sf : 0.0);
                    heightJson.put("n0", sn0 != null ? sn0 : 0.0);
                    heightJson.put("e0", se0 != null ? se0 : 0.0);
                    break;
                case 4: // 加权平均: 存储1个参数 a
                    Double wa = getDouble(R.id.tv_hf_w_a);
                    heightJson.put("a", wa != null ? wa : 0.0);
                    break;
            }
        }catch(org.json.JSONException ignored){}
        String heightParams = heightJson.toString();

        // 根据是否为编辑模式选择保存或更新
        if (currentCsId > 0) {
            // 编辑模式：更新现有坐标系
            CoordinateSystem existingCs = csRepo.getById(currentCsId);
            if (existingCs != null) {
                // 更新现有坐标系的属性
                existingCs.setName(name);
                existingCs.setEllipsoid(ellipsoid);
                existingCs.setProjection(projection);
                // 同步更新中央子午线，避免后续转换使用到空值
                existingCs.setCentralMeridian(centralMeridian);
                existingCs.setZone(zone);
                existingCs.setProjectionParams(projectionParams);
                existingCs.setDatumType(datumType);
                existingCs.setDatumParams(datumParams);
                existingCs.setPlaneParams(planeParams);
                existingCs.setHeightParams(heightParams);

                // 执行更新（在后台线程中执行，避免阻塞UI）
                executorService.execute(() -> {
                    boolean updateSuccess = csRepo.updateAndReturnResult(existingCs);
                    
                    runOnUiThread(() -> {
                        if (updateSuccess) {
                            Toast.makeText(this, "坐标系参数已成功保存", Toast.LENGTH_SHORT).show();
                            // 更新成功后自动跳转到点校正界面
                            if (tabLayout != null) {
                                tabLayout.selectTab(tabLayout.getTabAt(1));
                            }
                        } else {
                            Toast.makeText(this, "保存失败，请检查参数设置并重试", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                Toast.makeText(this, "更新失败：找不到要编辑的坐标系", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 新建模式：插入新坐标系
            CoordinateSystem cs = new CoordinateSystem(
                    name, ellipsoid, projection, centralMeridian, zone,
                    projectionParams, datumType, datumParams, planeParams, heightParams);
            long id = csRepo.insertAndReturnId(cs);
            if(id>0){
                currentCsId = id;
                
                // 显示保存成功提示
                Toast.makeText(this, "坐标系创建成功", Toast.LENGTH_SHORT).show();

                // 保存成功后自动跳转到点校正界面
                if (tabLayout != null) {
                    tabLayout.selectTab(tabLayout.getTabAt(1));
                }
            }else{
                // 二次校验：若名称在此刻已存在，提示名称重复
                boolean existsNow = csRepo.isNameExists(name);
                if (existsNow) {
                    new AlertDialog.Builder(this)
                            .setTitle("保存失败")
                            .setMessage("坐标系名称 \"" + name + "\" 已存在，请更换名称后重试")
                            .setPositiveButton("确定", null)
                            .show();
                } else {
                    android.util.Log.e("ParameterSettingsActivity", "保存坐标系失败: " + name);
                    Toast.makeText(this, "保存失败，请检查名称与参数有效性", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void saveKnownPoints(){
        if(currentCsId<=0){
            Toast.makeText(this, "请先保存坐标系", Toast.LENGTH_SHORT).show();
            return;
        }

        // 先删除相同坐标系下的所有已知点（覆盖机制）
        kpRepo.deleteByCs(currentCsId);

        // 先同步当前正在编辑的点
        if(lastKnownSel!=null){
            if(etKnownN!=null && etKnownE!=null && etKnownH!=null){
                knownPointValues.put(lastKnownSel.getText().toString(), new String[]{etKnownN.getText().toString(), etKnownE.getText().toString(), etKnownH.getText().toString()});
            }
            EditText etMeasuredB = pointCorrectionContent.findViewById(R.id.et_measured_b);
            EditText etMeasuredL = pointCorrectionContent.findViewById(R.id.et_measured_l);
            EditText etMeasuredH = pointCorrectionContent.findViewById(R.id.et_measured_h);
            if(etMeasuredB!=null && etMeasuredL!=null && etMeasuredH!=null){
                measuredPointValues.put(lastKnownSel.getText().toString(), new String[]{etMeasuredB.getText().toString(), etMeasuredL.getText().toString(), etMeasuredH.getText().toString()});
            }
        }

        // 保存所有已知点数据
        for(java.util.Map.Entry<String,String[]> entry: knownPointValues.entrySet()){
            String pointName = entry.getKey();
            String[] vals = entry.getValue();

            Double n=null,e=null,h=null;
            try{ n = vals[0].isEmpty()?null:Double.valueOf(vals[0]); }catch (Exception ignored){}
            try{ e = vals[1].isEmpty()?null:Double.valueOf(vals[1]); }catch (Exception ignored){}
            try{ h = vals[2].isEmpty()?null:Double.valueOf(vals[2]); }catch (Exception ignored){}
            String[] m = measuredPointValues.get(pointName);
            Double b=null,l=null,hm=null;
            if(m!=null && m.length>=3){
                try{ b = m[0].isEmpty()?null:Double.valueOf(m[0]); }catch(Exception ignored){}
                try{ l = m[1].isEmpty()?null:Double.valueOf(m[1]); }catch(Exception ignored){}
                try{ hm = m[2].isEmpty()?null:Double.valueOf(m[2]); }catch(Exception ignored){}
            }
            // 计算精度
            double horizontalAccuracy = 0.0;
            double elevationAccuracy = 0.0;

            if (n != null && e != null && h != null && b != null && l != null && hm != null) {
                // 将测量点的经纬度转换为投影坐标进行比较
                double[] projectedCoords = convertBLToProjection(b, l);
                if (projectedCoords != null && projectedCoords.length >= 2) {
                    double projectedN = projectedCoords[0];
                    double projectedE = projectedCoords[1];

                    // 计算水平精度 = √(ΔN² + ΔE²)
                    double deltaN = n - projectedN;
                    double deltaE = e - projectedE;
                    horizontalAccuracy = Math.sqrt(deltaN * deltaN + deltaE * deltaE);

                    // 计算高程精度 = |ΔH|
                    elevationAccuracy = Math.abs(h - hm);
                }
            }

            // 只有当坐标值有效时才保存
            if (n != null && e != null && h != null) {
                KnownPoint kp = new KnownPoint(currentCsId, pointName, n, e, h, b, l, hm, horizontalAccuracy, elevationAccuracy);
                long insertResult = kpRepo.insertSync(kp);

                // 更新内存中的精度值
                accuracyValues.put(pointName, new double[]{horizontalAccuracy, elevationAccuracy});

            } else {
                // 调试信息：显示跳过保存的原因
                Toast.makeText(this, "跳过保存已知点: " + pointName +
                        " (坐标值无效: n=" + n + ", e=" + e + ", h=" + h + ")", Toast.LENGTH_LONG).show();
            }
        }

        // 保存完成后检查是否有待处理的投影参数计算
        checkPendingProjectionCalculation();
    }
    /**
     * 联动保存：先保存坐标系，再保存已知点，然后计算参数
     * 确保坐标系和点校正数据的一致性
     */
    private void saveCoordinateSystemAndKnownPoints(){
        // 先保存坐标系（如果还没有保存）
        if(currentCsId <= 0){
            // 新建模式：保存坐标系并检查是否成功
            long previousCsId = currentCsId;
            saveCoordinateSystem();

            // 检查坐标系是否保存成功（通过比较ID是否发生变化）
            if(currentCsId > 0 && currentCsId != previousCsId){
                // 坐标系保存成功，继续保存已知点
                saveKnownPoints();
                // 保存完成后计算参数
                calculateParametersFromKnownPoints();
            } else {
                // 坐标系保存失败（可能是名称重复或其他原因），不继续执行
                // saveCoordinateSystem() 方法内部已经显示了相应的错误提示
                return;
            }
        } else {
            // 编辑模式：只保存已知点，不更新坐标系
            // 因为点校正界面不应该修改坐标系信息
            saveKnownPoints();
            // 保存完成后计算参数
            // 检查是否有足够的已知点进行计算
            List<KnownPoint> existingPoints = kpRepo.getByCsSync(currentCsId);
            if (existingPoints != null && existingPoints.size() >= 2) {
                calculateParametersFromKnownPoints();
            } else {
                Toast.makeText(this, "已知点已保存，需要至少2个点才能计算参数", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 将经纬度坐标转换为投影坐标（单位：米）
     * 根据当前坐标系的投影参数进行转换
     */
    private double[] convertBLToProjection(double B, double L) {

        try {
            CoordinateSystem cs = csRepo.getById(currentCsId);

            if (cs == null) {
                // 无坐标系信息时，尝试从输入框读取参数，走本地高斯转换
                double cm = safeParseDouble(etCentralMeridian != null ? etCentralMeridian.getText().toString().trim() : null, 114.0);
                double a  = safeParseDouble(etSemiMajor != null ? etSemiMajor.getText().toString().trim() : null, 6378137.0);
                double invF = safeParseDouble(etInvF != null ? etInvF.getText().toString().trim() : null, 298.257222101);
                GaussKrugerCGCS2000.XY xy =
                        GaussKrugerCGCS2000.lonLatToXY(L, B, cm, a, invF);
                return new double[]{xy.northing, xy.easting};
            }

            String projection = cs.getProjection();
            String ellipsoid = cs.getEllipsoid();

            if (projection != null && projection.startsWith("GAUSS")) {
                // 不再区分3/6度带；不使用带号；参数全部来自输入框（若输入为空则用数据库/默认）
                double cm = safeParseDouble(etCentralMeridian != null ? etCentralMeridian.getText().toString().trim() : null, cs.getCentralMeridian());
                double a  = safeParseDouble(etSemiMajor != null ? etSemiMajor.getText().toString().trim() : null, cs.getSemiMajorAxis());
                double invF = safeParseDouble(etInvF != null ? etInvF.getText().toString().trim() : null, cs.getInverseFlattening());
                GaussKrugerCGCS2000.XY xy =
                        GaussKrugerCGCS2000.lonLatToXY(L, B, cm, a, invF);
                return new double[]{xy.northing, xy.easting};
            } else if ("UTM".equals(projection)) {
                return convertCoordinatesUTM(B, L);
            } else {
                // 其他类型默认走高斯本地实现（使用输入框或数据库参数）
                double cm = safeParseDouble(etCentralMeridian != null ? etCentralMeridian.getText().toString().trim() : null, cs.getCentralMeridian());
                double a  = safeParseDouble(etSemiMajor != null ? etSemiMajor.getText().toString().trim() : null, cs.getSemiMajorAxis());
                double invF = safeParseDouble(etInvF != null ? etInvF.getText().toString().trim() : null, cs.getInverseFlattening());
                GaussKrugerCGCS2000.XY xy = GaussKrugerCGCS2000.lonLatToXY(L, B, cm, a, invF);
                return new double[]{xy.northing, xy.easting};
            }
        } catch (Exception e) {
            Log.e("CoordinateConversion", "坐标转换失败: " + e.getMessage());
            double x = L * 111320.0 * Math.cos(Math.toRadians(B));
            double y = B * 110540.0;
            return new double[]{x, y};
        }
    }

    private double safeParseDouble(String text, double defaultValue){
        try{
            if(text==null || text.isEmpty()) return defaultValue;
            return Double.parseDouble(text);
        }catch(Exception ignore){
            return defaultValue;
        }
    }

    /**
     * 使用Proj4J进行UTM坐标转换
     */
    private double[] convertCoordinatesUTM(double lat, double lon) {

        Log.d("CoordinateConversion", "=== UTM坐标转换开始 (WGS84ToUTM) ===");
        Log.d("CoordinateConversion", "输入: lat=" + lat + "°, lon=" + lon + "°");
        try {
            WGS84ToUTM.UTM utm = WGS84ToUTM.lonLatToUTM(lon, lat);
            int zone = utm.zone;
            double easting = utm.easting;
            double northing = utm.northing;
            double centralMeridian = zone * 6 - 183; // 与 WGS84ToUTM 一致的中央经线


            // 保存计算的参数到数据库
            saveProjectionParameters("UTM", zone, centralMeridian, "WGS84");

            return new double[]{northing, easting}; // 返回 X=北向, Y=东向
        } catch (Exception e) {
            Log.e("CoordinateConversion", "UTM坐标转换失败: " + e.getMessage());
            throw new RuntimeException("UTM坐标转换失败: " + e.getMessage());
        }
    }

    /**
     * 保存投影参数到数据库
     */
    private void saveProjectionParameters(String projectionType, int zone, double centralMeridian, String ellipsoid) {
        try {
            // 如果用户手动输入了中央子午线，优先使用用户输入的值
            double finalCentralMeridian = centralMeridian; // 创建final变量用于lambda表达式
            if (userManuallyEnteredCentralMeridian && etCentralMeridian != null) {
                String userInput = etCentralMeridian.getText().toString().trim();
                if (!userInput.isEmpty()) {
                    try {
                        double userValue = Double.parseDouble(userInput);
                        if (!Double.isNaN(userValue) && !Double.isInfinite(userValue)) {
                            finalCentralMeridian = userValue; // 使用用户输入的值
                        }
                    } catch (NumberFormatException e) {
                        // 用户输入无效，使用传入的值
                    }
                }
            }

            if (currentCsId > 0) {
                final double finalCentralMeridianForLambda = finalCentralMeridian; // 确保在lambda中使用的变量是final的
                executorService.execute(() -> {
                    CoordinateSystem cs = csRepo.getById(currentCsId);
                    if (cs != null) {
                        cs.setProjection(projectionType);
                        // 高斯投影禁止使用带号，设置为null
                        if (projectionType.startsWith("GAUSS")) {
                            cs.setZone(null);
                        } else {
                            cs.setZone(zone);
                        }
                        cs.setCentralMeridian(finalCentralMeridianForLambda);
                        cs.setEllipsoid(ellipsoid);
                        csRepo.update(cs);

                        runOnUiThread(() -> {
                            // 更新UI显示
                            updateProjectionDisplay(projectionType, zone, finalCentralMeridianForLambda, ellipsoid);
                        });
                    }
                });
            }
        } catch (Exception e) {
        }
    }

    /**
     * 更新投影参数显示
     */
    private void updateProjectionDisplay(String projectionType, int zone, double centralMeridian, String ellipsoid) {
        // 更新UI中显示投影参数的控件

        // 1. 更新椭球选择（不触发自动计算）
        updateEllipsoidSelectionWithoutAutoCalculation(ellipsoid);

        // 2. 更新投影类型选择（不触发自动计算）
        updateProjectionTypeSelectionWithoutAutoCalculation(projectionType);

        // 3. 更新中央子午线输入框
        if (etCentralMeridian != null) {
            etCentralMeridian.setText(String.format("%.8f", centralMeridian));
            // 投影参数计算完成，保持中央子午线输入框可编辑
        }

        // 4. 更新带号输入框（仅UTM投影显示带号，高斯投影禁止使用带号）
        if ("UTM".equals(projectionType)) {
            EditText etZone = findViewById(R.id.et_utm_zone);
            if (etZone != null) {
                etZone.setText(String.valueOf(zone));
            }
        }
        // 高斯投影不显示带号，保持隐藏状态

        // 5. 更新其他投影参数
        updateOtherProjectionParameters(projectionType, zone, centralMeridian);

        // 6. 刷新投影行显示
        updateProjectionRows();

        // 7. 默认选择北半球
        RadioButton rbNorth = findViewById(R.id.rb_north);
        if (rbNorth != null) {
            rbNorth.setChecked(true);
        }
    }

    /**
     * 更新椭球选择（不触发自动计算）
     */
    private void updateEllipsoidSelectionWithoutAutoCalculation(String ellipsoid) {
        TextView tvEllipsoid = findViewById(R.id.tv_ellipsoid_name);
        if (tvEllipsoid != null) {
            if ("WGS84".equals(ellipsoid)) {
                selectedEllipsoidIndex = 1;
                tvEllipsoid.setText("WGS84");
            } else if ("CGCS2000".equals(ellipsoid)) {
                selectedEllipsoidIndex = 0; // 假设CGCS2000是索引0
                tvEllipsoid.setText("CGCS2000");
            }
            updateEllipsoidParams();
            // 不触发自动计算
        }
    }

    /**
     * 更新椭球选择
     */
    private void updateEllipsoidSelection(String ellipsoid) {
        TextView tvEllipsoid = findViewById(R.id.tv_ellipsoid_name);
        if (tvEllipsoid != null) {
            if ("WGS84".equals(ellipsoid)) {
                selectedEllipsoidIndex = 1;
                tvEllipsoid.setText("WGS84");
            } else if ("CGCS2000".equals(ellipsoid)) {
                selectedEllipsoidIndex = 0; // 假设CGCS2000是索引0
                tvEllipsoid.setText("CGCS2000");
            }
            updateEllipsoidParams();

            // 已移除RTK自动计算功能，用户需手动输入中央子午线
        }
    }
    /**
     * 更新投影类型选择（不触发自动计算）
     */
    private void updateProjectionTypeSelectionWithoutAutoCalculation(String projectionType) {
        TextView tvProjection = findViewById(R.id.tv_projection_model);
        if (tvProjection != null) {
            // 默认选择北半球
            RadioButton rbNorth = findViewById(R.id.rb_north);

            switch (projectionType) {
                case "UTM":
                    selectedEllipsoidIndex = 1; // UTM使用WGS84
                    tvProjection.setText("UTM投影 (自动选择)");

                    if (rbNorth != null) {
                        rbNorth.setChecked(true);
                    }
                    break;
                case "GAUSS3":
                case "GAUSS6":
                case "GAUSS":
                    // 统一使用3度带高斯投影
                    tvProjection.setText("高斯投影（3度带）");
                    if (rbNorth != null) {
                        rbNorth.setChecked(true);
                    }
                    break;
            }
        }
    }

    /**
     * 更新投影类型选择
     */
    private void updateProjectionTypeSelection(String projectionType) {
        TextView tvProjection = findViewById(R.id.tv_projection_model);
        if (tvProjection != null) {
            // 默认选择北半球
            RadioButton rbNorth = findViewById(R.id.rb_north);

            switch (projectionType) {
                case "UTM":
                    selectedEllipsoidIndex = 1; // UTM使用WGS84
                    tvProjection.setText("UTM投影 (自动选择)");

                    if (rbNorth != null) {
                        rbNorth.setChecked(true);
                    }

                    // 已移除RTK自动计算功能，用户需手动输入中央子午线
                    break;
                case "GAUSS3":
                case "GAUSS6":
                case "GAUSS":
                    // 统一使用3度带高斯投影
                    tvProjection.setText("高斯投影（3度带）");
                    if (rbNorth != null) {
                        rbNorth.setChecked(true);
                    }
                    break;
            }
        }
    }

    /**
     * 更新其他投影参数
     */
    private void updateOtherProjectionParameters(String projectionType, int zone, double centralMeridian) {
        try {
            // 默认选择北半球
            RadioButton rbNorth = findViewById(R.id.rb_north);

            if ("UTM".equals(projectionType)) {
                // UTM投影的标准参数

                // 原点纬度 (UTM通常为0) - 使用行查找方法并禁用编辑
                setEditTextInRow(R.id.row_origin_lat, "0.0", false);

                // 比例因子 (UTM标准为0.9996) - 禁用编辑
                EditText etScaleFactor = findViewById(R.id.et_scale_factor);
                if (etScaleFactor != null) {
                    etScaleFactor.setText("0.9996");
                    etScaleFactor.setEnabled(false);
                    etScaleFactor.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                // 东偏移 (UTM标准为500000) - 禁用编辑
                EditText etAddEast = findViewById(R.id.et_add_east);
                if (etAddEast != null) {
                    etAddEast.setText("500000.0");
                    etAddEast.setEnabled(false);
                    etAddEast.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                // 北偏移 (北半球为0) - 使用行查找方法并禁用编辑
                setEditTextInRow(R.id.row_add_north, "0.0", false);

                if (rbNorth != null) {
                    rbNorth.setChecked(true);
                }

            } else if ("GAUSS3".equals(projectionType) || "GAUSS6".equals(projectionType)) {
                // 高斯-克吕格投影的标准参数

                // 原点纬度 (通常为0) - 使用行查找方法并禁用编辑
                setEditTextInRow(R.id.row_origin_lat, "0.0", false);

                // 比例因子 (高斯-克吕格标准为1.0) - 禁用编辑
                EditText etScaleFactor = findViewById(R.id.et_scale_factor);
                if (etScaleFactor != null) {
                    etScaleFactor.setText("1.0");
                    etScaleFactor.setEnabled(false);
                    etScaleFactor.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                // 东偏移 (3度带和6度带不同) - 禁用编辑
                EditText etAddEast = findViewById(R.id.et_add_east);
                if (etAddEast != null) {
                    // 统一使用标准偏移量500000米
                    double falseEasting = 500000.0;
                    etAddEast.setText(String.valueOf(falseEasting));
                    etAddEast.setEnabled(false);
                    etAddEast.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                // 北偏移 (通常为0) - 使用行查找方法并禁用编辑
                setEditTextInRow(R.id.row_add_north, "0.0", false);

                if (rbNorth != null) {
                    rbNorth.setChecked(true);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试不同投影转换的结果，帮助找到最佳匹配
     */
    private void testAllProjections(double lat, double lon) {
        try {
            // 1. UTM (WGS84)
            double[] utmResult = convertCoordinatesUTM(lat, lon);

            // 2. 高斯-克吕格 3度带 (CGCS2000)
            // 已禁用：不再区分3/6度带，也不再调用Proj4J高斯实现
            double[] gk3_cgcs = new double[]{0,0};

            // 3. 高斯-克吕格 6度带 (CGCS2000)
            double[] gk6_cgcs = new double[]{0,0};

            // 4. 高斯-克吕格 3度带 (WGS84)
            double[] gk3_wgs = new double[]{0,0};

            // 5. 高斯-克吕格 6度带 (WGS84)
            double[] gk6_wgs = new double[]{0,0};
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 分析多个点的经度分布，确定最佳的统一投影方法
     */
    private ProjectionInfo analyzeOptimalProjection(List<KnownPoint> knownPoints) {
        if (knownPoints == null || knownPoints.isEmpty()) {
            return new ProjectionInfo("UTM", 51, 123.0, "WGS84"); // 默认值
        }

        // 收集所有有效的经度值
        List<Double> longitudes = new java.util.ArrayList<>();
        for (KnownPoint kp : knownPoints) {
            if (kp.getMeasuredL() != null) {
                longitudes.add(kp.getMeasuredL());
            }
        }

        if (longitudes.isEmpty()) {
            return new ProjectionInfo("UTM", 51, 123.0, "WGS84"); // 默认值
        }

        // 计算经度范围
        double minLon = longitudes.stream().mapToDouble(Double::doubleValue).min().orElse(120.0);
        double maxLon = longitudes.stream().mapToDouble(Double::doubleValue).max().orElse(121.0);
        double centerLon = (minLon + maxLon) / 2.0;
        double lonRange = maxLon - minLon;

        // 根据经度跨度选择最佳投影方法
        if (lonRange <= 1.5) {
            // 跨度小于1.5度，使用高斯-克吕格3度带
            int zone = (int) (centerLon / 3) + 1;
            double centralMeridian = zone * 3;
            return new ProjectionInfo("GAUSS3", zone, centralMeridian, "CGCS2000");
        } else if (lonRange <= 3.0) {
            // 跨度小于3度，使用高斯-克吕格6度带
            int zone = (int) ((centerLon + 3) / 6) + 1;
            double centralMeridian = zone * 6 - 3;
            return new ProjectionInfo("GAUSS6", zone, centralMeridian, "CGCS2000");
        } else {
            // 跨度较大，使用UTM投影
            int zone = (int) ((centerLon + 180) / 6) + 1;
            double centralMeridian = zone * 6 - 183;
            return new ProjectionInfo("UTM", zone, centralMeridian, "WGS84");
        }
    }

    /**
     * 投影信息类
     */
    private static class ProjectionInfo {
        public final String type;
        public final int zone;
        public final double centralMeridian;
        public final String ellipsoid;

        public ProjectionInfo(String type, int zone, double centralMeridian, String ellipsoid) {
            this.type = type;
            this.zone = zone;
            this.centralMeridian = centralMeridian;
            this.ellipsoid = ellipsoid;
        }
    }
    /**
     * 使用指定的投影信息进行坐标转换
     */
    private double[] convertWithProjectionInfo(double lat, double lon, ProjectionInfo projInfo) {
        Log.d("CoordinateConversion", "=== 使用投影信息进行坐标转换 ===");
        Log.d("CoordinateConversion", "输入: lat=" + lat + "°, lon=" + lon + "°");
        Log.d("CoordinateConversion", "投影信息: 类型=" + projInfo.type + ", 椭球=" + projInfo.ellipsoid + ", 中央子午线=" + projInfo.centralMeridian + "°");

        try {
            double[] result = null;
            switch (projInfo.type) {
                case "GAUSS3":
                    Log.d("CoordinateConversion", "选择高斯-克吕格3度带投影");
                    // 已禁用：统一使用本地实现（由 convertBLToProjection 内部处理）
                    result = convertBLToProjection(lat, lon);
                    break;
                case "GAUSS6":
                    Log.d("CoordinateConversion", "选择高斯-克吕格6度带投影");
                    // 已禁用：统一使用本地实现（由 convertBLToProjection 内部处理）
                    result = convertBLToProjection(lat, lon);
                    break;
                case "UTM":
                    Log.d("CoordinateConversion", "选择UTM投影");
                    result = convertCoordinatesUTM(lat, lon);
                    break;
                default:
                    Log.w("CoordinateConversion", "未知投影类型: " + projInfo.type + "，默认使用UTM投影");
                    result = convertCoordinatesUTM(lat, lon);
                    break;
            }

            if (result != null && result.length >= 2) {
                Log.d("CoordinateConversion", "转换成功: X=" + result[0] + "m, Y=" + result[1] + "m");
            } else {
                Log.w("CoordinateConversion", "转换结果异常: " + (result == null ? "null" : "长度=" + result.length));
            }

            return result;
        } catch (Exception e) {
            Log.e("CoordinateConversion", "坐标转换失败: " + e.getMessage());
            // 不返回近似值，而是抛出异常让上层处理
            throw new RuntimeException("坐标转换失败: " + e.getMessage());
        }
    }

    /**
     * 获取椭球参数
     * 根据国家标准和国际标准定义的椭球参数
     */
    private double[] getEllipsoidParameters(String ellipsoid) {
        // 返回 [长半轴a, 扁率f, 反扁率]
        switch (ellipsoid) {
            case "WGS84":
                // WGS84椭球：长半轴6378137.0m，反扁率298.257223563
                return new double[]{6378137.0, 1.0 / 298.257223563, 298.257223563};
            case "CGCS2000":
                // CGCS2000椭球：与WGS84基本一致，长半轴6378137.0m，反扁率298.257222101
                return new double[]{6378137.0, 1.0 / 298.257222101, 298.257222101};
            case "北京54":
                // 北京54坐标系：克拉索夫斯基1940椭球体，长半轴6378245.0m，反扁率298.3
                return new double[]{6378245.0, 1.0 / 298.3, 298.3};
            case "西安80":
                // 西安80坐标系：IUGG1975椭球体，长半轴6378140.0m，反扁率298.257
                return new double[]{6378140.0, 1.0 / 298.257, 298.257};
            default:
                // 默认使用CGCS2000（国家大地坐标系）
                return new double[]{6378137.0, 1.0 / 298.257222101, 298.257222101};
        }
    }

    /**
     * 标准分度带计算方法（统一使用3度带）
     */
    private static class ZoneInfo {
        public final int zone;
        public final double centralMeridian;

        public ZoneInfo(int zone, double centralMeridian) {
            this.zone = zone;
            this.centralMeridian = centralMeridian;
        }
    }


    /**
     * 计算WGS84坐标系UTM分度带信息
     */
    private ZoneInfo calculateUTMZone(double longitude, double latitude) {
        // UTM带数 = (经度整数位/6)的整数部分 + 31
        int zone = (int) Math.floor(longitude / 6.0) + 31;
        // UTM中央经线 = (zone - 31) * 6 + 3 - 180
        double centralMeridian = (zone - 31) * 6 + 3 - 180;

        // 确保带号在有效范围内 (1-60)
        if (zone < 1) zone = 1;
        if (zone > 60) zone = 60;

        return new ZoneInfo(zone, centralMeridian); // UTM
    }


    /**
     * 根据已知点计算坐标系参数
     */
    private void calculateParametersFromKnownPoints() {
        if (currentCsId <= 0) {
            Toast.makeText(this, "请先保存坐标系", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取当前坐标系的所有已知点
        executorService.execute(() -> {
            try {
                List<KnownPoint> knownPoints = kpRepo.getByCsSync(currentCsId);
                if (knownPoints == null || knownPoints.size() < 2) {
                    runOnUiThread(() -> Toast.makeText(this, "至少需要2个已知点才能计算参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 分离有效数据（既有已知坐标又有测量坐标的点）
                List<Point2D> srcPoints = new java.util.ArrayList<>();
                List<Point2D> dstPoints = new java.util.ArrayList<>();
                List<Vector3D> src3D = new java.util.ArrayList<>();
                List<Vector3D> dst3D = new java.util.ArrayList<>();

                // 分析所有点的经度分布，确定最佳统一投影方法
                ProjectionInfo optimalProjection = analyzeOptimalProjection(knownPoints);

                // 保存最佳投影参数到数据库
                saveProjectionParameters(optimalProjection.type, optimalProjection.zone,
                        optimalProjection.centralMeridian, optimalProjection.ellipsoid);

                for (KnownPoint kp : knownPoints) {
                    if (kp.getX() != null && kp.getY() != null && kp.getZ() != null &&
                            kp.getMeasuredB() != null && kp.getMeasuredL() != null && kp.getMeasuredH() != null) {

                        // 2D点（平面校正用）
                        // 使用统一的最佳投影方法转换所有点
                        Log.d("PlaneCorrection", "转换已知点: " + kp.getName());
                        Log.d("PlaneCorrection", "  测量坐标: B=" + kp.getMeasuredB() + "°, L=" + kp.getMeasuredL() + "°");
                        Log.d("PlaneCorrection", "  已知坐标: X=" + kp.getX() + "m, Y=" + kp.getMeasuredL() + "m");

                        double[] projectedCoords = convertWithProjectionInfo(kp.getMeasuredB(), kp.getMeasuredL(), optimalProjection);
                        Log.d("PlaneCorrection", "  投影结果: X=" + projectedCoords[0] + "m, Y=" + projectedCoords[1] + "m");

                        // 验证投影结果是否合理
                        if (Double.isNaN(projectedCoords[0]) || Double.isNaN(projectedCoords[1]) ||
                                Double.isInfinite(projectedCoords[0]) || Double.isInfinite(projectedCoords[1])) {
                            Log.w("PlaneCorrection", "  投影结果无效，跳过此点");
                            continue;
                        }

                        // 坐标轴定义统一：X=北向(N), Y=东向(E)
                        // 注意：srcPoints = 投影结果（国家投影坐标），dstPoints = 已知坐标（地方平面坐标）
                        // 调用 estimate(dstPoints, srcPoints) 表示：从地方平面坐标转换到国家投影坐标
                        Log.d("PlaneCorrection", String.format("  添加控制点%d: 地方平面坐标(%.10f, %.10f) -> 国家投影坐标(%.10f, %.10f)", 
                            srcPoints.size() + 1, kp.getX(), kp.getY(), projectedCoords[0], projectedCoords[1]));
                        srcPoints.add(new Point2D(projectedCoords[0], projectedCoords[1])); // 投影结果：X=北向, Y=东向
                        dstPoints.add(new Point2D(kp.getX(), kp.getY()));                  // 已知坐标：X=北向, Y=东向

                        Log.d("PlaneCorrection", "  点已添加到计算列表");

                        // 3D点（七参数用）
                        src3D.add(new Vector3D(kp.getMeasuredL(), kp.getMeasuredB(), kp.getMeasuredH()));
                        dst3D.add(new Vector3D(kp.getY(), kp.getX(), kp.getZ()));
                    }
                }

                if (srcPoints.size() < 2) {
                    runOnUiThread(() -> Toast.makeText(this, "有效已知点数量不足，无法计算参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 根据当前坐标系设置计算参数
                runOnUiThread(() -> {
                    try {
                        // 确保面板可见性正确设置
                        updatePlanePanelVisibility();
                        updateDatumPanelVisibility();
                        updateHeightPanelVisibility(); // 确保高程拟合面板可见性正确设置

                        // 计算平面校正参数
                        if (selectedPlaneIndex == 1 && srcPoints.size() >= 2) {
                            // 三参数
                            ThreeParameterTransform threeParam = ThreeParameterSolver.estimate(srcPoints, dstPoints);
                            updatePlaneParameters(threeParam);
                            // 🔧 立即保存三参数到数据库（不依赖界面更新）
                            saveThreeParameterToDatabase(threeParam);
                        } else if (selectedPlaneIndex == 2 && srcPoints.size() >= 2) {
                            // 四参数
                            // 添加调试信息
                            StringBuilder debugInfo = new StringBuilder();
                            debugInfo.append("四参数计算调试信息:\n");
                            debugInfo.append("源点数量: ").append(srcPoints.size()).append("\n");
                            debugInfo.append("目标点数量: ").append(dstPoints.size()).append("\n");

                            // 检查坐标数值范围
                            double srcMinX = srcPoints.stream().mapToDouble(p -> p.x).min().orElse(0);
                            double srcMaxX = srcPoints.stream().mapToDouble(p -> p.x).max().orElse(0);
                            double srcMinY = srcPoints.stream().mapToDouble(p -> p.y).min().orElse(0);
                            double srcMaxY = srcPoints.stream().mapToDouble(p -> p.y).max().orElse(0);

                            double dstMinX = dstPoints.stream().mapToDouble(p -> p.x).min().orElse(0);
                            double dstMaxX = dstPoints.stream().mapToDouble(p -> p.x).max().orElse(0);
                            double dstMinY = dstPoints.stream().mapToDouble(p -> p.y).min().orElse(0);
                            double dstMaxY = dstPoints.stream().mapToDouble(p -> p.y).max().orElse(0);

                            debugInfo.append("源点范围: X[").append(String.format("%.2f", srcMinX)).append(" ~ ").append(String.format("%.2f", srcMaxX))
                                    .append("], Y[").append(String.format("%.2f", srcMinY)).append(" ~ ").append(String.format("%.2f", srcMaxY)).append("]\n");
                            debugInfo.append("目标点范围: X[").append(String.format("%.2f", dstMinX)).append(" ~ ").append(String.format("%.2f", dstMaxX))
                                    .append("], Y[").append(String.format("%.2f", dstMinY)).append(" ~ ").append(String.format("%.2f", dstMaxY)).append("]\n");

                            for (int i = 0; i < Math.min(srcPoints.size(), 3); i++) {
                                debugInfo.append("源点").append(i+1).append(": (").append(String.format("%.6f", srcPoints.get(i).x)).append(", ").append(String.format("%.6f", srcPoints.get(i).y)).append(")\n");
                                debugInfo.append("目标点").append(i+1).append(": (").append(String.format("%.6f", dstPoints.get(i).x)).append(", ").append(String.format("%.6f", dstPoints.get(i).y)).append(")\n");
                            }

                            try {
                                // 检查数据是否合理
                                boolean dataValid = true;
                                String errorMsg = "";

                                Log.d("PlaneCorrection", "=== 四参数数据验证开始 ===");
                                Log.d("PlaneCorrection", "源点数量: " + srcPoints.size());
                                Log.d("PlaneCorrection", "目标点数量: " + dstPoints.size());

                                // 检查坐标数值范围是否合理
                                double srcRangeX = srcMaxX - srcMinX;
                                double srcRangeY = srcMaxY - srcMinY;
                                double dstRangeX = dstMaxX - dstMinX;
                                double dstRangeY = dstMaxY - dstMinY;

                                Log.d("PlaneCorrection", "坐标范围分析:");
                                Log.d("PlaneCorrection", "  源点: X范围=" + srcRangeX + "m, Y范围=" + srcRangeY + "m");
                                Log.d("PlaneCorrection", "  目标点: X范围=" + dstRangeX + "m, Y范围=" + dstRangeY + "m");

                                // 检查投影坐标是否在合理范围内（米）
                                if (Math.abs(srcMaxX) > 10000000 || Math.abs(srcMaxY) > 10000000) {
                                    dataValid = false;
                                    errorMsg = "投影坐标值异常（超过1000万米），可能是坐标转换错误";
                                    Log.e("PlaneCorrection", "源点坐标异常: X=" + srcMaxX + "m, Y=" + srcMaxY + "m");
                                } else if (Math.abs(dstMaxX) > 10000000 || Math.abs(dstMaxY) > 10000000) {
                                    dataValid = false;
                                    errorMsg = "已知点坐标值异常（超过1000万米），请检查坐标单位";
                                    Log.e("PlaneCorrection", "目标点坐标异常: X=" + dstMaxX + "m, Y=" + dstMaxY + "m");
                                } else if (srcRangeX < 1e-6 || srcRangeY < 1e-6) {
                                    dataValid = false;
                                    errorMsg = "源点坐标变化范围过小，可能是投影转换问题";
                                    Log.w("PlaneCorrection", "源点坐标范围过小: X=" + srcRangeX + "m, Y=" + srcRangeY + "m");
                                } else if (dstRangeX < 1e-6 || dstRangeY < 1e-6) {
                                    dataValid = false;
                                    errorMsg = "目标点坐标变化范围过小";
                                    Log.w("PlaneCorrection", "目标点坐标范围过小: X=" + dstRangeX + "m, Y=" + dstRangeY + "m");
                                } else if (Math.abs(srcRangeX / dstRangeX) > 1e6 || Math.abs(dstRangeX / srcRangeX) > 1e6) {
                                    dataValid = false;
                                    errorMsg = "源点和目标点的坐标范围差异过大，可能存在单位问题";
                                    Log.w("PlaneCorrection", "坐标范围差异过大: 比例=" + (srcRangeX / dstRangeX));
                                }

                                Log.d("PlaneCorrection", "数据验证结果: " + (dataValid ? "通过" : "失败"));
                                if (!dataValid) {
                                    Log.w("PlaneCorrection", "验证失败原因: " + errorMsg);
                                }

                                if (!dataValid) {
                                    debugInfo.append("数据验证失败: ").append(errorMsg).append("\n");
                                    debugInfo.append("坐标转换结果异常，请检查：\n");
                                    debugInfo.append("1. 椭球参数设置是否正确\n");
                                    debugInfo.append("2. 投影参数是否匹配\n");
                                    debugInfo.append("3. 中央子午线设置是否正确\n");
                                    debugInfo.append("4. 已知点坐标单位是否为米\n\n");

                                    // 显示错误信息并停止计算
                                    new AlertDialog.Builder(this)
                                            .setTitle("坐标转换错误")
                                            .setMessage(debugInfo.toString())
                                            .setPositiveButton("确定", null)
                                            .show();

                                    return; // 停止计算，不继续执行
                                }

                                // 修正：与 FourParameterWithThreePoints.java 一致
                                // srcPoints = 投影结果（国家投影坐标），dstPoints = 已知坐标（地方平面坐标）
                                // 在 FourParameterWithThreePoints.java 中：srcPlane（地方平面坐标）→ dstPlane（国家投影坐标）
                                // 所以应该调用 estimate(dstPoints, srcPoints) 来匹配：从地方平面坐标转换到国家投影坐标
                                // 但是，在 CoordinateSystemManager 中应用时，需要从国家投影坐标转换为地方平面坐标
                                // 所以我们需要保存反向转换的参数
                                FourParameterSolver.FourParameterTransform forwardParam = FourParameterSolver.estimate(dstPoints, srcPoints);
                                
                                // 计算反向转换的参数（从国家投影坐标转换为地方平面坐标）
                                // 因为在 CoordinateSystemManager 中，输入是国家投影坐标，输出是地方平面坐标
                                FourParameterSolver.FourParameterTransform fourParam = forwardParam.inverse();
                                
                                Log.d("PlaneCorrection", "正向参数: dx=" + forwardParam.getDx() + ", dy=" + forwardParam.getDy() + 
                                      ", theta=" + Math.toDegrees(forwardParam.getTheta()) + "°, k=" + forwardParam.getK());
                                Log.d("PlaneCorrection", "反向参数: dx=" + fourParam.getDx() + ", dy=" + fourParam.getDy() + 
                                      ", theta=" + Math.toDegrees(fourParam.getTheta()) + "°, k=" + fourParam.getK());

                                debugInfo.append("计算结果:\n");
                                debugInfo.append("dx: ").append(String.format("%.6f", fourParam.getDx())).append("\n");
                                debugInfo.append("dy: ").append(String.format("%.6f", fourParam.getDy())).append("\n");
                                debugInfo.append("theta: ").append(String.format("%.6f", Math.toDegrees(fourParam.getTheta()))).append("°\n");
                                debugInfo.append("k: ").append(String.format("%.6f", fourParam.getK())).append("\n");

                                // 验证计算结果（注意：fourParam 是反向转换，从国家投影坐标转换为地方平面坐标）
                                // 所以验证时应该用 (srcPoints, dstPoints)：将国家投影坐标转换为地方平面坐标
                                double rmse = FourParameterSolver.calculateRMSE(srcPoints, dstPoints, fourParam);
                                boolean isValid = rmse <= 0.1;

                                debugInfo.append("验证结果:\n");
                                debugInfo.append("RMSE: ").append(String.format("%.6f", rmse)).append("\n");
                                debugInfo.append("变换有效: ").append(isValid ? "是" : "否").append("\n");

                                // 显示变换后的坐标（修正：fourParam 是反向转换，从国家投影坐标 → 地方平面坐标）
                                debugInfo.append("变换验证:\n");
                                for (int i = 0; i < Math.min(srcPoints.size(), 2); i++) {
                                    double[] transformed = fourParam.transform(srcPoints.get(i).x, srcPoints.get(i).y);
                                    debugInfo.append("源点（国家投影）").append(i+1).append(": (").append(String.format("%.6f", srcPoints.get(i).x)).append(", ").append(String.format("%.6f", srcPoints.get(i).y)).append(")\n");
                                    debugInfo.append("变换后（地方平面）: (").append(String.format("%.6f", transformed[0])).append(", ").append(String.format("%.6f", transformed[1])).append(")\n");
                                    debugInfo.append("目标点（地方平面）").append(i+1).append("实际: (").append(String.format("%.6f", dstPoints.get(i).x)).append(", ").append(String.format("%.6f", dstPoints.get(i).y)).append(")\n");
                                }

                                updatePlaneParameters(fourParam);

                                // 🔧 立即保存四参数到数据库（不依赖界面更新）
                                saveFourParameterToDatabase(fourParam);

                                // 保存四参数计算详细数据到数据库
                                saveFourParameterCalculationData(fourParam, srcPoints, dstPoints, rmse, isValid, debugInfo.toString());

                                // 显示详细调试信息
                                new AlertDialog.Builder(this)
                                        .setTitle("四参数计算详情")
                                        .setMessage(debugInfo.toString())
                                        .setPositiveButton("确定", null)
                                        .show();

                            } catch (Exception e) {
                                debugInfo.append("计算异常: ").append(e.getMessage());
                                new AlertDialog.Builder(this)
                                        .setTitle("四参数计算错误")
                                        .setMessage(debugInfo.toString())
                                        .setPositiveButton("确定", null)
                                        .show();
                                e.printStackTrace();
                            }
                        } else if (selectedPlaneIndex == 3 && srcPoints.size() >= 3) {
                            // 平面平差 - 6参数最小二乘法
                            try {
                                PlaneAdjustmentTransform planeAdjustment = PlaneAdjustmentSolver.estimate(srcPoints, dstPoints);
                                updatePlaneAdjustmentParameters(planeAdjustment);
                                
                                // 🔧 立即保存平面平差参数到数据库（不依赖界面更新）
                                savePlaneAdjustmentToDatabase(planeAdjustment);

                                String resultMessage = String.format(
                                        "平面平差计算完成:\n" +
                                                "北原点: %.3f m\n" +
                                                "东原点: %.3f m\n" +
                                                "北平移: %.6f m\n" +
                                                "东平移: %.6f m\n" +
                                                "旋转尺度: %.6f\n" +
                                                "比例尺: %.6f\n" +
                                                "RMSE: %.6f m",
                                        planeAdjustment.getNorthOrigin(),
                                        planeAdjustment.getEastOrigin(),
                                        planeAdjustment.getNorthTranslation(),
                                        planeAdjustment.getEastTranslation(),
                                        planeAdjustment.getRotationScale(),
                                        planeAdjustment.getScale(),
                                        planeAdjustment.getRmse()
                                );

                                // 显示详细计算结果
                                new AlertDialog.Builder(this)
                                        .setTitle("平面平差计算结果")
                                        .setMessage(resultMessage)
                                        .setPositiveButton("确定", null)
                                        .show();

                            } catch (Exception e) {
                                String errorMessage = "平面平差计算异常: " + e.getMessage();
                                new AlertDialog.Builder(this)
                                        .setTitle("平面平差计算错误")
                                        .setMessage(errorMessage)
                                        .setPositiveButton("确定", null)
                                        .show();
                                e.printStackTrace();
                            }
                        } else {
                            // 如果没有选择平面校正模式，默认使用三参数
                            if (srcPoints.size() >= 2) {
                                ThreeParameterTransform threeParam = ThreeParameterSolver.estimate(srcPoints, dstPoints);
                                updatePlaneParameters(threeParam);
                                // 🔧 立即保存三参数到数据库（不依赖界面更新）
                                saveThreeParameterToDatabase(threeParam);
                            }
                        }

                        // 计算七参数（基准转换）
                        if (selectedProjectionIndex == 1 && src3D.size() >= 3) {
                            SevenParameterTransform sevenParam = SevenParameterSolver.estimate(src3D, dst3D);
                            updateDatumParameters(sevenParam);
                            // 🔧 立即保存七参数到数据库（不依赖界面更新）
                            saveSevenParameterToDatabase(sevenParam);
                        }

                        // 计算高程拟合参数
                        // 注意：当基准转换选择七参数时，不需要单独的高程拟合，因为七参数已包含完整的三维变换
                        if (src3D.size() >= 2 && selectedProjectionIndex != 1) {
                            // 确保高程拟合类型已正确设置
                            if (selectedHeightFitIndex <= 0) {
                                selectedHeightFitIndex = 4; // 默认使用加权平均
                                updateHeightFitDisplay();
                            }

                            try {
                                calculateHeightFitParameters(src3D, dst3D);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(this, "高程拟合参数计算失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else if (selectedProjectionIndex == 1) {
                            // 七参数模式下，高程拟合设为无参数
                            selectedHeightFitIndex = 0; // 0表示无参数
                            updateHeightFitDisplay();

                            // 七参数模式下，平面校正也设为无参数
                            selectedPlaneIndex = 0; // 0表示无参数
                            ((TextView)findViewById(R.id.tv_plane_correction))
                                    .setText("无参数");
                            updatePlanePanelVisibility();
                        } else {
                            // 如果没有足够的3D点数据
                            Toast.makeText(this, "高程拟合：控制点数量不足(" + src3D.size() + ")，至少需要2个点", Toast.LENGTH_SHORT).show();
                        }

                        // 强制刷新高程拟合显示
                        updateHeightFitDisplay();
                        updateHeightPanelVisibility();

                        // 计算完成后自动切换到坐标系界面显示结果
                        if (tabLayout != null && tabLayout.getTabCount() > 0) {
                            tabLayout.post(() -> {
                                tabLayout.selectTab(tabLayout.getTabAt(0));
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "参数计算失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "获取已知点数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 更新平面校正参数到界面
     */
    private void updatePlaneParameters(ThreeParameterTransform threeParam) {
        // 只有在选择三参数时才显示面板
        if (panelThreeParam != null && selectedPlaneIndex == 1) {
            panelThreeParam.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        panelThreeParam.post(() -> {
            EditText etDx = findViewById(R.id.tv_tp_dx);
            EditText etDy = findViewById(R.id.tv_tp_dy);
            EditText etTheta = findViewById(R.id.tv_tp_theta);

            if (etDx != null) {
                etDx.setText(String.format("%.6f", threeParam.getDx()));
            } else {
                Toast.makeText(this, "警告: 找不到三参数dx输入框", Toast.LENGTH_SHORT).show();
            }

            if (etDy != null) {
                etDy.setText(String.format("%.6f", threeParam.getDy()));
            } else {
                Toast.makeText(this, "警告: 找不到三参数dy输入框", Toast.LENGTH_SHORT).show();
            }

            if (etTheta != null) {
                etTheta.setText(String.format("%.6f", Math.toDegrees(threeParam.getTheta())));
            } else {
                Toast.makeText(this, "警告: 找不到三参数theta输入框", Toast.LENGTH_SHORT).show();
            }
        });

        // 确保面板显示状态与当前选择一致
        updatePlanePanelVisibility();
    }
    private void updatePlaneParameters(FourParameterSolver.FourParameterTransform fourParam) {
        // 只有在选择四参数时才显示面板
        if (panelFourParam != null && selectedPlaneIndex == 2) {
            panelFourParam.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        panelFourParam.post(() -> {
            EditText etDx = findViewById(R.id.tv_fp_dx);
            EditText etDy = findViewById(R.id.tv_fp_dy);
            EditText etTheta = findViewById(R.id.tv_fp_theta);
            EditText etK = findViewById(R.id.tv_fp_k);

            if (etDx != null) {
                etDx.setText(String.format("%.6f", fourParam.getDx()));
            } else {
                Toast.makeText(this, "警告: 找不到四参数dx输入框", Toast.LENGTH_SHORT).show();
            }

            if (etDy != null) {
                etDy.setText(String.format("%.6f", fourParam.getDy()));
            } else {
                Toast.makeText(this, "警告: 找不到四参数dy输入框", Toast.LENGTH_SHORT).show();
            }

            if (etTheta != null) {
                etTheta.setText(String.format("%.6f", Math.toDegrees(fourParam.getTheta())));
            } else {
                Toast.makeText(this, "警告: 找不到四参数theta输入框", Toast.LENGTH_SHORT).show();
            }

            if (etK != null) {
                etK.setText(String.format("%.6f", fourParam.getK()));
            } else {
                Toast.makeText(this, "警告: 找不到四参数k输入框", Toast.LENGTH_SHORT).show();
            }
        });

        // 确保面板显示状态与当前选择一致
        updatePlanePanelVisibility();
    }

    /**
     * 更新平面平差参数到界面
     */
    private void updatePlaneAdjustmentParameters(PlaneAdjustmentTransform planeAdjustment) {
        // 只有在选择平面平差时才显示面板
        if (panelPlaneAdjustment != null && selectedPlaneIndex == 3) {
            panelPlaneAdjustment.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        panelPlaneAdjustment.post(() -> {
            EditText etNorthOrigin = findViewById(R.id.tv_pa_north_origin);
            EditText etEastOrigin = findViewById(R.id.tv_pa_east_origin);
            EditText etNorthTranslation = findViewById(R.id.tv_pa_north_translation);
            EditText etEastTranslation = findViewById(R.id.tv_pa_east_translation);
            EditText etRotationScale = findViewById(R.id.tv_pa_rotation_scale);
            EditText etScale = findViewById(R.id.tv_pa_scale);

            if (etNorthOrigin != null) {
                etNorthOrigin.setText(String.format("%.3f", planeAdjustment.getNorthOrigin()));
            } else {
                Toast.makeText(this, "警告: 找不到北原点输入框", Toast.LENGTH_SHORT).show();
            }

            if (etEastOrigin != null) {
                etEastOrigin.setText(String.format("%.3f", planeAdjustment.getEastOrigin()));
            } else {
                Toast.makeText(this, "警告: 找不到东原点输入框", Toast.LENGTH_SHORT).show();
            }

            if (etNorthTranslation != null) {
                etNorthTranslation.setText(String.format("%.6f", planeAdjustment.getNorthTranslation()));
            } else {
                Toast.makeText(this, "警告: 找不到北平移输入框", Toast.LENGTH_SHORT).show();
            }

            if (etEastTranslation != null) {
                etEastTranslation.setText(String.format("%.6f", planeAdjustment.getEastTranslation()));
            } else {
                Toast.makeText(this, "警告: 找不到东平移输入框", Toast.LENGTH_SHORT).show();
            }

            if (etRotationScale != null) {
                etRotationScale.setText(String.format("%.6f", planeAdjustment.getRotationScale()));
            } else {
                Toast.makeText(this, "警告: 找不到旋转尺度输入框", Toast.LENGTH_SHORT).show();
            }

            if (etScale != null) {
                etScale.setText(String.format("%.6f", planeAdjustment.getScale()));
            } else {
                Toast.makeText(this, "警告: 找不到比例尺输入框", Toast.LENGTH_SHORT).show();
            }
        });

        // 确保面板显示状态与当前选择一致
        updatePlanePanelVisibility();
    }

    /**
     * 更新基准转换参数到界面
     */
    private void updateDatumParameters(SevenParameterTransform sevenParam) {
        // 确保七参数面板可见
        if (panelSevenParam != null) {
            panelSevenParam.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        panelSevenParam.post(() -> {
            EditText etDx = findViewById(R.id.tv_sp_dx);
            EditText etDy = findViewById(R.id.tv_sp_dy);
            EditText etDz = findViewById(R.id.tv_sp_dz);
            EditText etRx = findViewById(R.id.tv_sp_rx);
            EditText etRy = findViewById(R.id.tv_sp_ry);
            EditText etRz = findViewById(R.id.tv_sp_rz);
            EditText etScale = findViewById(R.id.tv_sp_s);

            if (etDx != null) {
                etDx.setText(String.format("%.6f", sevenParam.getDx()));
            } else {
                Toast.makeText(this, "警告: 找不到七参数dx输入框", Toast.LENGTH_SHORT).show();
            }

            if (etDy != null) {
                etDy.setText(String.format("%.6f", sevenParam.getDy()));
            } else {
                Toast.makeText(this, "警告: 找不到七参数dy输入框", Toast.LENGTH_SHORT).show();
            }

            if (etDz != null) {
                etDz.setText(String.format("%.6f", sevenParam.getDz()));
            } else {
                Toast.makeText(this, "警告: 找不到七参数dz输入框", Toast.LENGTH_SHORT).show();
            }

            if (etRx != null) {
                etRx.setText(String.format("%.6f", Math.toDegrees(sevenParam.getRx())));
            } else {
                Toast.makeText(this, "警告: 找不到七参数rx输入框", Toast.LENGTH_SHORT).show();
            }

            if (etRy != null) {
                etRy.setText(String.format("%.6f", Math.toDegrees(sevenParam.getRy())));
            } else {
                Toast.makeText(this, "警告: 找不到七参数ry输入框", Toast.LENGTH_SHORT).show();
            }

            if (etRz != null) {
                etRz.setText(String.format("%.6f", Math.toDegrees(sevenParam.getRz())));
            } else {
                Toast.makeText(this, "警告: 找不到七参数rz输入框", Toast.LENGTH_SHORT).show();
            }

            if (etScale != null) {
                etScale.setText(String.format("%.6f", sevenParam.getS()));
            } else {
                Toast.makeText(this, "警告: 找不到七参数scale输入框", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 计算高程拟合参数
     * 使用 HeightFittingCalculator 进行计算
     */
    private void calculateHeightFitParameters(List<Vector3D> src, List<Vector3D> dst) {
        int n = src.size();
        if (n < 2) {
            Toast.makeText(this, "高程拟合：控制点数量不足(" + n + ")，至少需要2个点", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示控制点数据
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("控制点数据:\n");
        for (int i = 0; i < Math.min(n, 3); i++) {
            debugInfo.append("点").append(i+1).append(": 源(")
                    .append(String.format("%.6f", src.get(i).x)).append(", ")
                    .append(String.format("%.6f", src.get(i).y)).append(", ")
                    .append(String.format("%.6f", src.get(i).z)).append(") -> 目标(")
                    .append(String.format("%.6f", dst.get(i).x)).append(", ")
                    .append(String.format("%.6f", dst.get(i).y)).append(", ")
                    .append(String.format("%.6f", dst.get(i).z)).append(")\n");
        }
        Log.d("HeightFit", debugInfo.toString());

        switch (selectedHeightFitIndex) {
            case 1: // 垂直平差
                Log.d("HeightFit", "执行垂直平差计算");
                calculateVerticalAdjustmentNew(src, dst);
                break;
            case 2: // 平面拟合
                Log.d("HeightFit", "执行平面拟合计算");
                calculatePlaneFitNew(src, dst);
                break;
            case 3: // 曲面拟合
                Log.d("HeightFit", "执行曲面拟合计算");
                calculateSurfaceFitNew(src, dst);
                break;
            case 4: // 加权平均
                Log.d("HeightFit", "执行加权平均计算");
                calculateWeightedAverageNew(src, dst);
                break;
            default:
                Log.w("HeightFit", "未知的高程拟合类型: " + selectedHeightFitIndex);
                break;
        }
    }
    
    /**
     * 垂直平差计算 - 使用 HeightFittingCalculator
     */
    private void calculateVerticalAdjustmentNew(List<Vector3D> src, List<Vector3D> dst) {
        HeightFittingCalculator.VerticalAdjustmentResult result = HeightFittingCalculator.calculateVerticalAdjustment(src, dst);

        if (result == null) {
            Toast.makeText(this, "垂直平差计算失败：控制点不足或数据质量差", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存参数到数据库
        saveVerticalAdjustmentParameters(result.northOrigin, result.eastOrigin, 
                result.northSlope, result.eastSlope, result.heightConstant, result.rmse);
        
        // 更新界面显示
        updateVerticalAdjustmentUI(result.northOrigin, result.eastOrigin, 
                result.northSlope, result.eastSlope, result.heightConstant);
        
        // 显示结果
        Toast.makeText(this, result.resultMessage, Toast.LENGTH_LONG).show();
    }
    
    /**
     * 平面拟合计算 - 使用 HeightFittingCalculator
     */
    private void calculatePlaneFitNew(List<Vector3D> src, List<Vector3D> dst) {
        HeightFittingCalculator.PlaneFitResult result =
                HeightFittingCalculator.calculatePlaneFit(src, dst);
        
        if (result == null) {
            Toast.makeText(this, "平面拟合计算失败：控制点不足", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存参数到数据库
        savePlaneFittingParameters(result.northOrigin, result.eastOrigin, 
                result.paramA, result.paramB, result.paramC, result.rmse);
        
        // 更新界面显示
        updatePlaneFittingUI(result.northOrigin, result.eastOrigin, 
                result.paramA, result.paramB, result.paramC);
        
        // 显示结果
        Toast.makeText(this, result.resultMessage, Toast.LENGTH_LONG).show();
    }
    
    /**
     * 曲面拟合计算 - 使用 HeightFittingCalculator
     */
    private void calculateSurfaceFitNew(List<Vector3D> src, List<Vector3D> dst) {
        HeightFittingCalculator.SurfaceFitResult result =
                HeightFittingCalculator.calculateSurfaceFit(src, dst);
        
        if (result == null) {
            Toast.makeText(this, "8参数曲面拟合计算失败：至少需要8个控制点", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存参数到数据库
        saveSurfaceFittingParameters(result.northOrigin, result.eastOrigin, result.params, result.rmse);
        
        // 更新界面显示
        updateSurfaceFittingUI(result.northOrigin, result.eastOrigin, result.params);
        
        // 显示结果
        Toast.makeText(this, result.resultMessage, Toast.LENGTH_LONG).show();
    }
    
    /**
     * 加权平均计算 - 使用 HeightFittingCalculator
     */
    private void calculateWeightedAverageNew(List<Vector3D> src, List<Vector3D> dst) {
        HeightFittingCalculator.WeightedAverageResult result =
                HeightFittingCalculator.calculateWeightedAverage(src, dst);
        
        if (result == null) {
            Toast.makeText(this, "加权平均计算失败：数据异常", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存参数到数据库
        saveWeightedAverageParameters(result.weightedAverage, result.rmse, 
                result.weights, result.dataPoints);
        
        // 更新界面显示
        updateWeightedAverageUI(result.weightedAverage);
        
        // 显示结果
        Toast.makeText(this, result.resultMessage, Toast.LENGTH_LONG).show();
    }

    /**
     * 使用带部分选主元的高斯消元法求解线性方程组 A·x = b。
     * 适用于对称正定或一般非奇异方程组，内部对输入进行拷贝以避免副作用。
     *
     * @param A 系数矩阵，维度为 n×n，不能为空
     * @param b 常量向量，长度为 n，不能为空
     * @return 解向量 x（长度为 n）；若输入非法或矩阵奇异则返回 null
     * @throws IllegalArgumentException 当 A 与 b 维度不一致时抛出
     * @author AI Assistant
     * @since 1.0.0
     */
    private double[] solveLinearSystem(double[][] A, double[] b) {
        // 参数校验
        if (A == null || b == null) return null;
        int n = A.length;
        if (n == 0 || b.length != n) throw new IllegalArgumentException("矩阵与向量维度不一致");
        for (double[] row : A) {
            if (row == null || row.length != n) throw new IllegalArgumentException("系数矩阵必须为方阵");
        }

        // 深拷贝，避免修改入参
        double[][] M = new double[n][n];
        double[]   y = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            y[i] = b[i];
        }

        // 前向消元（带部分选主元）
        for (int k = 0; k < n; k++) {
            // 选择当前列的主元行
            int pivot = k;
            double maxAbs = Math.abs(M[k][k]);
            for (int i = k + 1; i < n; i++) {
                double val = Math.abs(M[i][k]);
                if (val > maxAbs) { pivot = i; maxAbs = val; }
            }
            // 奇异性检查
            if (maxAbs == 0.0) return null; // 奇异矩阵，无唯一解
            // 行交换：将主元行换到第 k 行
            if (pivot != k) {
                double[] tmpRow = M[k]; M[k] = M[pivot]; M[pivot] = tmpRow;
                double tmp = y[k]; y[k] = y[pivot]; y[pivot] = tmp;
            }

            // 归一化当前主元行（可选：提升数值稳定性）
            double diag = M[k][k];
            for (int j = k; j < n; j++) M[k][j] /= diag;
            y[k] /= diag;

            // 消去下面各行的第 k 列
            for (int i = k + 1; i < n; i++) {
                double factor = M[i][k];
                if (factor == 0.0) continue;
                for (int j = k; j < n; j++) M[i][j] -= factor * M[k][j];
                y[i] -= factor * y[k];
            }
        }

        // 回代求解
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];
            for (int j = i + 1; j < n; j++) sum -= M[i][j] * x[j];
            x[i] = sum; // 此时 M[i][i] 已被归一化为 1
        }

        return x;
    }
    /**
     * 启用中央子午线输入框的手动输入功能
     */
    private void enableCentralMeridianInput() {
        if (etCentralMeridian != null) {
            etCentralMeridian.setEnabled(true);
            etCentralMeridian.setBackgroundColor(android.graphics.Color.WHITE);
            etCentralMeridian.setFocusable(true);
            etCentralMeridian.setFocusableInTouchMode(true);

            // 添加文本变化监听器，当用户手动输入时标记为手动输入模式
            etCentralMeridian.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // 标记用户已手动输入中央子午线
                    userManuallyEnteredCentralMeridian = true;
                }
            });
        }
    }

    /**
     * 确保中央子午线输入框保持禁用状态（保留方法以备需要时使用）
     */
    private void ensureCentralMeridianDisabled() {
        if (etCentralMeridian != null) {
            etCentralMeridian.setEnabled(false);
            etCentralMeridian.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            etCentralMeridian.setFocusable(false);
            etCentralMeridian.setFocusableInTouchMode(false);
        }
    }

    private void attachClearButtonsRecursive(View root){
        if(root==null) return;
        if(root instanceof ViewGroup){
            ViewGroup vg = (ViewGroup) root;
            for(int i=0;i<vg.getChildCount();i++){
                View child = vg.getChildAt(i);
                attachClearButtonsRecursive(child);
            }
        }
        // 若当前 View 是清空图标且父行存在 EditText
        if(root instanceof ImageView){
            ImageView iv = (ImageView) root;
            if(iv.getDrawable()!=null && getResources().getResourceEntryName(R.drawable.ic_menu_close_clear_cancel).equals(getResources().getResourceEntryName(iv.getDrawable().getConstantState()==null?0:R.drawable.ic_menu_close_clear_cancel))){
                View parent = (View) iv.getParent();
                if(parent instanceof ViewGroup){
                    ViewGroup pg = (ViewGroup) parent;
                    // 查找同一行里的 EditText（取第一个）
                    for(int j=0;j<pg.getChildCount();j++){
                        View sib = pg.getChildAt(j);
                        if(sib instanceof EditText){
                            EditText et = (EditText) sib;

                            // 所有输入框都支持清除功能，包括中央子午线
                            final String def = et.getText().toString();
                            iv.setOnClickListener(v -> {
                                et.setText(def);
                                // 如果清除的是中央子午线，重置手动输入标记
                                if(et.getId() == R.id.et_central_meridian){
                                    userManuallyEnteredCentralMeridian = false;
                                }
                            });
                            break;
                        }
                    }
                }
            }
        }
    }

    // 弹窗显示已知点汇总
    /**
     * 计算水平精度和高程精度
     * @param knownCoords 已知点坐标 [N, E, H]
     * @param measuredCoords 测量点坐标 [B, L, H]
     * @return [水平精度, 高程精度]
     */
    private double[] calculateAccuracy(String[] knownCoords, String[] measuredCoords) {
        double horizontalAccuracy = 0.0;
        double elevationAccuracy = 0.0;

        try {
            if (knownCoords != null && measuredCoords != null &&
                    knownCoords.length >= 3 && measuredCoords.length >= 3) {

                // 解析已知点坐标
                double knownN = parseDouble(knownCoords[0]);
                double knownE = parseDouble(knownCoords[1]);
                double knownH = parseDouble(knownCoords[2]);

                // 解析测量点坐标
                double measuredB = parseDouble(measuredCoords[0]);
                double measuredL = parseDouble(measuredCoords[1]);
                double measuredH = parseDouble(measuredCoords[2]);

                // 将测量点的经纬度转换为投影坐标进行比较
                double[] projectedCoords = convertBLToProjection(measuredB, measuredL);
                if (projectedCoords != null && projectedCoords.length >= 2) {
                    double projectedN = projectedCoords[0];
                    double projectedE = projectedCoords[1];

                    // 计算水平精度 = √(ΔN² + ΔE²)
                    double deltaN = knownN - projectedN;
                    double deltaE = knownE - projectedE;
                    horizontalAccuracy = Math.sqrt(deltaN * deltaN + deltaE * deltaE);

                    // 计算高程精度 = |ΔH|
                    elevationAccuracy = Math.abs(knownH - measuredH);
                }
            }
        } catch (Exception e) {
            // 如果计算失败，使用默认值
            horizontalAccuracy = 0.0;
            elevationAccuracy = 0.0;
        }

        return new double[]{horizontalAccuracy, elevationAccuracy};
    }

    /**
     * 安全的字符串转double方法
     */
    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void showKnownPointsDialog(){
        if(pointCorrectionContent==null || containerKnownPoints==null) return;
        // 若有正在编辑的点, 保存当前输入
        if(lastKnownSel!=null){
            if(etKnownN!=null && etKnownE!=null && etKnownH!=null){
                knownPointValues.put(lastKnownSel.getText().toString(), new String[]{etKnownN.getText().toString(), etKnownE.getText().toString(), etKnownH.getText().toString()});
            }
            EditText etMeasuredB = pointCorrectionContent.findViewById(R.id.et_measured_b);
            EditText etMeasuredL = pointCorrectionContent.findViewById(R.id.et_measured_l);
            EditText etMeasuredH = pointCorrectionContent.findViewById(R.id.et_measured_h);
            if(etMeasuredB!=null && etMeasuredL!=null && etMeasuredH!=null){
                measuredPointValues.put(lastKnownSel.getText().toString(), new String[]{etMeasuredB.getText().toString(), etMeasuredL.getText().toString(), etMeasuredH.getText().toString()});
            }
        }

        // 获取已知点名称列表
        List<String> names = new java.util.ArrayList<>();
        for(int i=0;i<containerKnownPoints.getChildCount();i++){
            View child = containerKnownPoints.getChildAt(i);
            if(child instanceof LinearLayout){
                // 检查LinearLayout的第一个子视图是否是TextView（已知点）
                if(((LinearLayout)child).getChildCount() > 0 &&
                        ((LinearLayout)child).getChildAt(0) instanceof TextView){
                    TextView tv = (TextView) ((LinearLayout)child).getChildAt(0);
                    String text = tv.getText().toString();
                    // 只添加以"已知点"开头的文本，过滤掉按钮等其他内容
                    if(text.startsWith("已知点")){
                        names.add(text);
                    }
                }
            } else if(child instanceof TextView) {
                // 处理直接的TextView子项（如tv_known_point_1, tv_known_point_2）
                TextView tv = (TextView) child;
                String text = tv.getText().toString();
                // 只添加以"已知点"开头的文本
                if(text.startsWith("已知点")){
                    names.add(text);
                }
            }
            // 忽略Button等其他类型的子视图
        }

        // 如果没有已知点，至少显示默认的2个点
        if(names.isEmpty()){
            names.add("已知点1");
            names.add("已知点2");
        }

        // 创建自定义布局
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(24, 24, 24, 16);
        dialogLayout.setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"));

        // 创建表格容器
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.TableLayout table = new android.widget.TableLayout(this);
        table.setStretchAllColumns(true);
        table.setBackgroundColor(android.graphics.Color.WHITE);

        // 表头
        android.widget.TableRow header = new android.widget.TableRow(this);
        header.addView(createKnownPointCell("名称", true, false));
        for(String n:names){
            header.addView(createKnownPointCell(n, true, true));
        }
        table.addView(header);

        // 表格数据行
        String[] rowNames = {"水平精度","高程精度","北坐标","东坐标","高程","经度","纬度","大地高"};

        for(int i = 0; i < rowNames.length; i++){
            android.widget.TableRow tr = new android.widget.TableRow(this);
            tr.addView(createKnownPointCell(rowNames[i], false, false));

            // 根据实际已知点数量动态生成列
            for(int j = 0; j < names.size(); j++){
                String val = "";
                String pointName = names.get(j);

                // 从实际数据中获取
                String[] arr = knownPointValues.get(pointName);
                String[] measuredArr = measuredPointValues.get(pointName);

                if(arr != null && arr.length >= 3){
                    switch(rowNames[i]){
                        case "北坐标":
                            val = (arr[0] != null && !arr[0].isEmpty()) ? arr[0] + "m" : "0.000m";
                            break;
                        case "东坐标":
                            val = (arr[1] != null && !arr[1].isEmpty()) ? arr[1] + "m" : "0.000m";
                            break;
                        case "高程":
                            val = (arr[2] != null && !arr[2].isEmpty()) ? arr[2] + "m" : "0.000m";
                            break;
                        case "水平精度":
                        case "高程精度":
                            // 使用存储的精度值或计算精度
                            double[] accuracy = accuracyValues.get(pointName);
                            if (accuracy != null) {
                                if (rowNames[i].equals("水平精度")) {
                                    val = String.format("%.3fm", accuracy[0]);
                                } else {
                                    val = String.format("%.3fm", accuracy[1]);
                                }
                            } else {
                                // 如果没有存储的精度值，重新计算
                                accuracy = calculateAccuracy(arr, measuredArr);
                                if (rowNames[i].equals("水平精度")) {
                                    val = String.format("%.3fm", accuracy[0]);
                                } else {
                                    val = String.format("%.3fm", accuracy[1]);
                                }
                            }
                            break;
                        case "经度":
                            if(measuredArr != null && measuredArr.length >= 2 && measuredArr[1] != null && !measuredArr[1].isEmpty()) {
                                val = measuredArr[1];
                            } else {
                                val = "0.000000";
                            }
                            break;
                        case "纬度":
                            if(measuredArr != null && measuredArr.length >= 1 && measuredArr[0] != null && !measuredArr[0].isEmpty()) {
                                val = measuredArr[0];
                            } else {
                                val = "0.000000";
                            }
                            break;
                        case "大地高":
                            if(measuredArr != null && measuredArr.length >= 3 && measuredArr[2] != null && !measuredArr[2].isEmpty()) {
                                val = measuredArr[2] + "m";
                            } else {
                                val = "0.000m";
                            }
                            break;
                    }
                } else {
                    // 默认值
                    switch(rowNames[i]){
                        case "水平精度":
                        case "高程精度":
                        case "北坐标":
                        case "东坐标":
                        case "高程":
                        case "大地高":
                            val = "0.000m";
                            break;
                        case "纬度":
                        case "经度":
                            val = "0.000000";
                            break;
                    }
                }
                tr.addView(createKnownPointCell(val, false, true));
            }
            table.addView(tr);
        }

        sv.addView(table);
        dialogLayout.addView(sv);

        // 创建按钮布局
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        buttonLayout.setPadding(0, 24, 0, 0);

        // 关闭按钮
        Button closeButton = new Button(this);
        closeButton.setText("关闭");
        closeButton.setTextColor(android.graphics.Color.BLACK);
        closeButton.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"));
        closeButton.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        closeParams.setMargins(0, 0, 8, 0);
        closeButton.setLayoutParams(closeParams);

        // 保存按钮
        Button saveButton = new Button(this);
        saveButton.setText("保存");
        saveButton.setTextColor(android.graphics.Color.WHITE);
        saveButton.setBackgroundColor(android.graphics.Color.BLACK);
        saveButton.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveParams.setMargins(8, 0, 0, 0);
        saveButton.setLayoutParams(saveParams);

        buttonLayout.addView(closeButton);
        buttonLayout.addView(saveButton);
        dialogLayout.addView(buttonLayout);

        // 创建对话框
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setCancelable(true)
                .create();

        // 设置按钮点击事件
        closeButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            // 联动保存：先保存坐标系，再保存已知点，然后计算参数
            saveCoordinateSystemAndKnownPoints();
            dialog.dismiss();
        });

        dialog.show();

        // 设置对话框大小和位置
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.7);
            window.setAttributes(params);
            window.setGravity(Gravity.CENTER);
        }
    }

    private TextView createKnownPointCell(String txt, boolean bold, boolean knownCol){
        TextView tv = new TextView(this);
        tv.setText(txt==null?"":txt);
        tv.setPadding(12, 16, 12, 16);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(14);

        if(bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextColor(android.graphics.Color.BLACK);
        } else {
            tv.setTextColor(android.graphics.Color.parseColor("#333333"));
        }

        // 背景色与边框
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        if(bold) {
            // 表头样式
            gd.setColor(android.graphics.Color.parseColor("#E8E8E8"));
        } else if(knownCol) {
            // 数据列样式
            gd.setColor(android.graphics.Color.parseColor("#F8F8F8"));
        } else {
            // 行标签样式
            gd.setColor(android.graphics.Color.parseColor("#F0F0F0"));
        }

        int stroke = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, getResources().getDisplayMetrics());
        gd.setStroke(stroke, android.graphics.Color.parseColor("#DDDDDD"));
        tv.setBackground(gd);

        return tv;
    }

    private void updateHeightPanelVisibility(){
        // 0=无参数 1=垂直平差 2=平面拟合 3=曲面拟合 4=加权平均
        if(phVertical!=null) phVertical.setVisibility(selectedHeightFitIndex==1? View.VISIBLE: View.GONE);  // 垂直平差
        if(phPlane!=null)    phPlane   .setVisibility(selectedHeightFitIndex==2? View.VISIBLE: View.GONE);  // 平面拟合
        if(phSurface!=null)  phSurface .setVisibility(selectedHeightFitIndex==3? View.VISIBLE: View.GONE);  // 曲面拟合
        if(phWeighted!=null) phWeighted.setVisibility(selectedHeightFitIndex==4? View.VISIBLE: View.GONE);  // 加权平均
        // 无参数时所有面板都隐藏
    }

    private Double getDouble(int viewId){
        View v = findViewById(viewId);
        if(!(v instanceof TextView)) return null;
        String txt = ((TextView)v).getText().toString().trim();
        if(txt.isEmpty()) return null;
        try{ return Double.valueOf(txt);}catch(Exception e){ return null; }
    }

    // 从某一行(rowId)的第 index 个子 View(0 基) 提取 EditText 数值
    private Double getDoubleFromRow(int rowId, int childIndex){
        View row = findViewById(rowId);
        if(!(row instanceof ViewGroup)) return null;
        ViewGroup ll = (ViewGroup) row;
        if(childIndex>=ll.getChildCount()) return null;
        View v = ll.getChildAt(childIndex);
        if(v instanceof EditText){
            String txt = ((EditText)v).getText().toString().trim();
            if(txt.isEmpty()) return null;
            try{ return Double.valueOf(txt);}catch(Exception ignore){}
        }
        return null;
    }

    private void updateCoordinateLabels(int coordinateTypeIndex) {
        if (pointCorrectionContent == null) return;

        TextView label1 = pointCorrectionContent.findViewById(R.id.tv_coord_label_1);
        TextView label2 = pointCorrectionContent.findViewById(R.id.tv_coord_label_2);
        TextView label3 = pointCorrectionContent.findViewById(R.id.tv_coord_label_3);

        if (label1 != null && label2 != null && label3 != null) {
            if (coordinateTypeIndex == 0) {
                // N(X), E(Y), H(Z)
                label1.setText("北(X)");
                label2.setText("东(Y)");
                label3.setText("高(Z)");
            } else if (coordinateTypeIndex == 1) {
                // N(Y), E(X), H(Z)
                label1.setText("北(Y)");
                label2.setText("东(X)");
                label3.setText("高(Z)");
            }
        }
    }

    /**
     * 保存加权平均参数到数据库
     */
    private void saveWeightedAverageParameters(double weightedAverage, double weightedRMSE,
                                               double[] weights, double[] dataPoints) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                // 获取当前坐标系
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存加权平均参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 构建加权平均参数JSON
                org.json.JSONObject heightJson = new org.json.JSONObject();
                try {
                    heightJson.put("type", 4); // 4=加权平均
                    heightJson.put("method", "weighted_average");
                    heightJson.put("a", weightedAverage);        // A: 加权平均值 (m)
                    heightJson.put("weighted_rmse", weightedRMSE); // 加权均方根误差
                    heightJson.put("point_count", dataPoints.length);
                    heightJson.put("timestamp", System.currentTimeMillis());

                    // 保存权重和数据点信息
                    org.json.JSONArray weightsArray = new org.json.JSONArray();
                    org.json.JSONArray dataArray = new org.json.JSONArray();
                    for (int i = 0; i < weights.length; i++) {
                        weightsArray.put(weights[i]);
                        dataArray.put(dataPoints[i]);
                    }
                    heightJson.put("weights", weightsArray);
                    heightJson.put("data_points", dataArray);

                    // 更新坐标系的高程参数
                    cs.setHeightParams(heightJson.toString());
                    csRepo.update(cs);

                    runOnUiThread(() -> {
                        Toast.makeText(this, "加权平均参数已保存到数据库", Toast.LENGTH_SHORT).show();
                    });

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "加权平均参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存加权平均参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 更新加权平均界面显示
     */
    private void updateWeightedAverageUI(double weightedAverage) {
        // 确保加权平均面板可见
        if (phWeighted != null) {
            phWeighted.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        runOnUiThread(() -> {
            if (phWeighted != null) {
                phWeighted.post(() -> {
                    EditText etA = findViewById(R.id.tv_hf_w_a);

                    if (etA != null) {
                        etA.setText(String.format("%.6f", weightedAverage));
                    } else {
                        Toast.makeText(this, "警告: 找不到加权平均A参数输入框", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 保存8参数曲面拟合参数到数据库
     */
    private void saveSurfaceFittingParameters(double northOrigin, double eastOrigin,
                                              double[] params, double rmse) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                // 获取当前坐标系
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存曲面拟合参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 构建8参数曲面拟合参数JSON
                org.json.JSONObject heightJson = new org.json.JSONObject();
                try {
                    heightJson.put("type", 3); // 3=8参数曲面拟合
                    heightJson.put("method", "surface_fitting_8param");

                    // 8个曲面参数
                    heightJson.put("a", params[0]);  // 常数项
                    heightJson.put("b", params[1]);  // x的一次项系数
                    heightJson.put("c", params[2]);  // y的一次项系数
                    heightJson.put("d", params[3]);  // x²的二次项系数
                    heightJson.put("e", params[4]);  // y²的二次项系数
                    heightJson.put("f", params[5]);  // xy的交叉项系数
                    heightJson.put("g", params[6]);  // x³的三次项系数
                    heightJson.put("h", params[7]);  // y³的三次项系数

                    // 坐标原点
                    heightJson.put("north_origin", northOrigin);
                    heightJson.put("east_origin", eastOrigin);

                    // 精度信息
                    heightJson.put("rmse", rmse);
                    heightJson.put("timestamp", System.currentTimeMillis());

                    // 更新坐标系的高程参数
                    cs.setHeightParams(heightJson.toString());
                    csRepo.update(cs);

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "曲面拟合参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存曲面拟合参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
    /**
     * 更新8参数曲面拟合界面显示
     */
    private void updateSurfaceFittingUI(double northOrigin, double eastOrigin, double[] params) {
        // 确保曲面拟合面板可见
        if (phSurface != null) {
            phSurface.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        runOnUiThread(() -> {
            if (phSurface != null) {
                phSurface.post(() -> {
                    // 查找8个参数输入框
                    EditText etA = findViewById(R.id.tv_hf_s_a);
                    EditText etB = findViewById(R.id.tv_hf_s_b);
                    EditText etC = findViewById(R.id.tv_hf_s_c);
                    EditText etD = findViewById(R.id.tv_hf_s_d);
                    EditText etE = findViewById(R.id.tv_hf_s_e);
                    EditText etF = findViewById(R.id.tv_hf_s_f);
                    EditText etG = findViewById(R.id.tv_hf_s_g);
                    EditText etH = findViewById(R.id.tv_hf_s_h);
                    EditText etN0 = findViewById(R.id.tv_hf_s_n0);
                    EditText etE0 = findViewById(R.id.tv_hf_s_e0);

                    // 更新8个参数
                    if (etA != null) {
                        etA.setText(String.format("%.9f", params[0]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合a输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etB != null) {
                        etB.setText(String.format("%.9f", params[1]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合b输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etC != null) {
                        etC.setText(String.format("%.9f", params[2]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合c输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etD != null) {
                        etD.setText(String.format("%.9f", params[3]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合d输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etE != null) {
                        etE.setText(String.format("%.9f", params[4]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合e输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etF != null) {
                        etF.setText(String.format("%.9f", params[5]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合f输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etG != null) {
                        etG.setText(String.format("%.12f", params[6]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合g输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etH != null) {
                        etH.setText(String.format("%.12f", params[7]));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合h输入框", Toast.LENGTH_SHORT).show();
                    }

                    // 更新坐标原点
                    if (etN0 != null) {
                        etN0.setText(String.format("%.6f", northOrigin));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合北原点输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etE0 != null) {
                        etE0.setText(String.format("%.6f", eastOrigin));
                    } else {
                        Toast.makeText(this, "警告: 找不到曲面拟合东原点输入框", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 保存垂直平差参数到数据库
     */
    private void saveVerticalAdjustmentParameters(double northOrigin, double eastOrigin,
                                                  double northSlope, double eastSlope,
                                                  double heightConstant, double rmse) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                // 获取当前坐标系
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存垂直平差参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 构建垂直平差参数JSON
                org.json.JSONObject heightJson = new org.json.JSONObject();
                try {
                    heightJson.put("type", 1); // 1=垂直平差
                    heightJson.put("method", "vertical_adjustment");
                    heightJson.put("n0", northOrigin);        // 北原点
                    heightJson.put("e0", eastOrigin);         // 东原点
                    heightJson.put("nslope", northSlope);     // 北斜坡
                    heightJson.put("eslope", eastSlope);      // 东斜坡
                    heightJson.put("const", heightConstant);  // 高差常量
                    heightJson.put("rmse", rmse);             // 均方根误差
                    heightJson.put("timestamp", System.currentTimeMillis());

                    // 更新坐标系的高程参数
                    cs.setHeightParams(heightJson.toString());
                    csRepo.update(cs);

                    runOnUiThread(() -> {
                        Toast.makeText(this, "垂直平差参数已保存到数据库", Toast.LENGTH_SHORT).show();
                    });

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "垂直平差参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存垂直平差参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 更新垂直平差界面显示
     */
    private void updateVerticalAdjustmentUI(double northOrigin, double eastOrigin,
                                            double northSlope, double eastSlope,
                                            double heightConstant) {
        // 确保垂直平差面板可见
        if (phVertical != null) {
            phVertical.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        runOnUiThread(() -> {
            if (phVertical != null) {
                phVertical.post(() -> {
                    EditText etN0 = findViewById(R.id.tv_hf_v_n0);
                    EditText etE0 = findViewById(R.id.tv_hf_v_e0);
                    EditText etNslope = findViewById(R.id.tv_hf_v_nslope);
                    EditText etEslope = findViewById(R.id.tv_hf_v_eslope);
                    EditText etConst = findViewById(R.id.tv_hf_v_const);

                    if (etN0 != null) {
                        etN0.setText(String.format("%.6f", northOrigin));
                    } else {
                        Toast.makeText(this, "警告: 找不到垂直平差北原点输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etE0 != null) {
                        etE0.setText(String.format("%.6f", eastOrigin));
                    } else {
                        Toast.makeText(this, "警告: 找不到垂直平差东原点输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etNslope != null) {
                        etNslope.setText(String.format("%.6f", northSlope));
                    } else {
                        Toast.makeText(this, "警告: 找不到垂直平差北斜坡输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etEslope != null) {
                        etEslope.setText(String.format("%.6f", eastSlope));
                    } else {
                        Toast.makeText(this, "警告: 找不到垂直平差东斜坡输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etConst != null) {
                        etConst.setText(String.format("%.6f", heightConstant));
                    } else {
                        Toast.makeText(this, "警告: 找不到垂直平差高差常量输入框", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 保存平面拟合参数到数据库
     */
    private void savePlaneFittingParameters(double northOrigin, double eastOrigin,
                                            double paramA, double paramB, double paramC, double rmse) {
        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                // 获取当前坐标系
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs == null) {
                    runOnUiThread(() -> Toast.makeText(this, "找不到坐标系，无法保存平面拟合参数", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 构建平面拟合参数JSON
                org.json.JSONObject heightJson = new org.json.JSONObject();
                try {
                    heightJson.put("type", 2); // 2=平面拟合
                    heightJson.put("method", "plane_fitting");
                    heightJson.put("a", paramA);             // A: 常数项 (m)
                    heightJson.put("b", paramB);             // B: 东向系数
                    heightJson.put("c", paramC);             // C: 北向系数
                    heightJson.put("n0", northOrigin);       // 北原点
                    heightJson.put("e0", eastOrigin);        // 东原点
                    heightJson.put("rmse", rmse);            // 均方根误差
                    heightJson.put("timestamp", System.currentTimeMillis());

                    // 更新坐标系的高程参数
                    cs.setHeightParams(heightJson.toString());
                    csRepo.update(cs);

                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "平面拟合参数格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存平面拟合参数失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 更新平面拟合界面显示
     */
    private void updatePlaneFittingUI(double northOrigin, double eastOrigin,
                                      double paramA, double paramB, double paramC) {
        // 确保平面拟合面板可见
        if (phPlane != null) {
            phPlane.setVisibility(View.VISIBLE);
        }

        // 等待UI更新后再查找元素
        runOnUiThread(() -> {
            if (phPlane != null) {
                phPlane.post(() -> {
                    EditText etA = findViewById(R.id.tv_hf_p_a);
                    EditText etB = findViewById(R.id.tv_hf_p_b);
                    EditText etC = findViewById(R.id.tv_hf_p_c);
                    EditText etN0 = findViewById(R.id.tv_hf_p_n0);
                    EditText etE0 = findViewById(R.id.tv_hf_p_e0);

                    if (etA != null) {
                        etA.setText(String.format("%.6f", paramA));
                    } else {
                        Toast.makeText(this, "警告: 找不到平面拟合A参数输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etB != null) {
                        etB.setText(String.format("%.6f", paramB));
                    } else {
                        Toast.makeText(this, "警告: 找不到平面拟合B参数输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etC != null) {
                        etC.setText(String.format("%.6f", paramC));
                    } else {
                        Toast.makeText(this, "警告: 找不到平面拟合C参数输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etN0 != null) {
                        etN0.setText(String.format("%.6f", northOrigin));
                    } else {
                        Toast.makeText(this, "警告: 找不到平面拟合北原点输入框", Toast.LENGTH_SHORT).show();
                    }

                    if (etE0 != null) {
                        etE0.setText(String.format("%.6f", eastOrigin));
                    } else {
                        Toast.makeText(this, "警告: 找不到平面拟合东原点输入框", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 保存四参数计算的详细数据到数据库
     */
    private void saveFourParameterCalculationData(
            FourParameterSolver.FourParameterTransform fourParam,
            List<Point2D> srcPoints,
            List<Point2D> dstPoints,
            double rmse,
            boolean isValid,
            String debugInfo) {

        if (currentCsId <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                // 构建计算数据JSON
                org.json.JSONObject calculationData = new org.json.JSONObject();

                // 基本计算结果
                calculationData.put("calculation_type", "four_parameter");
                calculationData.put("timestamp", System.currentTimeMillis());
                calculationData.put("coordinate_system_id", currentCsId);

                // 四参数结果
                org.json.JSONObject parameters = new org.json.JSONObject();
                parameters.put("dx", fourParam.getDx());
                parameters.put("dy", fourParam.getDy());
                parameters.put("theta_radians", fourParam.getTheta());
                parameters.put("theta_degrees", Math.toDegrees(fourParam.getTheta()));
                parameters.put("k", fourParam.getK());
                calculationData.put("parameters", parameters);

                // 验证结果
                org.json.JSONObject validation = new org.json.JSONObject();
                validation.put("rmse", rmse);
                validation.put("is_valid", isValid);
                validation.put("point_count", srcPoints.size());
                calculationData.put("validation", validation);

                // 源点数据
                org.json.JSONArray srcPointsArray = new org.json.JSONArray();
                for (int i = 0; i < srcPoints.size(); i++) {
                    org.json.JSONObject point = new org.json.JSONObject();
                    point.put("index", i + 1);
                    point.put("x", srcPoints.get(i).x);
                    point.put("y", srcPoints.get(i).y);
                    srcPointsArray.put(point);
                }
                calculationData.put("source_points", srcPointsArray);

                // 目标点数据
                org.json.JSONArray dstPointsArray = new org.json.JSONArray();
                for (int i = 0; i < dstPoints.size(); i++) {
                    org.json.JSONObject point = new org.json.JSONObject();
                    point.put("index", i + 1);
                    point.put("x", dstPoints.get(i).x);
                    point.put("y", dstPoints.get(i).y);
                    dstPointsArray.put(point);
                }
                calculationData.put("target_points", dstPointsArray);

                // 变换验证数据（修正：fourParam 是反向转换，从国家投影坐标 → 地方平面坐标）
                org.json.JSONArray transformedPointsArray = new org.json.JSONArray();
                for (int i = 0; i < srcPoints.size(); i++) {
                    double[] transformed = fourParam.transform(srcPoints.get(i).x, srcPoints.get(i).y);
                    org.json.JSONObject point = new org.json.JSONObject();
                    point.put("index", i + 1);
                    point.put("source_x", srcPoints.get(i).x);  // 源坐标（国家投影）
                    point.put("source_y", srcPoints.get(i).y);
                    point.put("transformed_x", transformed[0]);  // 变换后（地方平面）
                    point.put("transformed_y", transformed[1]);
                    point.put("target_x", dstPoints.get(i).x);  // 目标坐标（地方平面）
                    point.put("target_y", dstPoints.get(i).y);
                    point.put("error_x", transformed[0] - dstPoints.get(i).x);
                    point.put("error_y", transformed[1] - dstPoints.get(i).y);
                    double pointError = Math.sqrt(Math.pow(transformed[0] - dstPoints.get(i).x, 2) +
                            Math.pow(transformed[1] - dstPoints.get(i).y, 2));
                    point.put("point_error", pointError);
                    transformedPointsArray.put(point);
                }
                calculationData.put("transformed_points", transformedPointsArray);

                // 调试信息
                calculationData.put("debug_info", debugInfo);

                // 获取当前坐标系并更新计算历史
                CoordinateSystem cs = csRepo.getById(currentCsId);
                if (cs != null) {
                    // 获取现有的计算历史
                    String existingHistory = cs.getCalculationHistory();
                    org.json.JSONArray historyArray;

                    if (existingHistory != null && !existingHistory.isEmpty()) {
                        try {
                            historyArray = new org.json.JSONArray(existingHistory);
                        } catch (org.json.JSONException e) {
                            historyArray = new org.json.JSONArray();
                        }
                    } else {
                        historyArray = new org.json.JSONArray();
                    }

                    // 添加新的计算记录
                    historyArray.put(calculationData);

                    // 限制历史记录数量（保留最近20次）
                    if (historyArray.length() > 20) {
                        org.json.JSONArray newHistoryArray = new org.json.JSONArray();
                        for (int i = historyArray.length() - 20; i < historyArray.length(); i++) {
                            newHistoryArray.put(historyArray.get(i));
                        }
                        historyArray = newHistoryArray;
                    }

                    // 更新坐标系的计算历史
                    cs.setCalculationHistory(historyArray.toString());
                    csRepo.update(cs);
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存计算数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // ==================== 高斯投影自动参数计算 ====================

    /**
     * 计算UTM投影参数
     */
    private OptimalProjectionParams calculateUTMProjectionParameters(List<ControlPoint> points, ProjectionRegion region) {
        String ellipsoid = getSelectedEllipsoidName();

        // 计算UTM分度带
        ZoneInfo utmZone = calculateUTMZone(region.avgLon, region.avgLat);

        // UTM投影标准参数
        double standardK0 = 0.9996; // UTM标准比例因子

        // 简化处理：UTM使用标准参数，不进行优化
        double optimalK0 = standardK0;
        double maxResidual = 0.0;
        double rmsResidual = 0.0;

        return new OptimalProjectionParams(utmZone.centralMeridian, optimalK0, standardK0,
                maxResidual, rmsResidual, ellipsoid);
    }

    /**
     * 计算高斯投影参数（统一使用3度带）
     */
    private OptimalProjectionParams calculateGaussProjectionParameters(List<ControlPoint> points, ProjectionRegion region) {
        String ellipsoid = getSelectedEllipsoidName();

        // 使用 CentralMeridianCalculation 计算中央子午线（3度带）
        CentralMeridianCalculation.BaseResult result = 
            CentralMeridianCalculation.calculateForGaussKruger3Degree(region.avgLon, region.avgLat);
        double centralMeridian = result.centralMeridianDegrees;

        // 3度带标准k₀值
        double standardK0 = 1.0; // 高斯投影标准比例因子

        // 简化处理：对于3度带，不进行残差评估和k₀优化
        // 因为3度带的投影变形已经很小，通常不需要优化
        double optimalK0 = standardK0;
        double maxResidual = 0.0;
        double rmsResidual = 0.0;

        return new OptimalProjectionParams(centralMeridian, optimalK0, standardK0,
                maxResidual, rmsResidual, ellipsoid);
    }

    /**
     * 投影区域信息类
     */
    private static class ProjectionRegion {
        public final double minLon, maxLon, avgLon;
        public final double minLat, maxLat, avgLat;
        public final double lonRange;

        public ProjectionRegion(double minLon, double maxLon, double avgLon,
                                double minLat, double maxLat, double avgLat) {
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.avgLon = avgLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.avgLat = avgLat;
            this.lonRange = maxLon - minLon;
        }
    }

    /**
     * 最优投影参数类
     */
    private static class OptimalProjectionParams {
        public final double centralMeridian;
        public final double optimalK0;
        public final double standardK0;
        public final double maxResidual;
        public final double rmsResidual;
        public final String ellipsoid;

        public OptimalProjectionParams(double centralMeridian,
                                       double optimalK0, double standardK0,
                                       double maxResidual, double rmsResidual, String ellipsoid) {
            this.centralMeridian = centralMeridian;
            this.optimalK0 = optimalK0;
            this.standardK0 = standardK0;
            this.maxResidual = maxResidual;
            this.rmsResidual = rmsResidual;
            this.ellipsoid = ellipsoid;
        }
    }

    /**
     * 控制点数据类
     * 支持地理坐标、平面坐标和高程信息
     */
    private static class ControlPoint {
        // 地理坐标
        public final double lat, lon;                    // 纬度、经度 (度)

        // 平面坐标（可选，用于验证和比较）
        public final Double knownX, knownY;              // 已知平面坐标 (米)
        public final String knownCoordinateSystem;       // 已知坐标系名称

        // 高程信息
        public final double geodeticHeight;              // 大地高 (米)
        public final double orthometricHeight;           // 正常高/水准高 (米)

        // 点位信息
        public final String name;                        // 控制点名称
        public final String pointNumber;                 // 点号
        public final String grade;                       // 等级（如：一等、二等等）

        // 构造函数1：只有地理坐标（用于从零建立投影坐标系）
        public ControlPoint(String name, double lat, double lon, double geodeticHeight, double orthometricHeight) {
            this(name, null, null, lat, lon, null, null, null, geodeticHeight, orthometricHeight);
        }

        // 构造函数2：包含已知平面坐标（用于验证和优化）
        public ControlPoint(String name, String pointNumber, String grade,
                            double lat, double lon,
                            Double knownX, Double knownY, String knownCoordinateSystem,
                            double geodeticHeight, double orthometricHeight) {
            this.name = name;
            this.pointNumber = pointNumber;
            this.grade = grade;
            this.lat = lat;
            this.lon = lon;
            this.knownX = knownX;
            this.knownY = knownY;
            this.knownCoordinateSystem = knownCoordinateSystem;
            this.geodeticHeight = geodeticHeight;
            this.orthometricHeight = orthometricHeight;
        }

        // 判断是否有已知平面坐标
        public boolean hasKnownPlaneCoordinates() {
            return knownX != null && knownY != null;
        }

        // 获取点位描述
        public String getPointDescription() {
            StringBuilder desc = new StringBuilder(name);
            if (pointNumber != null) desc.append(" (").append(pointNumber).append(")");
            if (grade != null) desc.append(" [").append(grade).append("]");
            return desc.toString();
        }
    }

    /**
     * 确定投影区域
     * 识别工程区域的经度范围，计算平均经度作为自定义中央子午线
     */
    private ProjectionRegion determineProjectionRegion(List<ControlPoint> points) {
        double minLon = Double.MAX_VALUE;
        double maxLon = Double.MIN_VALUE;
        double minLat = Double.MAX_VALUE;
        double maxLat = Double.MIN_VALUE;
        double sumLon = 0, sumLat = 0;

        for (ControlPoint point : points) {
            minLon = Math.min(minLon, point.lon);
            maxLon = Math.max(maxLon, point.lon);
            minLat = Math.min(minLat, point.lat);
            maxLat = Math.max(maxLat, point.lat);
            sumLon += point.lon;
            sumLat += point.lat;
        }

        double avgLon = sumLon / points.size();
        double avgLat = sumLat / points.size();

        return new ProjectionRegion(minLon, maxLon, avgLon, minLat, maxLat, avgLat);
    }
    /**
     * 优化投影参数
     * 根据坐标系统理论，使用标准分度带计算方法（统一使用3度带）
     */
    private OptimalProjectionParams optimizeProjectionParameters(List<ControlPoint> points, ProjectionRegion region) {
        String ellipsoid = getSelectedEllipsoidName();

        // 根据椭球类型选择合适的投影方式
        ZoneInfo zone;
        double standardK0;

        if ("WGS84".equals(ellipsoid)) {
            // WGS84使用UTM投影
            zone = calculateUTMZone(region.avgLon, region.avgLat);
            standardK0 = 0.9996; // UTM标准比例因子
        } else {
            // CGCS2000、北京54、西安80使用高斯-克吕格投影（统一3度带）
            // 使用 CentralMeridianCalculation 计算中央子午线
            CentralMeridianCalculation.BaseResult result = 
                CentralMeridianCalculation.calculateForGaussKruger3Degree(region.avgLon, region.avgLat);
            zone = new ZoneInfo(0, result.centralMeridianDegrees); // 高斯投影不使用带号
            standardK0 = 1.0; // 高斯3度带标准比例因子
        }

        // 简化处理：使用标准参数，不进行优化
        // 因为3度带的投影变形已经很小，通常不需要优化k₀
        return new OptimalProjectionParams(zone.centralMeridian, standardK0, standardK0, 0.0, 0.0, ellipsoid);
    }


    /**
     * 验证已知平面坐标
     * 如果控制点包含已知平面坐标，计算与理论投影坐标的差异
     */
    private void validateKnownPlaneCoordinates(List<ControlPoint> points, OptimalProjectionParams params) {
        boolean hasKnownCoordinates = points.stream().anyMatch(ControlPoint::hasKnownPlaneCoordinates);

        if (!hasKnownCoordinates) {
            return;
        }

        double maxDiff = 0;
        double sumSquaredDiff = 0;
        int validCount = 0;

        for (ControlPoint point : points) {
            if (!point.hasKnownPlaneCoordinates()) continue;

            // 使用计算出的投影参数计算理论坐标
            double[] calculated = convertToGaussKrugerWithK0(
                    point.lat, point.lon,
                    params.centralMeridian, params.optimalK0,
                    params.ellipsoid
            );

            // 计算与已知坐标的差异
            double diffX = calculated[0] - point.knownX;
            double diffY = calculated[1] - point.knownY;
            double totalDiff = Math.sqrt(diffX * diffX + diffY * diffY);

            maxDiff = Math.max(maxDiff, totalDiff);
            sumSquaredDiff += totalDiff * totalDiff;
            validCount++;
        }

        if (validCount > 0) {
            double rmsDiff = Math.sqrt(sumSquaredDiff / validCount);
            // 在UI中显示验证结果
            final int finalValidCount = validCount;
            final double finalMaxDiff = maxDiff;
            final double finalRmsDiff = rmsDiff;
        }
    }

    /**
     * 将经纬度转换为高斯-克吕格投影坐标（带自定义k0）
     * 
     * @param lat 纬度（度）
     * @param lon 经度（度）
     * @param centralMeridian 中央子午线（度）
     * @param k0 投影比例因子
     * @param ellipsoid 椭球名称
     * @return [X, Y] 坐标数组（米）
     */
    private double[] convertToGaussKrugerWithK0(double lat, double lon, 
                                                 double centralMeridian, double k0, 
                                                 String ellipsoid) {
        // 根据椭球类型获取参数
        double semiMajor = 6378137.0; // 默认CGCS2000
        double invF = 298.257222101;
        
        switch (ellipsoid) {
            case "WGS84":
                semiMajor = 6378137.0;
                invF = 298.257223563;
                break;
            case "北京54":
                semiMajor = 6378245.0;
                invF = 298.3;
                break;
            case "西安80":
                semiMajor = 6378140.0;
                invF = 298.257;
                break;
            case "CGCS2000":
            default:
                semiMajor = 6378137.0;
                invF = 298.257222101;
                break;
        }
        
        // 使用GaussKrugerCGCS2000的通用方法进行转换
        GaussKrugerCGCS2000.XY xy = GaussKrugerCGCS2000.lonLatToXY(
                lon, lat, centralMeridian, semiMajor, invF);
        
        // 应用自定义的k0因子（标准方法使用k0=1.0，这里需要调整）
        // 注意：GaussKrugerCGCS2000.lonLatToXY已经使用了内置的SCALE_K0
        // 如果需要不同的k0，需要进行调整
        double adjustedX = xy.northing * k0;
        double adjustedY = xy.easting * k0;
        
        return new double[]{adjustedX, adjustedY};
    }

    /**
     * 计算投影面高
     * 基于控制点的大地高和正常高计算平均高程异常
     */
    private double calculateProjectionHeight(List<ControlPoint> points) {
        double sumHeightAnomaly = 0;
        int validPoints = 0;

        for (ControlPoint point : points) {
            if (point.geodeticHeight != 0 && point.orthometricHeight != 0) {
                double heightAnomaly = point.geodeticHeight - point.orthometricHeight;
                sumHeightAnomaly += heightAnomaly;
                validPoints++;
            }
        }

        if (validPoints == 0) {
            return 0.0;
        }

        double avgHeightAnomaly = sumHeightAnomaly / validPoints;

        return avgHeightAnomaly;
    }

    /**
     * 将计算的高斯投影参数设置到UI（只读模式）
     * 所有参数将被自动填充并禁用编辑
     */
    private void setGaussProjectionParametersToUI(OptimalProjectionParams params, double projectionHeight, ProjectionRegion region) {
        runOnUiThread(() -> {
            try {
                // 设置中央子午线
                EditText etCentralMeridian = findViewById(R.id.et_central_meridian);
                if (etCentralMeridian != null) {
                    etCentralMeridian.setText(String.format("%.8f", params.centralMeridian));
                    // 投影参数计算完成，保持中央子午线输入框可编辑
                }

                // 设置原点纬度为0 - 使用行查找方法
                setEditTextInRow(R.id.row_origin_lat, "0.0000000", false);

                // 设置投影比例尺
                EditText etScaleFactor = findViewById(R.id.et_scale_factor);
                if (etScaleFactor != null) {
                    etScaleFactor.setText(String.format("%.6f", params.optimalK0));
                    etScaleFactor.setEnabled(false); // 禁用编辑
                    etScaleFactor.setBackgroundColor(android.graphics.Color.TRANSPARENT); // 设置透明背景
                }

                // 设置东向加常数为500000
                EditText etAddEast = findViewById(R.id.et_add_east);
                if (etAddEast != null) {
                    etAddEast.setText("500000");
                    etAddEast.setEnabled(false); // 禁用编辑
                    etAddEast.setBackgroundColor(android.graphics.Color.TRANSPARENT); // 设置透明背景
                }

                // 设置北向加常数为0 - 使用行查找方法
                setEditTextInRow(R.id.row_add_north, "0", false);

                // 设置带号 - 使用行查找方法（3度带）
                setEditTextInRow(R.id.row_zone, String.valueOf((int)(params.centralMeridian / 3) + 1), false);

                // 设置平均纬度
                setAverageLatitude(region.avgLat, false);

                // 设置投影面高 - 使用行查找方法
                setProjectionHeightInCard(projectionHeight, false);

                // 更新投影模型显示
                TextView tvProjectionModel = findViewById(R.id.tv_projection_model);
                if (tvProjectionModel != null) {
                    tvProjectionModel.setText("高斯投影（3度带）");
                }

                // 默认选择北半球（高斯投影也默认北半球）
                RadioButton rbNorth = findViewById(R.id.rb_north);
                if (rbNorth != null) {
                    rbNorth.setChecked(true);
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 在指定行中设置EditText的值和状态
     */
    private void setEditTextInRow(int rowId, String value, boolean enabled) {
        View row = findViewById(rowId);
        if (row instanceof ViewGroup) {
            ViewGroup rowGroup = (ViewGroup) row;
            if (rowGroup.getChildCount() > 1 && rowGroup.getChildAt(1) instanceof EditText) {
                EditText editText = (EditText) rowGroup.getChildAt(1);
                if (value != null) {
                    editText.setText(value);
                }
                editText.setEnabled(enabled);
                if (!enabled) {
                    editText.setBackgroundColor(android.graphics.Color.TRANSPARENT); // 设置透明背景
                } else {
                    editText.setBackgroundColor(0x00000000); // 恢复透明背景
                }
            }
        }
    }

    /**
     * 设置平均纬度
     */
    private void setAverageLatitude(double avgLat, boolean enabled) {
        // 平均纬度在投影卡片的第6个LinearLayout（从0开始计数）
        ViewGroup projectionCard = findViewById(R.id.card_touying);
        if (projectionCard instanceof ViewGroup) {
            ViewGroup cardContent = (ViewGroup) ((ViewGroup) projectionCard).getChildAt(0);
            if (cardContent.getChildCount() > 6) {
                View avgLatRow = cardContent.getChildAt(6);
                if (avgLatRow instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) avgLatRow;
                    if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                        EditText etAverageLat = (EditText) row.getChildAt(1);
                        if (avgLat != 0) { // 只有在有值时才设置
                            etAverageLat.setText(String.format("%.8f", avgLat));
                        }
                        etAverageLat.setEnabled(enabled);
                        if (!enabled) {
                            etAverageLat.setBackgroundColor(android.graphics.Color.TRANSPARENT); // 设置透明背景
                        } else {
                            etAverageLat.setBackgroundColor(0x00000000); // 恢复透明背景
                        }
                    }
                }
            }
        }
    }

    /**
     * 设置投影面高
     */
    private void setProjectionHeightInCard(double projectionHeight, boolean enabled) {
        // 投影面高在投影卡片的第7个LinearLayout（从0开始计数）
        ViewGroup projectionCard = findViewById(R.id.card_touying);
        if (projectionCard instanceof ViewGroup) {
            ViewGroup cardContent = (ViewGroup) ((ViewGroup) projectionCard).getChildAt(0);
            if (cardContent.getChildCount() > 7) {
                View projHeightRow = cardContent.getChildAt(7);
                if (projHeightRow instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) projHeightRow;
                    if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                        EditText etProjectionHeight = (EditText) row.getChildAt(1);
                        if (projectionHeight != 0) { // 只有在有值时才设置
                            etProjectionHeight.setText(String.format("%.3f", projectionHeight));
                        }
                        etProjectionHeight.setEnabled(enabled);
                        if (!enabled) {
                            etProjectionHeight.setBackgroundColor(android.graphics.Color.TRANSPARENT); // 设置透明背景
                        } else {
                            etProjectionHeight.setBackgroundColor(0x00000000); // 恢复透明背景
                        }
                    }
                }
            }
        }
    }

    /**
     * 显示计算结果
     * 根据坐标系统理论显示详细的投影参数信息
     */
    private void showCalculationResults(OptimalProjectionParams params, double projectionHeight, ProjectionRegion region) {
        runOnUiThread(() -> {
            // 计算标准分度带信息用于显示
            String ellipsoid = params.ellipsoid;
            String projectionSystem;
            String zoneInfo = "";
            int zone = 0;

            if ("WGS84".equals(ellipsoid)) {
                projectionSystem = "UTM投影 (通用横墨卡托) - 自动选择";
                ZoneInfo utmZone = calculateUTMZone(region.avgLon, region.avgLat);
                zoneInfo = String.format("UTM Zone %d%s", utmZone.zone, region.avgLat >= 0 ? "N" : "S");
                zone = utmZone.zone;
            } else {
                projectionSystem = "高斯-克吕格3度带投影 - 自动选择";
                // 使用 CentralMeridianCalculation 计算带号
                CentralMeridianCalculation.BaseResult result = 
                    CentralMeridianCalculation.calculateForGaussKruger3Degree(region.avgLon, region.avgLat);
                zone = result.getZoneNumber();
                zoneInfo = String.format("3度带 (标准中央子午线: %.0f°)", result.centralMeridianDegrees);
            }

            // 计算经度跨度和EPSG代码
            double lonSpan = region.maxLon - region.minLon;
            String epsgCode = calculateEPSGCode(ellipsoid, zone);

            String message = String.format(
                    "投影坐标系参数计算完成\n\n" +
                            "━━━ 坐标系统信息 ━━━\n" +
                            "参考椭球: %s\n" +
                            "投影方式: %s\n" +
                            "分度带: %s\n" +
                            "EPSG代码: %s\n\n" +
                            "━━━ 投影区域 ━━━\n" +
                            "经度范围: %.6f° ~ %.6f° (跨度: %.3f°)\n" +
                            "纬度范围: %.6f° ~ %.6f°\n" +
                            "区域中心: %.6f°E, %.6f°N\n\n" +
                            "━━━ 投影参数 ━━━\n" +
                            "中央子午线: %.6f°\n" +
                            "原点纬度: 0.000000°\n" +
                            "投影比例尺 k₀: %.6f\n" +
                            "东向加常数: 500000 m\n" +
                            "北向加常数: 0 m\n" +
                            "投影面高: %.3f m\n\n" +
                            "━━━ 精度评估 ━━━\n" +
                            "最大残差: %.1f mm\n" +
                            "RMS残差: %.1f mm\n" +
                            "标准k₀: %.6f\n" +
                            "优化k₀: %.6f\n" +
                            "精度等级: %s",
                    ellipsoid,
                    projectionSystem,
                    zoneInfo,
                    epsgCode,
                    region.minLon, region.maxLon, lonSpan,
                    region.minLat, region.maxLat,
                    region.avgLon, region.avgLat,
                    params.centralMeridian,
                    params.optimalK0,
                    projectionHeight,
                    params.maxResidual * 1000, // 转换为毫米
                    params.rmsResidual * 1000,  // 转换为毫米
                    params.standardK0,
                    params.optimalK0,
                    params.maxResidual <= 0.01 ? "高精度 (≤1cm)" :
                            params.maxResidual <= 0.05 ? "中等精度 (≤5cm)" : "低精度 (>5cm)"
            );

            new AlertDialog.Builder(this)
                    .setTitle("投影坐标系参数")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show();
        });
    }

    /**
     * 坐标系统验证功能
     * 验证控制点坐标是否符合所选椭球的适用范围
     */
    private boolean validateCoordinateSystem(List<ControlPoint> points, String ellipsoid) {
        for (ControlPoint point : points) {
            // 基本地理坐标范围检查
            if (point.lat < -90 || point.lat > 90) {
                return false;
            }
            if (point.lon < -180 || point.lon > 180) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查坐标是否在椭球的推荐使用范围内
     */
    private boolean isCoordinateInEllipsoidRange(double lat, double lon, String ellipsoid) {
        switch (ellipsoid) {
            case "CGCS2000":
            case "北京54":
            case "西安80":
                // 中国境内及周边地区：北纬3°-54°，东经73°-136°
                return lat >= 3 && lat <= 54 && lon >= 73 && lon <= 136;
            case "WGS84":
                // 全球适用
                return true;
            default:
                return true;
        }
    }

    /**
     * 计算坐标系统的EPSG代码（统一使用3度带）
     */
    private String calculateEPSGCode(String ellipsoid, int zone) {
        switch (ellipsoid) {
            case "WGS84":
                // UTM投影
                return String.valueOf(32600 + zone + (zone > 60 ? 100 : 0)); // 北半球32601-32660，南半球32701-32760
            case "CGCS2000":
                // CGCS2000 3度带：4513-4533 (对应75°E-135°E的3度带)
                return String.valueOf(4512 + zone);
            case "北京54":
                // 北京54 3度带：2422-2462
                return String.valueOf(2421 + zone);
            case "西安80":
                // 西安80 3度带：2349-2384
                return String.valueOf(2348 + zone);
            default:
                return "未知";
        }
    }

    /**
     * 获取当前工程的控制点数据
     * 从数据库中获取已知点数据，转换为控制点格式
     */
    private List<ControlPoint> getControlPointsForCurrentProject() {
        List<ControlPoint> points = new ArrayList<>();

        if (currentCsId <= 0) {
            return points;
        }

        try {
            // 从数据库获取当前坐标系的已知点
            List<KnownPoint> knownPoints = kpRepo.getByCsSync(currentCsId);

            if (knownPoints == null || knownPoints.isEmpty()) {
                return points;
            }

            // 转换已知点为控制点格式
            for (KnownPoint kp : knownPoints) {
                // 检查是否有完整的坐标数据
                if (kp.getMeasuredB() != null && kp.getMeasuredL() != null &&
                        kp.getMeasuredH() != null) {

                    // 获取高程数据
                    double geodeticHeight = kp.getMeasuredH(); // 大地高
                    double orthometricHeight = (kp.getZ() != null) ? kp.getZ() : geodeticHeight; // 正常高

                    // 创建控制点
                    if (kp.getX() != null && kp.getY() != null) {
                        // 包含已知平面坐标的控制点（用于验证和优化）
                        String coordinateSystem = getCurrentCoordinateSystemName();
                        ControlPoint cp = new ControlPoint(
                                kp.getName(),                    // 点名
                                null,                            // 点号 (KnownPoint中无此字段)
                                null,                            // 等级 (KnownPoint中无此字段)
                                kp.getMeasuredB(),               // 纬度
                                kp.getMeasuredL(),               // 经度
                                kp.getX(),                       // 已知X坐标
                                kp.getY(),                       // 已知Y坐标
                                coordinateSystem,                // 坐标系名称
                                geodeticHeight,                  // 大地高
                                orthometricHeight                // 正常高
                        );
                        points.add(cp);

                    } else {
                        // 只有地理坐标的控制点（用于从零建立投影坐标系）
                        ControlPoint cp = new ControlPoint(
                                kp.getName(),                    // 点名
                                kp.getMeasuredB(),               // 纬度
                                kp.getMeasuredL(),               // 经度
                                geodeticHeight,                  // 大地高
                                orthometricHeight                // 正常高
                        );
                        points.add(cp);
                    }
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return points;
    }
    /**
     * 获取当前坐标系名称
     */
    private String getCurrentCoordinateSystemName() {
        try {
            CoordinateSystem cs = csRepo.getById(currentCsId);
            if (cs != null) {
                return cs.getName();
            }
        } catch (Exception e) {
        }
        return "未知坐标系";
    }

    /**
     * 获取当前选择的椭球名称
     */
    private String getSelectedEllipsoidName() {
        String[] ellipsoidTypes = {"CGCS2000", "WGS84", "北京54", "西安80"};
        if (selectedEllipsoidIndex >= 0 && selectedEllipsoidIndex < ellipsoidTypes.length) {
            return ellipsoidTypes[selectedEllipsoidIndex];
        }
        return "CGCS2000"; // 默认
    }

    /**
     * 更新高程拟合显示
     */
    private void updateHeightFitDisplay() {
        // 检查基准转换和高程拟合的组合是否有效
        if (selectedProjectionIndex == 0 && selectedHeightFitIndex == 0) {
            // 基准转换选择无参数时，高程拟合不能选择无参数
            selectedHeightFitIndex = 4; // 自动改为加权平均
            Toast.makeText(this, "基准转换选择无参数时，高程拟合不能选择无参数，已自动设置为加权平均", Toast.LENGTH_SHORT).show();
        }

        TextView tvHeight = findViewById(R.id.tv_height_fit);
        if (tvHeight != null) {
            String[] heightTypes = {"无参数", "垂直平差", "平面拟合", "曲面拟合", "加权平均"};
            // 索引直接对应：0=无参数, 1=垂直平差, 2=平面拟合, 3=曲面拟合, 4=加权平均
            if (selectedHeightFitIndex >= 0 && selectedHeightFitIndex < heightTypes.length) {
                tvHeight.setText(heightTypes[selectedHeightFitIndex]);
            }
        }
        // 同时更新面板可见性
        updateHeightPanelVisibility();
    }

    /**
     * 重置所有输入字段到默认状态
     */
    private void resetAllFields() {
        // 重置坐标系名称
        EditText tvCoordName = findViewById(R.id.tv_coord_name);
        if (tvCoordName != null) {
            tvCoordName.setText("Unnamed");
            // 在新建模式下，重新启用坐标系名称的输入功能
            tvCoordName.setFocusable(true);
            tvCoordName.setFocusableInTouchMode(true);
            tvCoordName.setClickable(true);
            // 设置提示文本
            tvCoordName.setHint("请输入坐标系名称");
        }

        // 重置椭球参数
        if (etSemiMajor != null) {
            etSemiMajor.setText("");
        }
        if (etInvF != null) {
            etInvF.setText("");
        }
        if (etCentralMeridian != null) {
            etCentralMeridian.setText("");
            // 重置手动输入标记
            userManuallyEnteredCentralMeridian = false;
        }

        // 重置投影参数
        resetProjectionFields();

        // 重置基准转换参数
        resetDatumFields();

        // 重置平面校正参数
        resetPlaneFields();

        // 重置高程拟合参数
        resetHeightFields();

        // 重置选择状态
        selectedEllipsoidIndex = 0;
        selectedProjectionIndex = 0;
        selectedPlaneIndex = 0;
        selectedHeightFitIndex = 4;

        // 更新界面显示
        updateDatumPanelVisibility();
        updatePlanePanelVisibility();
        updateHeightPanelVisibility();
        updateProjectionRows();
        updateEllipsoidParams();

        // 重置最后选择的状态
        lastSel = null;
        if (leftMenu != null && leftMenu.getChildCount() > 0) {
            View first = leftMenu.getChildAt(0);
            if (first != null) {
                first.setBackgroundResource(R.drawable.bg_menu_selected_new);
                lastSel = first;
            }
        }
    }

    /**
     * 重置投影参数字段
     */
    private void resetProjectionFields() {
        // 重置原点纬度
        View rowOriginLat = findViewById(R.id.row_origin_lat);
        if (rowOriginLat != null) {
            resetRowFields(rowOriginLat);
        }

        // 重置比例因子
        View rowScale = findViewById(R.id.row_scale);
        if (rowScale != null) {
            resetRowFields(rowScale);
        }

        // 重置东偏移
        View rowAddE = findViewById(R.id.row_add_east);
        if (rowAddE != null) {
            resetRowFields(rowAddE);
        }

        // 重置北偏移
        View rowAddN = findViewById(R.id.row_add_north);
        if (rowAddN != null) {
            resetRowFields(rowAddN);
        }

        // 重置带号
        View rowZone = findViewById(R.id.row_zone);
        if (rowZone != null) {
            resetRowFields(rowZone);
        }

        // 重置半球选择
        RadioButton rbNorth = findViewById(R.id.rb_north);
        if (rbNorth != null) {
            rbNorth.setChecked(true);
        }
    }

    /**
     * 重置基准转换参数字段
     */
    private void resetDatumFields() {
        // 重置七参数面板
        if (panelSevenParam != null) {
            resetPanelFields(panelSevenParam);
        }
    }

    /**
     * 重置平面校正参数字段
     */
    private void resetPlaneFields() {
        // 重置三参数面板
        if (panelThreeParam != null) {
            resetPanelFields(panelThreeParam);
        }

        // 重置四参数面板
        if (panelFourParam != null) {
            resetPanelFields(panelFourParam);
        }

        // 重置平面平差面板
        if (panelPlaneAdjustment != null) {
            resetPanelFields(panelPlaneAdjustment);
        }
    }

    /**
     * 重置高程拟合参数字段
     */
    private void resetHeightFields() {
        // 重置垂直平差面板
        if (phVertical != null) {
            resetPanelFields(phVertical);
        }

        // 重置平面拟合面板
        if (phPlane != null) {
            resetPanelFields(phPlane);
        }

        // 重置曲面拟合面板
        if (phSurface != null) {
            resetPanelFields(phSurface);
        }

        // 重置加权平均面板
        if (phWeighted != null) {
            resetPanelFields(phWeighted);
        }
    }

    /**
     * 重置行中的字段
     */
    private void resetRowFields(View row) {
        if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof EditText) {
                    ((EditText) child).setText("");
                }
            }
        }
    }

    /**
     * 重置面板中的字段
     */
    private void resetPanelFields(View panel) {
        if (panel instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) panel;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof EditText) {
                    ((EditText) child).setText("");
                } else if (child instanceof ViewGroup) {
                    resetPanelFields(child);
                }
            }
        }
    }

    /**
     * 清空已知点数据
     */
    private void clearKnownPoints() {
        // 清空已知点数据
        knownPointValues.clear();
        measuredPointValues.clear();
        accuracyValues.clear();

        // 重置最后选择的已知点
        lastKnownSel = null;

        // 清空已知点容器
        if (containerKnownPoints != null) {
            containerKnownPoints.removeAllViews();
        }

        // 重置已知点计数
        knownPointCount = 2;

        // 添加默认的已知点
        knownPointValues.put("已知点1", new String[]{"", "", ""});
        knownPointValues.put("已知点2", new String[]{"", "", ""});

        // 重新创建已知点界面
        if (pointCorrectionContent != null) {
            createKnownPointsInterface();
        }
    }
    /**
     * 创建已知点界面
     */
    private void createKnownPointsInterface() {
        if (containerKnownPoints == null || pointCorrectionContent == null) {
            return;
        }

        // 清空容器
        containerKnownPoints.removeAllViews();

        // 找到前两个静态视图
        TextView tv1 = pointCorrectionContent.findViewById(R.id.tv_known_point_1);
        TextView tv2 = pointCorrectionContent.findViewById(R.id.tv_known_point_2);

        if (tv1 != null && tv2 != null) {
            // 设置前两个点的文本和点击事件
            tv1.setText("已知点1");
            tv1.setOnClickListener(v -> selectKnownPoint(tv1));
            tv1.setBackgroundResource(R.drawable.bg_known_point_unselected);
            tv1.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_point_unselected, 0, 0, 0);

            tv2.setText("已知点2");
            tv2.setOnClickListener(v -> selectKnownPoint(tv2));
            tv2.setBackgroundResource(R.drawable.bg_known_point_unselected);
            tv2.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_point_unselected, 0, 0, 0);

            // 将静态视图添加到容器
            containerKnownPoints.addView(tv1);
            containerKnownPoints.addView(tv2);

            // 默认选中第一个点
            if (tv1 != null) {
                tv1.post(() -> selectKnownPoint(tv1));
            }
        }

        // 清空输入字段
        if (etKnownN != null) etKnownN.setText("");
        if (etKnownE != null) etKnownE.setText("");
        if (etKnownH != null) etKnownH.setText("");

        EditText etMeasuredB = pointCorrectionContent.findViewById(R.id.et_measured_b);
        EditText etMeasuredL = pointCorrectionContent.findViewById(R.id.et_measured_l);
        EditText etMeasuredH = pointCorrectionContent.findViewById(R.id.et_measured_h);
        if (etMeasuredB != null) etMeasuredB.setText("");
        if (etMeasuredL != null) etMeasuredL.setText("");
        if (etMeasuredH != null) etMeasuredH.setText("");
    }

}