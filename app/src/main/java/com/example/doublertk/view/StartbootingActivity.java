package com.example.doublertk.view;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.data.Job;
import com.example.doublertk.data.JobRepository;
import com.example.doublertk.data.DockingLog;
import com.example.doublertk.data.DockingLogRepository;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class StartbootingActivity extends BaseActivity {

    private ShipView shipView;
    private Handler moveHandler = new Handler(Looper.getMainLooper());
    private Runnable moveRunnable;
    private boolean isMoving = false;

    private TextView tvJobName;
    private TextView tvLegName;
    private TextView tvTargetPoint;

    private TextView tvGuidanceState;

    private TextView tvRtkStatus;
    private TextView tvRtkLatency;
    private TextView tvPdop;
    private TextView tvBaseline;
    private TextView tvDiffSource;

    private TextView tvSpeed;

    private TextView tvCrossTrack;
    private TextView tvAlongTrack;
    private TextView tvHeadingError;

    private TextView tvAlarmSummary;

    private JobRepository jobRepository;
    private DockingLogRepository dockingLogRepository;
    private long selectedJobId = -1;
    private String selectedJobName;

    private Handler simHandler;
    private Runnable simUpdateRunnable;
    private final SimGuidanceStatus simStatus = new SimGuidanceStatus();
    private GuidanceState guidanceState = GuidanceState.IDLE;

    private boolean dockSuccessNotified = false;

    // 停靠日志相关
    private DockingLog currentDockingLog;
    private long guidanceStartTime = 0;
    private List<ErrorSample> errorSamples = new ArrayList<>();

    // 误差采样数据
    private static class ErrorSample {
        double bowError;
        double sternError;
        double headingError;
        boolean rtkFixed;
        double pdop;
        double baseline;
        long timestamp;
    }

    private static final String PREFS_GUIDANCE = "guidance_prefs";
    private static final String KEY_SELECTED_JOB_ID = "selected_job_id";
    private static final String KEY_SELECTED_JOB_NAME = "selected_job_name";

    private enum GuidanceState {
        IDLE,
        READY,
        GUIDING,
        PAUSED,
        ERROR
    }

    private static class SimGuidanceStatus {
        String rtkStatusText = "RTK：-";
        String rtkLatencyText = "延迟：-";
        String pdopText = "PDOP：-";
        String baselineText = "基线：-";
        String diffSourceText = "数据源：模拟";
        String speedText = "速度：-";
        String crossTrackText = "横向偏差：-";
        String alongTrackText = "到目标点距离：-";
        String headingErrorText = "航向偏差：-";
        String alarmText = "报警：无";

        boolean rtkFixed = false;
        double distanceToTargetM = 120.0;
        double crossTrackM = 1.2;
        double headingErrorDeg = 5.0;
        double speedMs = 0.8;
        double pdop = 1.5;
        double baselineM = 1.2;
        long latencyMs = 600;
        int tick = 0;

        // ShipView driving data (local/world coordinates in pixels-like units for demo)
        float shipX = 0f;
        float shipY = 0f;
        float shipHeadingDeg = 0f;
        float targetX = 0f;
        float targetY = -280f;
        float targetHeadingDeg = 0f;

        float targetNorthX = 0f;
        float targetNorthY = 0f;
        float targetSouthX = 0f;
        float targetSouthY = 0f;

        boolean hasDockTarget = false;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_start_booting;
    }

    @Override
    protected void initView() {
        setTopBarTitle("开始引导");
        jobRepository = new JobRepository(this);
        dockingLogRepository = new DockingLogRepository(this);
        selectedJobId = getSharedPreferences(PREFS_GUIDANCE, MODE_PRIVATE).getLong(KEY_SELECTED_JOB_ID, -1);
        selectedJobName = getSharedPreferences(PREFS_GUIDANCE, MODE_PRIVATE).getString(KEY_SELECTED_JOB_NAME, null);
        bindGuidancePanelViews();
        restoreSelectedJobNameIfNeeded();
        updateGuidanceStateByPrerequisites();
        updateUiForState();
        setupShipControls();
        setupGuidanceButtons();
    }

    private void bindGuidancePanelViews() {
        tvJobName = findViewById(R.id.tv_job_name);
        tvLegName = findViewById(R.id.tv_leg_name);
        tvTargetPoint = findViewById(R.id.tv_target_point);

        tvGuidanceState = findViewById(R.id.tv_guidance_state);

        TextView tvSelectJob = findViewById(R.id.tv_select_job);
        if (tvSelectJob != null) {
            tvSelectJob.setOnClickListener(v -> showSelectJobDialog());
        }

        tvRtkStatus = findViewById(R.id.tv_rtk_status);
        tvRtkLatency = findViewById(R.id.tv_rtk_latency);
        tvPdop = findViewById(R.id.tv_pdop);
        tvBaseline = findViewById(R.id.tv_baseline);
        tvDiffSource = findViewById(R.id.tv_diff_source);

        tvSpeed = findViewById(R.id.tv_speed);

        tvCrossTrack = findViewById(R.id.tv_cross_track);
        tvAlongTrack = findViewById(R.id.tv_along_track);
        tvHeadingError = findViewById(R.id.tv_heading_error);

        tvAlarmSummary = findViewById(R.id.tv_alarm_summary);
    }


    private void updateSelectedJobText() {
        if (tvJobName == null) return;

        if (selectedJobId > 0) {
            if (selectedJobName != null && !selectedJobName.isEmpty()) {
                tvJobName.setText("作业：" + selectedJobName);
            } else {
                tvJobName.setText("作业：ID=" + selectedJobId);
            }
        } else {
            tvJobName.setText("作业：未选择");
        }
    }

    private void restoreSelectedJobNameIfNeeded() {
        if (selectedJobId <= 0) return;
        if (selectedJobName != null && !selectedJobName.isEmpty()) {
            updateSelectedJobText();
            return;
        }

        new Thread(() -> {
            try {
                Future<Job> future = jobRepository.getJobById(selectedJobId);
                Job job = future.get();
                if (job != null && job.getName() != null && !job.getName().isEmpty()) {
                    selectedJobName = job.getName();
                    getSharedPreferences(PREFS_GUIDANCE, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_SELECTED_JOB_NAME, selectedJobName)
                            .apply();
                }
            } catch (Exception ignore) {
            }

            runOnUiThread(this::updateSelectedJobText);
        }).start();
    }

    private void showSelectJobDialog() {
        new Thread(() -> {
            List<Job> jobs = new ArrayList<>();
            try {
                Future<List<Job>> future = jobRepository.getAllJobs();
                List<Job> list = future.get();
                if (list != null) jobs.addAll(list);
            } catch (Exception ignore) {
            }

            runOnUiThread(() -> {
                if (jobs.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("选择作业")
                            .setMessage("暂无作业，请先到作业管理创建")
                            .setPositiveButton("确定", null)
                            .show();
                    return;
                }

                String[] names = new String[jobs.size()];
                for (int i = 0; i < jobs.size(); i++) {
                    Job j = jobs.get(i);
                    names[i] = j == null ? "" : (j.getName() == null ? "" : j.getName());
                }

                new AlertDialog.Builder(this)
                        .setTitle("选择作业")
                        .setItems(names, (dialog, which) -> {
                            if (which < 0 || which >= jobs.size()) return;
                            Job j = jobs.get(which);
                            if (j == null) return;
                            setSelectedJob(j.getId(), j.getName());
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }).start();
    }

    private void setSelectedJob(long jobId, String jobName) {
        selectedJobId = jobId;
        selectedJobName = jobName;
        getSharedPreferences(PREFS_GUIDANCE, MODE_PRIVATE)
                .edit()
                .putLong(KEY_SELECTED_JOB_ID, jobId)
                .putString(KEY_SELECTED_JOB_NAME, jobName)
                .apply();

        updateSelectedJobText();
        updateGuidanceStateByPrerequisites();
        updateUiForState();
    }

    private void updateGuidanceStateByPrerequisites() {
        // 目前只要求选择作业；航段/目标点后续接入后在这里补齐门槛。
        if (selectedJobId > 0) {
            if (guidanceState == GuidanceState.GUIDING || guidanceState == GuidanceState.PAUSED) return;
            guidanceState = GuidanceState.READY;
        } else {
            guidanceState = GuidanceState.IDLE;
        }
    }

    private void updateUiForState() {
        updateSelectedJobText();
        if (tvLegName != null) tvLegName.setText("航段：-" );
        if (tvTargetPoint != null) tvTargetPoint.setText("目标点：-" );

        if (tvGuidanceState != null) {
            tvGuidanceState.setText(buildGuidanceStateText());
        }

        // 不在引导态时也显示模拟的“基础定位状态”，便于联调 UI。
        applySimStatusToUi(simStatus);
    }

    private String buildGuidanceStateText() {
        String base = "引导状态：" + toGuidanceStateText(guidanceState);
        if (shipView == null) return base;
        String hint = buildDockingHintInShipFrame();
        if (hint == null || hint.isEmpty()) return base;
        return base + "\n" + hint;
    }

    /**
     * 基于船体坐标系(前/后/左/右 + 转向)输出指引，不做自动驾驶。
     */
    private String buildDockingHintInShipFrame() {
        PointF shipCenter = shipView.getShipCenterWorld();
        if (shipCenter == null) return null;
        PointF[] path = shipView.getGuidancePathWorld();
        if (path == null || path.length < 3) return null;

        PointF p0 = path[0];
        PointF p1 = path[1];
        PointF p2 = path[2];

        float distToP1 = distance(shipCenter.x, shipCenter.y, p1.x, p1.y);
        float distToP2 = distance(shipCenter.x, shipCenter.y, p2.x, p2.y);

        float toTargetTh = 120f;
        boolean goDirectTarget = distToP2 <= toTargetTh || distToP2 <= distToP1;
        PointF target = goDirectTarget ? p2 : p1;

        float dx = target.x - shipCenter.x;
        float dy = target.y - shipCenter.y;

        // 船体坐标系：heading=0 朝屏幕上方，右为正
        float headingDeg = shipView.getShipHeadingDeg();
        float a = (float) Math.toRadians(headingDeg);
        float fwdX = (float) Math.sin(a);
        float fwdY = (float) -Math.cos(a);
        float rightX = (float) Math.cos(a);
        float rightY = (float) Math.sin(a);

        float forward = dx * fwdX + dy * fwdY;
        float right = dx * rightX + dy * rightY;

        // 航向建议：对准最终进靠段 (p1->p2)
        float segX = p2.x - p1.x;
        float segY = p2.y - p1.y;
        float targetHeading = (float) Math.toDegrees(Math.atan2(segX, -segY));
        float headingErr = normalizeAngleDeg(targetHeading - headingDeg);

        StringBuilder sb = new StringBuilder();
        if (goDirectTarget) {
            sb.append("目标：沿泊位方向进靠");
        } else {
            sb.append("目标：进入预对准点");
        }

        // 平移提示阈值
        float moveTh = 25f;
        if (Math.abs(right) > moveTh) {
            sb.append("\n");
            if (right > 0) sb.append("向右 "); else sb.append("向左 ");
            sb.append(String.format("%.0f", Math.abs(right))).append("px");
        }
        if (Math.abs(forward) > moveTh) {
            sb.append("\n");
            if (forward > 0) sb.append("向前 "); else sb.append("向后 ");
            sb.append(String.format("%.0f", Math.abs(forward))).append("px");
        }

        // 转向提示阈值
        float turnTh = 6f;
        if (Math.abs(headingErr) > turnTh) {
            sb.append("\n");
            if (headingErr > 0) sb.append("顺时针转 "); else sb.append("逆时针转 ");
            sb.append(String.format("%.0f", Math.abs(headingErr))).append("°");
        }

        if (Math.abs(right) <= moveTh && Math.abs(forward) <= moveTh && Math.abs(headingErr) <= turnTh) {
            sb.append("\n保持当前姿态");
        }

        return sb.toString();
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private String toGuidanceStateText(GuidanceState state) {
        switch (state) {
            case IDLE:
                return "未就绪";
            case READY:
                return "就绪";
            case GUIDING:
                return "引导中";
            case PAUSED:
                return "已暂停";
            case ERROR:
                return "异常";
        }
        return "-";
    }

    private void startSimulationIfNeeded() {
        if (simHandler == null) {
            simHandler = new Handler(Looper.getMainLooper());
        }
        if (simUpdateRunnable != null) return;

        simUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateSimulatedStatus();
                applySimStatusToUi(simStatus);
                if (guidanceState == GuidanceState.GUIDING) {
                    simHandler.postDelayed(this, 500);
                }
            }
        };
        simHandler.post(simUpdateRunnable);
    }

    private void stopSimulation() {
        if (simHandler != null && simUpdateRunnable != null) {
            simHandler.removeCallbacks(simUpdateRunnable);
        }
        simUpdateRunnable = null;
    }

    private void updateSimulatedStatus() {
        simStatus.tick++;

        // 模拟 RTK 状态：每隔一段时间在 FIX/FLOAT 间变化
        boolean fixed = (simStatus.tick % 12) >= 4;
        simStatus.rtkFixed = fixed;
        simStatus.latencyMs = fixed ? 350 : 900;
        simStatus.pdop = fixed ? 1.2 : 2.8;
        simStatus.baselineM = fixed ? 1.1 : 2.5;

        simStatus.rtkStatusText = fixed ? "RTK：FIX(模拟)" : "RTK：FLOAT(模拟)";
        simStatus.rtkLatencyText = "延迟：" + simStatus.latencyMs + "ms";
        simStatus.pdopText = String.format("PDOP：%.1f", simStatus.pdop);
        simStatus.baselineText = String.format("基线：%.1fm", simStatus.baselineM);
        simStatus.diffSourceText = "数据源：模拟";

        // 引导中更新偏差/距离显示（不再自动移动船舶，由手动方向按钮控制）
        if (guidanceState == GuidanceState.GUIDING) {
            // 计算当前船舶与目标的偏差（仅用于显示，不自动收敛）
            if (simStatus.hasDockTarget && shipView != null) {
                float dRtk = shipView.getRtkCenterOffset();
                float a = (float) Math.toRadians(simStatus.shipHeadingDeg);
                float bowDx = (float) (Math.sin(a) * dRtk);
                float bowDy = (float) (-Math.cos(a) * dRtk);
                float sternDx = -bowDx;
                float sternDy = -bowDy;

                float bowX = simStatus.shipX + bowDx;
                float bowY = simStatus.shipY + bowDy;
                float sternX = simStatus.shipX + sternDx;
                float sternY = simStatus.shipY + sternDy;

                float bowErr = (float) Math.sqrt(
                        (simStatus.targetNorthX - bowX) * (simStatus.targetNorthX - bowX)
                                + (simStatus.targetNorthY - bowY) * (simStatus.targetNorthY - bowY)
                );
                float sternErr = (float) Math.sqrt(
                        (simStatus.targetSouthX - sternX) * (simStatus.targetSouthX - sternX)
                                + (simStatus.targetSouthY - sternY) * (simStatus.targetSouthY - sternY)
                );

                // 更新显示数值（仅显示，不控制船舶移动）
                simStatus.distanceToTargetM = (bowErr + sternErr) * 0.5;
                simStatus.crossTrackM = bowErr;
                simStatus.headingErrorDeg = Math.abs(normalizeAngleDeg(simStatus.targetHeadingDeg - simStatus.shipHeadingDeg));

                // 采样误差数据用于日志统计
                if (currentDockingLog != null && simStatus.tick % 4 == 0) {
                    ErrorSample sample = new ErrorSample();
                    sample.bowError = bowErr;
                    sample.sternError = sternErr;
                    sample.headingError = simStatus.headingErrorDeg;
                    sample.rtkFixed = fixed;
                    sample.pdop = simStatus.pdop;
                    sample.baseline = simStatus.baselineM;
                    sample.timestamp = System.currentTimeMillis();
                    errorSamples.add(sample);
                }

                // 检测停靠成功条件（仅提示，不自动暂停）
                if (bowErr <= 12f && sternErr <= 12f && simStatus.headingErrorDeg <= 8f) {
                    simStatus.alarmText = "报警：已停靠成功";
                    notifyDockSuccessOnce();
                } else if (shipView.isBoundaryAlarmActive()) {
                    float d = shipView.getMinDistanceToBoundaryMeters();
                    simStatus.alarmText = String.format("报警：靠近边界(%.1fm)", d);
                } else if (!fixed) {
                    simStatus.alarmText = "报警：差分不稳定(模拟)";
                } else {
                    simStatus.alarmText = "报警：无";
                }
            } else {
                if (shipView != null && shipView.isBoundaryAlarmActive()) {
                    float d = shipView.getMinDistanceToBoundaryMeters();
                    simStatus.alarmText = String.format("报警：靠近边界(%.1fm)", d);
                } else if (!fixed) {
                    simStatus.alarmText = "报警：差分不稳定(模拟)";
                } else {
                    simStatus.alarmText = "报警：无";
                }
            }
            simStatus.speedMs = 0.0;
        } else {
            simStatus.speedMs = 0.0;
            simStatus.alarmText = "报警：无";
        }

        simStatus.speedText = String.format("速度：%.1fm/s", simStatus.speedMs);
        simStatus.crossTrackText = String.format("横向偏差：%.2fm", simStatus.crossTrackM);
        simStatus.alongTrackText = String.format("到目标点距离：%.1fm", simStatus.distanceToTargetM);
        simStatus.headingErrorText = String.format("航向偏差：%.1f°", simStatus.headingErrorDeg);
    }

    private float normalizeAngleDeg(float deg) {
        float a = deg % 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private void applySimStatusToUi(SimGuidanceStatus s) {
        if (tvRtkStatus != null) tvRtkStatus.setText(s.rtkStatusText);
        if (tvRtkLatency != null) tvRtkLatency.setText(s.rtkLatencyText);
        if (tvPdop != null) tvPdop.setText(s.pdopText);
        if (tvBaseline != null) tvBaseline.setText(s.baselineText);
        if (tvDiffSource != null) tvDiffSource.setText(s.diffSourceText);

        if (tvSpeed != null) tvSpeed.setText(s.speedText);
        if (tvCrossTrack != null) tvCrossTrack.setText(s.crossTrackText);
        if (tvAlongTrack != null) tvAlongTrack.setText(s.alongTrackText);
        if (tvHeadingError != null) tvHeadingError.setText(s.headingErrorText);
        if (tvAlarmSummary != null) tvAlarmSummary.setText(s.alarmText);

        if (shipView != null) {
            shipView.setPose(s.shipX, s.shipY, s.shipHeadingDeg);
            if (guidanceState == GuidanceState.GUIDING) {
                shipView.appendTrackPoint(s.shipX, s.shipY);
            }
        }

        if (tvGuidanceState != null) {
            tvGuidanceState.setText(buildGuidanceStateText());
        }
    }

    // 摇杆当前偏移量
    private float joystickX = 0f;
    private float joystickY = 0f;

    private static final float JOYSTICK_TURN_DEG_PER_TICK = 2.5f;
    private static final float JOYSTICK_SPEED_UNITS_PER_TICK = 4f;

    /**
     * 设置船舶控制
     */
    private void setupShipControls() {
        // 获取船舶视图
        shipView = findViewById(R.id.ship_view);

        // 设置摇杆控制
        JoystickView joystickView = findViewById(R.id.joystick_view);
        if (joystickView != null && shipView != null) {
            joystickView.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
                @Override
                public void onMove(float xPercent, float yPercent) {
                    joystickX = xPercent;
                    joystickY = yPercent;
                    if (!isMoving) {
                        isMoving = true;
                        startJoystickMovement();
                    }
                }

                @Override
                public void onRelease() {
                    joystickX = 0f;
                    joystickY = 0f;
                    isMoving = false;
                    if (moveRunnable != null) {
                        moveHandler.removeCallbacks(moveRunnable);
                        moveRunnable = null;
                    }
                }
            });
        }

        // 船身居中按钮
        Button btnResetPosition = findViewById(R.id.btn_reset_position);
        if (btnResetPosition != null && shipView != null) {
            btnResetPosition.setOnClickListener(v -> {
                dockSuccessNotified = false;
                shipView.resetPosition();
            });
        }
    }

    private void notifyDockSuccessOnce() {
        if (dockSuccessNotified) return;
        dockSuccessNotified = true;

        // 停靠成功，保存日志
        if (currentDockingLog != null) {
            saveDockingLogAsSuccess();
        }

        // 显示成功对话框
        showDockingSuccessDialog();
    }

    /**
     * 设置引导控制按钮
     */
    private void setupGuidanceButtons() {
        Button btnStartGuidance = findViewById(R.id.btn_start_guidance);
        Button btnStopGuidance = findViewById(R.id.btn_stop_guidance);

        if (btnStartGuidance != null) {
            btnStartGuidance.setOnClickListener(v -> startGuidance());
        }

        if (btnStopGuidance != null) {
            btnStopGuidance.setOnClickListener(v -> stopGuidance());
        }
    }

    /**
     * 开始引导
     */
    private void startGuidance() {
        if (selectedJobId <= 0) {
            Toast.makeText(this, "请先选择作业", Toast.LENGTH_SHORT).show();
            return;
        }

        if (guidanceState == GuidanceState.GUIDING) {
            Toast.makeText(this, "引导已在进行中", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建停靠日志
        createDockingLog();

        // 更新状态
        guidanceState = GuidanceState.GUIDING;
        guidanceStartTime = System.currentTimeMillis();
        dockSuccessNotified = false;
        errorSamples.clear();

        // 启动模拟更新
        startSimulationIfNeeded();

        // 更新UI
        updateUiForState();

        Toast.makeText(this, "开始引导", Toast.LENGTH_SHORT).show();
    }

    /**
     * 停止引导
     */
    private void stopGuidance() {
        if (guidanceState != GuidanceState.GUIDING) {
            return;
        }

        // 取消停靠日志
        if (currentDockingLog != null) {
            cancelDockingLog();
        }

        // 更新状态
        guidanceState = GuidanceState.READY;
        stopSimulation();

        // 更新UI
        updateUiForState();

        Toast.makeText(this, "已停止引导", Toast.LENGTH_SHORT).show();
    }

    /**
     * 创建停靠日志
     */
    private void createDockingLog() {
        currentDockingLog = dockingLogRepository.createNewLog(selectedJobId, selectedJobName);

        // 设置目标位置信息（从ShipView获取）
        if (shipView != null && simStatus.hasDockTarget) {
            currentDockingLog.setTargetNorthX(simStatus.targetNorthX);
            currentDockingLog.setTargetNorthY(simStatus.targetNorthY);
            currentDockingLog.setTargetSouthX(simStatus.targetSouthX);
            currentDockingLog.setTargetSouthY(simStatus.targetSouthY);
            currentDockingLog.setTargetHeading(simStatus.targetHeadingDeg);
        }

        // 保存到数据库
        new Thread(() -> {
            try {
                Future<Long> future = dockingLogRepository.insert(currentDockingLog);
                long id = future.get();
                currentDockingLog.setId(id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 保存停靠日志（成功）
     */
    private void saveDockingLogAsSuccess() {
        if (currentDockingLog == null) return;

        // 设置最终位置信息
        currentDockingLog.setFinalBowX(simStatus.shipX);
        currentDockingLog.setFinalBowY(simStatus.shipY);
        currentDockingLog.setFinalSternX(simStatus.shipX);
        currentDockingLog.setFinalSternY(simStatus.shipY);
        currentDockingLog.setFinalHeading(simStatus.shipHeadingDeg);

        // 计算统计数据
        calculateLogStatistics();

        // 设置成功状态
        currentDockingLog.setEndTime(System.currentTimeMillis());
        currentDockingLog.setSuccess(true);
        currentDockingLog.setStatus("SUCCESS");

        // 更新到数据库
        new Thread(() -> {
            try {
                dockingLogRepository.update(currentDockingLog);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        currentDockingLog = null;
    }

    /**
     * 取消停靠日志
     */
    private void cancelDockingLog() {
        if (currentDockingLog == null) return;

        dockingLogRepository.cancelLog(currentDockingLog);
        currentDockingLog = null;
    }

    /**
     * 计算日志统计数据
     */
    private void calculateLogStatistics() {
        if (currentDockingLog == null || errorSamples.isEmpty()) return;

        // 计算最终误差
        if (!errorSamples.isEmpty()) {
            ErrorSample lastSample = errorSamples.get(errorSamples.size() - 1);
            currentDockingLog.setFinalBowError(lastSample.bowError);
            currentDockingLog.setFinalSternError(lastSample.sternError);
            currentDockingLog.setFinalHeadingError(lastSample.headingError);
        }

        // 计算最大误差和平均误差
        double maxBow = 0, maxStern = 0, maxHeading = 0;
        double sumBow = 0, sumStern = 0, sumHeading = 0;
        int fixCount = 0;
        double sumPdop = 0, sumBaseline = 0;

        for (ErrorSample sample : errorSamples) {
            maxBow = Math.max(maxBow, sample.bowError);
            maxStern = Math.max(maxStern, sample.sternError);
            maxHeading = Math.max(maxHeading, sample.headingError);

            sumBow += sample.bowError;
            sumStern += sample.sternError;
            sumHeading += sample.headingError;

            if (sample.rtkFixed) fixCount++;
            sumPdop += sample.pdop;
            sumBaseline += sample.baseline;
        }

        int count = errorSamples.size();
        currentDockingLog.setMaxBowError(maxBow);
        currentDockingLog.setMaxSternError(maxStern);
        currentDockingLog.setMaxHeadingError(maxHeading);

        currentDockingLog.setAvgBowError(sumBow / count);
        currentDockingLog.setAvgSternError(sumStern / count);
        currentDockingLog.setAvgHeadingError(sumHeading / count);

        currentDockingLog.setRtkFixRate((double) fixCount / count);
        currentDockingLog.setAvgPdop(sumPdop / count);
        currentDockingLog.setAvgBaseline(sumBaseline / count);
    }

    /**
     * 显示停靠成功对话框
     */
    private void showDockingSuccessDialog() {
        if (currentDockingLog == null) {
            Toast.makeText(this, "停靠成功！", Toast.LENGTH_LONG).show();
            return;
        }

        long duration = System.currentTimeMillis() - guidanceStartTime;
        String durationText = formatDuration(duration);

        String message = String.format(
            "停靠成功！\n\n" +
            "用时：%s\n" +
            "船头误差：%.2fm\n" +
            "船尾误差：%.2fm\n" +
            "航向误差：%.1f°\n" +
            "精度评级：%s",
            durationText,
            currentDockingLog.getFinalBowError(),
            currentDockingLog.getFinalSternError(),
            currentDockingLog.getFinalHeadingError(),
            currentDockingLog.getAccuracyRating()
        );

        new AlertDialog.Builder(this)
            .setTitle("停靠成功")
            .setMessage(message)
            .setPositiveButton("继续", (dialog, which) -> {
                // 重置状态，准备下一次停靠
                guidanceState = GuidanceState.READY;
                dockSuccessNotified = false;
                updateUiForState();
            })
            .setNegativeButton("查看日志", (dialog, which) -> {
                // 跳转到日志页面
                // TODO: 实现跳转
                Toast.makeText(this, "日志功能开发中", Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
    }

    /**
     * 格式化持续时间
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "分" + seconds + "秒";
        } else {
            return seconds + "秒";
        }
    }

    /**
     * 启动摇杆持续移动
     */
    private void startJoystickMovement() {
        moveRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMoving && shipView != null) {
                    // 根据摇杆偏移量移动船舶，偏移量越大移动越快
                    float threshold = 0.2f;
                    // 使用向量长度判断是否超过阈值，避免斜向移动时只触发单方向
                    float magnitude = (float) Math.sqrt(joystickX * joystickX + joystickY * joystickY);
                    if (magnitude > threshold) {
                        // X 控制航向，Y 控制沿航向前进/后退
                        // 速度与摇杆偏移量成正比（类似游戏摇杆的渐进式控制）
                        float turnRate = joystickX * Math.abs(joystickX) * JOYSTICK_TURN_DEG_PER_TICK;
                        simStatus.shipHeadingDeg = wrapDeg0To360(simStatus.shipHeadingDeg + turnRate);

                        float thrust = -joystickY * Math.abs(joystickY);  // 平方关系，小幅度更精细
                        float a = (float) Math.toRadians(simStatus.shipHeadingDeg);
                        float dx = (float) (Math.sin(a) * thrust * JOYSTICK_SPEED_UNITS_PER_TICK);
                        float dy = (float) (-Math.cos(a) * thrust * JOYSTICK_SPEED_UNITS_PER_TICK);
                        simStatus.shipX += dx;
                        simStatus.shipY += dy;

                        applySimStatusToUi(simStatus);
                    }
                    if (tvGuidanceState != null) {
                        tvGuidanceState.setText(buildGuidanceStateText());
                    }
                    moveHandler.postDelayed(this, 50);
                }
            }
        };
        moveHandler.post(moveRunnable);
    }

    private float wrapDeg0To360(float deg) {
        float a = deg % 360f;
        if (a < 0f) a += 360f;
        return a;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理Handler，避免内存泄漏
        if (moveHandler != null) {
            moveHandler.removeCallbacksAndMessages(null);
        }

        stopSimulation();
        if (simHandler != null) {
            simHandler.removeCallbacksAndMessages(null);
        }
    }
}

