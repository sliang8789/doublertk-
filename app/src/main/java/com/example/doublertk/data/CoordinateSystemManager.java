package com.example.doublertk.data;

// TODO: 以下导入的类需要实现或替换
// import static com.example.doublertk.calculate.WGS84ToUTM.lonLatToUTM;

import android.content.Context;
import android.util.Log;

import com.example.doublertk.calculate.BJ54GaussKruger;
import com.example.doublertk.calculate.FourParameterSolver;
import com.example.doublertk.calculate.GaussKrugerCGCS2000;
import com.example.doublertk.calculate.PlaneAdjustmentTransform;
import com.example.doublertk.calculate.SevenParameterTransform;
import com.example.doublertk.calculate.ThreeParameterTransform;
import com.example.doublertk.calculate.WGS84ToUTM;
import com.example.doublertk.calculate.Xian80GaussKruger;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// TODO: 以下导入的类需要实现或替换
// import com.example.doublertk.calculate.GaussKrugerCGCS2000;
// import com.example.doublertk.calculate.BJ54GaussKruger;
// import com.example.doublertk.calculate.Xian80GaussKruger;
// import com.example.doublertk.calculate.WGS84ToUTM;

/**
 * 坐标系管理器
 * 使用现有的Room数据库处理不同坐标系类型的数据管理
 */
public class CoordinateSystemManager {

    // 椭球类型常量
    public static final int ELLIPSOID_CGCS2000 = 0;
    public static final int ELLIPSOID_WGS84 = 1;
    public static final int ELLIPSOID_BEIJING54 = 2;
    public static final int ELLIPSOID_XIAN80 = 3;

    // 投影类型常量
    public static final int PROJECTION_GAUSS = 0;  // 高斯投影（统一使用3度带）
    public static final int PROJECTION_UTM = 1;

    private AppDatabase database;
    private CoordinateSystemDao coordinateSystemDao;
    private KnownPointDao knownPointDao;
    private Context context;
    private java.util.concurrent.ExecutorService executor;

    // 当前选中的坐标系
    private CoordinateSystem currentCoordinateSystem;

    public CoordinateSystemManager(Context context) {
        this.context = context;
        this.database = AppDatabase.getDatabase(context);
        this.coordinateSystemDao = database.coordinateSystemDao();
        this.knownPointDao = database.knownPointDao();
        this.executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        android.util.Log.d("CoordinateSystemManager", "CoordinateSystemManager 初始化完成");

        // 在后台线程中恢复当前坐标系设置
        executor.execute(() -> {
            try {
                android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
                long savedSystemId = prefs.getLong("current_coordinate_system_id", -1);
                android.util.Log.d("CoordinateSystemManager", "初始化时检查保存的坐标系ID: " + savedSystemId);
                if (savedSystemId > 0) {
                    currentCoordinateSystem = coordinateSystemDao.getById(savedSystemId);
                    if (currentCoordinateSystem != null) {
                        android.util.Log.d("CoordinateSystemManager", "初始化时恢复坐标系: " + currentCoordinateSystem.getName());
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("CoordinateSystemManager", "初始化时恢复坐标系失败: " + e.getMessage(), e);
                e.printStackTrace();
            }
        });
    }

    /**
     * 创建新的坐标系
     */
    public boolean createCoordinateSystem(String systemName, int ellipsoidType,
                                          int projectionType, int datumTransform,
                                          int planeCorrection, int heightFit) {
        try {
            java.util.concurrent.Future<Boolean> f = executor.submit(() -> {
                if (coordinateSystemDao.countByName(systemName) > 0) {
                    return false;
                }
                String ellipsoidName = getEllipsoidName(ellipsoidType);
                String projectionName = getProjectionName(projectionType);
                CoordinateSystem entity = new CoordinateSystem();
                entity.setName(systemName);
                entity.setEllipsoid(ellipsoidName);
                entity.setProjection(projectionName);
                entity.setCentralMeridian(null);
                entity.setZone(null);
                entity.setProjectionParams(null);
                entity.setDatumType(null);
                entity.setDatumParams(null);
                entity.setPlaneParams(null);
                entity.setHeightParams(null);
                long id = coordinateSystemDao.insert(entity);
                return id > 0;
            });
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 异步创建坐标系，返回 Future
     */
    public Future<Boolean> createCoordinateSystemAsync(String systemName, int ellipsoidType,
                                                       int projectionType, int datumTransform,
                                                       int planeCorrection, int heightFit) {
        return executor.submit(() ->
                createCoordinateSystem(systemName, ellipsoidType, projectionType, datumTransform, planeCorrection, heightFit));
    }

    /**
     * 设置当前坐标系
     */
    public boolean setCurrentCoordinateSystem(String systemName) {
        try {
            java.util.concurrent.Future<Boolean> f = executor.submit(() -> {
                List<CoordinateSystem> allSystems = coordinateSystemDao.getAll();
                CoordinateSystem found = null;
                for (CoordinateSystem s : allSystems) {
                    if (systemName.equals(s.getName())) { found = s; break; }
                }
                currentCoordinateSystem = found;
                return currentCoordinateSystem != null;
            });
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 异步设置当前坐标系（按名称）
     */
    public Future<Boolean> setCurrentCoordinateSystemAsync(String systemName) {
        return executor.submit(() -> setCurrentCoordinateSystem(systemName));
    }

    /**
     * 设置当前坐标系（通过ID）
     */
    public boolean setCurrentCoordinateSystemById(long systemId) {
        try {
            java.util.concurrent.Future<Boolean> f = executor.submit(() -> {
                android.util.Log.d("CoordinateSystemManager", "正在设置当前坐标系，ID: " + systemId);
                currentCoordinateSystem = coordinateSystemDao.getById(systemId);
                if (currentCoordinateSystem != null) {
                    android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
                    prefs.edit().putLong("current_coordinate_system_id", systemId).apply();
                    android.util.Log.d("CoordinateSystemManager", "坐标系设置成功: " + currentCoordinateSystem.getName() + " (ID: " + systemId + ")");
                    return true;
                } else {
                    android.util.Log.e("CoordinateSystemManager", "找不到ID为 " + systemId + " 的坐标系");
                    return false;
                }
            });
            return f.get();
        } catch (Exception e) {
            android.util.Log.e("CoordinateSystemManager", "设置坐标系失败: " + e.getMessage(), e);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 异步设置当前坐标系（按ID）
     */
    public Future<Boolean> setCurrentCoordinateSystemByIdAsync(long systemId) {
        return executor.submit(() -> setCurrentCoordinateSystemById(systemId));
    }

    /**
     * 获取当前坐标系（从持久化存储中恢复）
     */
    public CoordinateSystem getCurrentCoordinateSystem() {
        if (currentCoordinateSystem == null) {
            // 使用同步方式在后台线程中恢复当前坐标系
            try {
                java.util.concurrent.Future<CoordinateSystem> future = executor.submit(() -> {
                    try {
                        // 从 SharedPreferences 中恢复当前坐标系
                        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
                        long savedSystemId = prefs.getLong("current_coordinate_system_id", -1);
                        android.util.Log.d("CoordinateSystemManager", "从SharedPreferences恢复坐标系ID: " + savedSystemId);
                        if (savedSystemId > 0) {
                            CoordinateSystem system = coordinateSystemDao.getById(savedSystemId);
                            if (system != null) {
                                android.util.Log.d("CoordinateSystemManager", "成功恢复坐标系: " + system.getName());
                                currentCoordinateSystem = system;
                                return system;
                            } else {
                                android.util.Log.w("CoordinateSystemManager", "保存的坐标系ID " + savedSystemId + " 在数据库中不存在");
                            }
                        } else {
                            android.util.Log.d("CoordinateSystemManager", "没有保存的坐标系设置");
                        }
                        return null;
                    } catch (Exception e) {
                        android.util.Log.e("CoordinateSystemManager", "恢复坐标系失败: " + e.getMessage(), e);
                        e.printStackTrace();
                        return null;
                    }
                });

                // 等待后台线程完成，最多等待1秒
                currentCoordinateSystem = future.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                android.util.Log.e("CoordinateSystemManager", "获取当前坐标系时发生异常: " + e.getMessage(), e);
                e.printStackTrace();
            }
        }
        return currentCoordinateSystem;
    }

    /**
     * 保存已知点数据
     */
    public boolean saveKnownPoint(String pointName, String northOrX, String eastOrY, String height) {
        CoordinateSystem currentSystem = getCurrentCoordinateSystem();
        if (currentSystem == null) {
            return false;
        }

        try {
            // 检查点是否已存在
            List<KnownPoint> existingPoints = knownPointDao.getByCsSync(currentSystem.getId());
            KnownPoint existingPoint = null;
            if (existingPoints != null) {
                existingPoint = existingPoints.stream()
                        .filter(p -> pointName.equals(p.getName()))
                        .findFirst()
                        .orElse(null);
            }

            KnownPoint pointEntity;
            if (existingPoint != null) {
                pointEntity = existingPoint;
                // 更新创建时间
                pointEntity.setCreatedAt(System.currentTimeMillis());
            } else {
                pointEntity = new KnownPoint(currentSystem.getId(), pointName, null, null, null, null, null, null);
            }

            // 根据坐标系类型设置不同的字段
            if (currentSystem.getEllipsoidType() == ELLIPSOID_WGS84 &&
                    currentSystem.getProjectionType() == PROJECTION_UTM) {
                // WGS84 UTM投影
                pointEntity.setX(parseDouble(northOrX));
                pointEntity.setY(parseDouble(eastOrY));
                pointEntity.setZ(parseDouble(height));
            } else {
                // 其他椭球的高斯投影
                pointEntity.setX(parseDouble(northOrX));
                pointEntity.setY(parseDouble(eastOrY));
                pointEntity.setZ(parseDouble(height));
            }

            if (existingPoint != null) {
                knownPointDao.update(pointEntity);
            } else {
                knownPointDao.insert(pointEntity);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取已知点坐标数据
     */
    public String[] getKnownPointCoordinates(String pointName) {
        CoordinateSystem currentSystem = getCurrentCoordinateSystem();
        if (currentSystem == null) {
            return new String[]{"", "", ""};
        }

        try {
            List<KnownPoint> allPoints = knownPointDao.getByCsSync(currentSystem.getId());
            KnownPoint pointEntity = null;
            if (allPoints != null) {
                pointEntity = allPoints.stream()
                        .filter(p -> pointName.equals(p.getName()))
                        .findFirst()
                        .orElse(null);
            }

            if (pointEntity == null) {
                return new String[]{"", "", ""};
            }

            String[] coordinates = new String[3];

            if (currentSystem.getEllipsoidType() == ELLIPSOID_WGS84 &&
                    currentSystem.getProjectionType() == PROJECTION_UTM) {
                // WGS84 UTM投影
                coordinates[0] = formatDouble(pointEntity.getX());
                coordinates[1] = formatDouble(pointEntity.getY());
                coordinates[2] = formatDouble(pointEntity.getZ());
            } else {
                // 其他椭球的高斯投影
                coordinates[0] = formatDouble(pointEntity.getX());
                coordinates[1] = formatDouble(pointEntity.getY());
                coordinates[2] = formatDouble(pointEntity.getZ());
            }

            return coordinates;
        } catch (Exception e) {
            e.printStackTrace();
            return new String[]{"", "", ""};
        }
    }

    /**
     * 获取坐标标签（根据坐标系类型）
     */
    public String[] getCoordinateLabels() {
        if (currentCoordinateSystem == null) {
            return new String[]{"N(X)", "E(Y)", "H(Z)"};
        }

        if (currentCoordinateSystem.getEllipsoidType() == ELLIPSOID_WGS84 &&
                currentCoordinateSystem.getProjectionType() == PROJECTION_UTM) {
            return new String[]{"N(UTM)", "E(UTM)", "H"};
        } else {
            return new String[]{"N(X)", "E(Y)", "H(Z)"};
        }
    }

    /**
     * 获取表格显示数据（用于计算弹窗）
     */
    public Map<String, String[]> getTableDisplayData() {
        if (currentCoordinateSystem == null) {
            return new HashMap<>();
        }

        try {
            Map<String, String[]> displayData = new HashMap<>();
            List<KnownPoint> allPoints = knownPointDao.getByCsSync(currentCoordinateSystem.getId());
            if (allPoints == null) {
                allPoints = new ArrayList<>();
            }

            for (KnownPoint point : allPoints) {
                String pointName = point.getName();
                String[] rowData = new String[8]; // 8行数据

                // 不再展示默认精度占位，保持为空，等待真实数据填充
                rowData[0] = "";
                rowData[1] = "";

                if (currentCoordinateSystem.getEllipsoidType() == ELLIPSOID_WGS84 &&
                        currentCoordinateSystem.getProjectionType() == PROJECTION_UTM) {
                    // WGS84 UTM投影
                    rowData[2] = formatDouble(point.getX()) + "m"; // 北坐标
                    rowData[3] = formatDouble(point.getY()) + "m";  // 东坐标
                    rowData[4] = formatDouble(point.getZ()) + "m"; // 高程
                } else {
                    // 其他椭球的高斯投影
                    rowData[2] = formatDouble(point.getX()) + "m"; // 北坐标
                    rowData[3] = formatDouble(point.getY()) + "m";  // 东坐标
                    rowData[4] = formatDouble(point.getZ()) + "m"; // 高程
                }

                // 纬度和经度
                rowData[5] = formatDouble(point.getMeasuredB()); // 纬度
                rowData[6] = formatDouble(point.getMeasuredL()); // 经度
                rowData[7] = formatDouble(point.getMeasuredH()) + "m"; // 大地高

                displayData.put(pointName, rowData);
            }

            return displayData;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * 删除已知点
     */
    public boolean deleteKnownPoint(String pointName) {
        if (currentCoordinateSystem == null) {
            return false;
        }

        try {
            List<KnownPoint> allPoints = knownPointDao.getByCsSync(currentCoordinateSystem.getId());
            if (allPoints != null) {
                KnownPoint pointToDelete = allPoints.stream()
                        .filter(p -> pointName.equals(p.getName()))
                        .findFirst()
                        .orElse(null);
                if (pointToDelete != null) {
                    knownPointDao.delete(pointToDelete);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取所有已知点名称
     */
    public List<String> getAllKnownPointNames() {
        if (currentCoordinateSystem == null) {
            return new ArrayList<>();
        }

        try {
            List<String> names = new ArrayList<>();
            // Use synchronous query to avoid null LiveData when not observed
            List<KnownPoint> allPoints = knownPointDao.getByCsSync(currentCoordinateSystem.getId());
            if (allPoints != null) {
                for (KnownPoint point : allPoints) {
                    names.add(point.getName());
                }
            }
            return names;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定坐标系的已知点数量（同步）
     */
    public int getKnownPointCount(long coordinateSystemId) {
        try {
            java.util.concurrent.Future<Integer> f = executor.submit(() -> knownPointDao.count(coordinateSystemId));
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 异步获取已知点数量
     */
    public Future<Integer> getKnownPointCountAsync(long coordinateSystemId) {
        return executor.submit(() -> getKnownPointCount(coordinateSystemId));
    }

    /**
     * 获取所有坐标系
     */
    public List<CoordinateSystem> getAllCoordinateSystems() {
        try {
            java.util.concurrent.Future<List<CoordinateSystem>> f = executor.submit(() -> coordinateSystemDao.getAll());
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 异步获取所有坐标系
     */
    public Future<List<CoordinateSystem>> getAllCoordinateSystemsAsync() {
        return executor.submit(this::getAllCoordinateSystems);
    }

    /**
     * 删除坐标系
     */
    public boolean deleteCoordinateSystem(String systemName) {
        try {
            java.util.concurrent.Future<Boolean> f = executor.submit(() -> {
                List<CoordinateSystem> allSystems = coordinateSystemDao.getAll();
                CoordinateSystem systemToDelete = null;
                for (CoordinateSystem s : allSystems) {
                    if (systemName.equals(s.getName())) { systemToDelete = s; break; }
                }
                if (systemToDelete != null) {
                    coordinateSystemDao.delete(systemToDelete);
                    return true;
                }
                return false;
            });
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 异步删除坐标系
     */
    public Future<Boolean> deleteCoordinateSystemAsync(String systemName) {
        return executor.submit(() -> deleteCoordinateSystem(systemName));
    }

    /**
     * 获取椭球名称
     */
    public static String getEllipsoidName(int ellipsoidType) {
        switch (ellipsoidType) {
            case ELLIPSOID_CGCS2000: return "CGCS2000";
            case ELLIPSOID_WGS84: return "WGS84";
            case ELLIPSOID_BEIJING54: return "北京54";
            case ELLIPSOID_XIAN80: return "西安80";
            default: return "未知";
        }
    }

    /**
     * 获取投影名称
     */
    public static String getProjectionName(int projectionType) {
        switch (projectionType) {
            case PROJECTION_GAUSS: return "高斯投影（3度带）";
            case PROJECTION_UTM: return "UTM投影";
            default: return "未知";
        }
    }

    /**
     * 完整的坐标转换流程
     * 
     * 转换顺序：
     * 1. 投影转换：经纬度 → 平面坐标（北向、东向）
     * 2. 基准转换 OR 平面校正（二选一，优先使用平面校正）
     * 3. 高程拟合（必须应用）
     * 
     * @param lon 经度（度）
     * @param lat 纬度（度）
     * @param height 大地高（米）
     * @return 转换后的坐标 [北坐标, 东坐标, 高程]，失败返回null
     */
    public double[] transformCoordinate(double lon, double lat, double height) {
        CoordinateSystem currentSystem = getCurrentCoordinateSystem();
        if (currentSystem == null) return null;

        try {
            // ==================== 步骤1: 投影转换 ====================
            double[] xy = null;
            String projection = normalizeProjectionName(currentSystem.getProjection());
            String ellipsoid = currentSystem.getEllipsoid();
            Double centralMeridian = currentSystem.getCentralMeridian();
            Integer zone = currentSystem.getZone();

            // 高斯投影：合并3/6度带（不使用带号），根据椭球与中央子午线统一计算
            if (projection != null && projection.startsWith("GAUSS")) {
                // 若未设置中央子午线：必须有值，否则无法进行投影转换
                if (centralMeridian == null) {
                    Log.e("CoordinateSystemManager", "错误: 高斯投影必须设置中央子午线");
                    return null;
                }
                // 针对不同椭球使用专用实现（每个类内部已包含各自椭球参数）
                // 注意：WGS84椭球一般用于UTM投影，不建议用于高斯投影
                if ("CGCS2000".equals(ellipsoid) || ellipsoid == null || ellipsoid.isEmpty()) {
                    GaussKrugerCGCS2000.XY xyPair = GaussKrugerCGCS2000.projectCGCS2000(lon, lat, centralMeridian);
                    xy = new double[]{xyPair.northing, xyPair.easting};
                } else if ("北京54".equals(ellipsoid)) {
                    BJ54GaussKruger.Result r = BJ54GaussKruger.projectWithCentralMeridian(lon, lat, centralMeridian, true);
                    xy = new double[]{r.northing, r.easting};
                } else if ("西安80".equals(ellipsoid)) {
                    Xian80GaussKruger.Result r = Xian80GaussKruger.projectWithCentralMeridian(lon, lat, centralMeridian, true);
                    xy = new double[]{r.northing, r.easting};
                } else if ("WGS84".equals(ellipsoid)) {
                    Log.e("CoordinateSystemManager", "错误: WGS84椭球不支持高斯投影，请使用UTM投影");
                    return null;
                } else {
                    Log.w("CoordinateSystemManager", "未知椭球类型: " + ellipsoid + "，默认使用CGCS2000");
                    GaussKrugerCGCS2000.XY xyPair = GaussKrugerCGCS2000.projectCGCS2000(lon, lat, centralMeridian);
                    xy = new double[]{xyPair.northing, xyPair.easting};
                }
                Log.d("CoordinateSystemManager", "✓ 投影转换: 高斯投影 N=" + xy[0] + ", E=" + xy[1] + ", L0=" + centralMeridian);
            } else if ("UTM".equals(projection)) {
                WGS84ToUTM.UTM utm;
                if (centralMeridian != null) {
                    utm = WGS84ToUTM.lonLatToUTM(lon, lat, centralMeridian, zone);
                } else {
                    utm = WGS84ToUTM.lonLatToUTMWithAutoCalculatedCentralMeridian(lon, lat);
                }
                xy = new double[]{utm.northing, utm.easting};
                Log.d("CoordinateSystemManager", "✓ 投影转换: UTM N=" + xy[0] + ", E=" + xy[1] + ", zone=" + utm.zone + utm.hemisphere);
            } else {
                xy = new double[]{lon, lat};
                Log.d("CoordinateSystemManager", "✓ 投影转换: 无投影 X=" + xy[0] + ", Y=" + xy[1]);
            }

            // 应用投影面高程修正（如果有）
            // 注意：投影面高程修正主要影响北坐标（Y坐标），对东坐标影响很小
            String projectionParams = currentSystem.getProjectionParams();
            if (projectionParams != null && !projectionParams.isEmpty()) {
                try {
                    JSONObject projJson = new JSONObject(projectionParams);
                    if (projJson.has("projectionHeight")) {
                        double projectionHeight = projJson.optDouble("projectionHeight", 0.0);
                        if (Math.abs(projectionHeight) > 0.001) { // 投影面高程不为0时才修正
                            // 投影面高程修正公式（高斯投影）：
                            // 对于北坐标：ΔN = -H * (1 - H/(2*R)) ≈ -H（当H较小时）
                            // 其中H是投影面高程（相对于椭球面的高度，米）
                            // R是地球平均半径（约6371000米）
                            // 当H < 1000米时，可以简化为：ΔN ≈ -H
                            double R = 6371000.0; // 地球平均半径（米）
                            double deltaN = -projectionHeight * (1.0 - projectionHeight / (2.0 * R));
                            double northBeforeCorrection = xy[0];
                            xy[0] = xy[0] + deltaN; // 修正北坐标

                            Log.d("CoordinateSystemManager", String.format("✓ 投影面高程修正: 投影面高=%.3f m, 修正前北坐标=%.6f m, 修正量=%.6f m, 修正后北坐标=%.6f m",
                                    projectionHeight, northBeforeCorrection, deltaN, xy[0]));
                        } else {
                            Log.d("CoordinateSystemManager", "投影面高程为0或未设置，跳过高程修正");
                        }
                    } else {
                        Log.d("CoordinateSystemManager", "投影参数中未找到投影面高程字段");
                    }
                } catch (Exception e) {
                    Log.w("CoordinateSystemManager", "解析投影面高程参数失败: " + e.getMessage(), e);
                }
            } else {
                Log.d("CoordinateSystemManager", "投影参数为空，未应用投影面高程修正");
            }

            // 当前坐标值
            double north = xy[0];
            double east = xy[1];
            double h = height;

            // ==================== 步骤2: 基准转换 OR 平面校正（二选一）====================
            String planeParams = currentSystem.getPlaneParams();
            String datumParams = currentSystem.getDatumParams();

            // 优先使用平面校正参数
            if (planeParams != null && !planeParams.isEmpty()) {
                Log.d("CoordinateSystemManager", "→ 应用平面校正参数: " + planeParams);
                JSONObject planeJson = new JSONObject(planeParams);
                int type = planeJson.optInt("type", 0);  // 使用数字索引

                if (type == 1) { // 三参数
                    double dx = planeJson.optDouble("dx", 0);
                    double dy = planeJson.optDouble("dy", 0);
                    double theta = planeJson.optDouble("theta", 0);
                    ThreeParameterTransform transform = new ThreeParameterTransform(dx, dy, theta);
                    double[] xy2 = transform.transform(north, east);
                    north = xy2[0];
                    east = xy2[1];
                    Log.d("CoordinateSystemManager", "  ✓ 三参数平面校正");
                } else if (type == 2) { // 四参数
                    double dx = planeJson.optDouble("dx", 0);
                    double dy = planeJson.optDouble("dy", 0);
                    double theta = planeJson.optDouble("theta", 0);
                    double k = planeJson.optDouble("k", 1);
                    FourParameterSolver.FourParameterTransform transform =
                            new FourParameterSolver.FourParameterTransform(dx, dy, theta, k, true);
                    double[] xy2 = transform.transform(north, east);
                    north = xy2[0];
                    east = xy2[1];
                    Log.d("CoordinateSystemManager", "  ✓ 四参数平面校正");
                } else if (type == 3) { // 平面平差
                    double northOrigin = planeJson.optDouble("north_origin", 0);
                    double eastOrigin = planeJson.optDouble("east_origin", 0);
                    double northTranslation = planeJson.optDouble("north_translation", 0);
                    double eastTranslation = planeJson.optDouble("east_translation", 0);
                    double rotationScale = planeJson.optDouble("rotation_scale", 0);
                    double scale = planeJson.optDouble("scale", 1);
                    PlaneAdjustmentTransform transform = new PlaneAdjustmentTransform(
                            northOrigin, eastOrigin, northTranslation, eastTranslation,
                            rotationScale, scale, 0);
                    double[] xy2 = transform.transform(north, east);
                    north = xy2[0];
                    east = xy2[1];
                    Log.d("CoordinateSystemManager", "  ✓ 平面平差校正");
                } else {
                    Log.w("CoordinateSystemManager", "⚠ 未知的平面校正类型: " + type);
                }

            } else if (datumParams != null && !datumParams.isEmpty()) {
                // 使用基准转换参数
                Log.d("CoordinateSystemManager", "→ 应用基准转换参数");
                JSONObject json = new JSONObject(datumParams);

                // 优先从JSON中读取type，如果没有则从datumType字段获取
                int datumType = json.optInt("type", currentSystem.getDatumTransform());

                if (datumType == 1) { // 七参数（基准转换只用七参数）
                    double dx = json.optDouble("dx", 0);
                    double dy = json.optDouble("dy", 0);
                    double dz = json.optDouble("dz", 0);
                    double rx = json.optDouble("rx", 0);
                    double ry = json.optDouble("ry", 0);
                    double rz = json.optDouble("rz", 0);
                    double scale = json.optDouble("scale", 0);
                    SevenParameterTransform transform =
                            new SevenParameterTransform(dx, dy, dz, scale * 1e-6, rx, ry, rz, true);
                    double[] xyz = transform.transform(north, east, h);
                    north = xyz[0];
                    east = xyz[1];
                    h = xyz[2];
                    Log.d("CoordinateSystemManager", "  ✓ 七参数基准转换");
                } else {
                    Log.w("CoordinateSystemManager", "⚠ 警告: 未知的基准转换类型: " + datumType);
                }
            } else {
                Log.d("CoordinateSystemManager", "→ 无基准转换或平面校正参数");
            }

            // ==================== 步骤3: 高程拟合（必须应用）====================
            String heightParams = currentSystem.getHeightParams();
            if (heightParams != null && !heightParams.isEmpty()) {
                Log.d("CoordinateSystemManager", "→ 应用高程拟合参数");
                JSONObject heightJson = new JSONObject(heightParams);
                int heightType = heightJson.optInt("type", 0);  // 使用数字索引

                if (heightType == 4) { // 加权平均
                    double weightedAverage = heightJson.optDouble("a", 0);
                    h = h + weightedAverage;
                    Log.d("CoordinateSystemManager", "  ✓ 加权平均高程拟合: Δh=" + weightedAverage);

                } else if (heightType == 1) { // 垂直平差
                    double northOrigin = heightJson.optDouble("n0", 0);
                    double eastOrigin = heightJson.optDouble("e0", 0);
                    double heightConstant = heightJson.optDouble("const", 0);
                    double eastSlope = heightJson.optDouble("eslope", 0);
                    double northSlope = heightJson.optDouble("nslope", 0);

                    double x_rel = east - eastOrigin;
                    double y_rel = north - northOrigin;
                    double deltaH = heightConstant + eastSlope * x_rel + northSlope * y_rel;
                    h = h + deltaH;
                    Log.d("CoordinateSystemManager", "  ✓ 垂直平差高程拟合: Δh=" + deltaH);

                } else if (heightType == 2) { // 平面拟合
                    double northOrigin = heightJson.optDouble("n0", 0);
                    double eastOrigin = heightJson.optDouble("e0", 0);
                    double paramA = heightJson.optDouble("a", 0);
                    double paramB = heightJson.optDouble("b", 0);
                    double paramC = heightJson.optDouble("c", 0);

                    double x_rel = east - eastOrigin;
                    double y_rel = north - northOrigin;
                    double deltaH = paramA + paramB * x_rel + paramC * y_rel;
                    h = h + deltaH;
                    Log.d("CoordinateSystemManager", "  ✓ 平面拟合高程: Δh=" + deltaH);

                } else if (heightType == 3) { // 曲面拟合
                    // 支持两种字段名格式（兼容性）
                    double northOrigin = heightJson.optDouble("north_origin", heightJson.optDouble("n0", 0));
                    double eastOrigin = heightJson.optDouble("east_origin", heightJson.optDouble("e0", 0));

                    // 尝试读取8个参数
                    double a = heightJson.optDouble("a", 0);
                    double b = heightJson.optDouble("b", 0);
                    double c = heightJson.optDouble("c", 0);
                    double d = heightJson.optDouble("d", 0);
                    double e = heightJson.optDouble("e", 0);
                    double f = heightJson.optDouble("f", 0);
                    double g = heightJson.optDouble("g", 0);
                    double hParam = heightJson.optDouble("h", 0);

                    double x = east - eastOrigin;
                    double y = north - northOrigin;

                    double deltaH = a +                    // a
                                   b * x +                 // b*x
                                   c * y +                 // c*y
                                   d * x * x +             // d*x²
                                   e * y * y +             // e*y²
                                   f * x * y +             // f*x*y
                                   g * x * x * x +         // g*x³
                                   hParam * y * y * y;     // h*y³
                    h = h + deltaH;
                    Log.d("CoordinateSystemManager", "  ✓ 曲面拟合高程(8参数): Δh=" + deltaH);
                }
            } else {
                Log.w("CoordinateSystemManager", "⚠ 警告: 未设置高程拟合参数（高程拟合应该是必须的）");
            }

            // ==================== 返回最终结果 ====================
            Log.d("CoordinateSystemManager", "✓ 坐标转换完成: N=" + north + ", E=" + east + ", H=" + h);
            return new double[]{north, east, h};

        } catch (Exception e) {
            Log.e("CoordinateSystemManager", "坐标转换失败", e);
            e.printStackTrace();
            return null;
        }
    }

    private static String normalizeProjectionName(String projection) {
        if (projection == null) return null;
        String p = projection.trim();
        if (p.isEmpty()) return p;

        if ("UTM投影".equals(p)) return "UTM";
        if (p.contains("UTM")) return "UTM";

        if (p.contains("高斯")) return "GAUSS";
        if (p.startsWith("GAUSS")) return p;

        return p;
    }

    /**
     * 测试方法：验证当前坐标系设置
     */
    public void testCurrentCoordinateSystem() {
        android.util.Log.d("CoordinateSystemManager", "=== 开始测试当前坐标系设置 ===");

        // 1. 检查内存中的当前坐标系
        if (currentCoordinateSystem != null) {
            android.util.Log.d("CoordinateSystemManager", "内存中的当前坐标系: " + currentCoordinateSystem.getName() + " (ID: " + currentCoordinateSystem.getId() + ")");
        } else {
            android.util.Log.w("CoordinateSystemManager", "内存中没有当前坐标系");
        }

        // 2. 检查SharedPreferences中的设置（在后台线程中执行数据库操作）
        try {
            android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
            long savedSystemId = prefs.getLong("current_coordinate_system_id", -1);
            android.util.Log.d("CoordinateSystemManager", "SharedPreferences中保存的坐标系ID: " + savedSystemId);

            if (savedSystemId > 0) {
                // 在后台线程中查询数据库
                executor.execute(() -> {
                    try {
                        CoordinateSystem savedSystem = coordinateSystemDao.getById(savedSystemId);
                        if (savedSystem != null) {
                            android.util.Log.d("CoordinateSystemManager", "SharedPreferences中的坐标系: " + savedSystem.getName() + " (ID: " + savedSystemId + ")");
                        } else {
                            android.util.Log.e("CoordinateSystemManager", "SharedPreferences中的坐标系ID在数据库中不存在: " + savedSystemId);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("CoordinateSystemManager", "查询数据库失败: " + e.getMessage(), e);
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("CoordinateSystemManager", "检查SharedPreferences失败: " + e.getMessage(), e);
        }

        // 3. 测试getCurrentCoordinateSystem方法
        CoordinateSystem testSystem = getCurrentCoordinateSystem();
        if (testSystem != null) {
            android.util.Log.d("CoordinateSystemManager", "getCurrentCoordinateSystem()返回: " + testSystem.getName() + " (ID: " + testSystem.getId() + ")");
        } else {
            android.util.Log.w("CoordinateSystemManager", "getCurrentCoordinateSystem()返回null");
        }

        android.util.Log.d("CoordinateSystemManager", "=== 测试完成 ===");
    }

    // 辅助方法
    private double parseDouble(String value) {
        try {
            return value == null || value.isEmpty() ? 0.0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String formatDouble(Double value) {
        if (value == null) {
            return "";
        }
        return String.format("%.3f", value);
    }

    // Getter方法
    public String getCurrentSystemName() {
        CoordinateSystem currentSystem = getCurrentCoordinateSystem();
        return currentSystem != null ? currentSystem.getName() : null;
    }

    public int getCurrentEllipsoidType() {
        CoordinateSystem currentSystem = getCurrentCoordinateSystem();
        return currentSystem != null ? currentSystem.getEllipsoidType() : -1;
    }

    public int getCurrentProjectionType() {
        CoordinateSystem currentSystem = getCurrentCoordinateSystem();
        return currentSystem != null ? currentSystem.getProjectionType() : -1;
    }
}