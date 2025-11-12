package com.example.doublertk.data;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 坐标系仓库，负责线程调度 & DAO 封装。
 */
public class CoordinateSystemRepository {

    private final CoordinateSystemDao dao;
    private final KnownPointDao knownPointDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CoordinateSystemRepository(Application app) {
        dao = AppDatabase.getDatabase(app).coordinateSystemDao();
        knownPointDao = AppDatabase.getDatabase(app).knownPointDao();
    }

    public long insertAndReturnId(CoordinateSystem cs) {
        Future<Long> f = executor.submit(() -> dao.insert(cs));
        try {
            return f.get();
        } catch (Exception e) {
            android.util.Log.e("CoordinateSystemRepository", "insert coordinate_systems failed: "
                    + "name=" + cs.getName()
                    + ", ellipsoid=" + cs.getEllipsoid()
                    + ", projection=" + cs.getProjection()
                    + ", centralMeridian=" + cs.getCentralMeridian()
                    + ", zone=" + cs.getZone()
                    + ", projectionParams=" + cs.getProjectionParams()
                    + ", datumType=" + cs.getDatumType()
                    + ", datumParams=" + cs.getDatumParams()
                    + ", planeParams=" + cs.getPlaneParams()
                    + ", heightParams=" + cs.getHeightParams(), e);
            return -1;
        }
    }

    public void update(CoordinateSystem cs) {
        executor.execute(() -> {
            try {
                dao.update(cs);
                android.util.Log.d("CoordinateSystemRepository", "成功更新坐标系: " + cs.getName());
            } catch (Exception e) {
                android.util.Log.e("CoordinateSystemRepository", "更新坐标系失败: "
                        + "id=" + cs.getId()
                        + ", name=" + cs.getName()
                        + ", ellipsoid=" + cs.getEllipsoid()
                        + ", projection=" + cs.getProjection()
                        + ", centralMeridian=" + cs.getCentralMeridian()
                        + ", zone=" + cs.getZone()
                        + ", projectionParams=" + cs.getProjectionParams()
                        + ", datumType=" + cs.getDatumType()
                        + ", datumParams=" + cs.getDatumParams()
                        + ", planeParams=" + cs.getPlaneParams()
                        + ", heightParams=" + cs.getHeightParams(), e);
            }
        });
    }
    
    /**
     * 同步更新坐标系，返回是否成功
     * @param cs 要更新的坐标系
     * @return 如果更新成功返回true，否则返回false
     */
    public boolean updateAndReturnResult(CoordinateSystem cs) {
        Future<Boolean> f = executor.submit(() -> {
            try {
                dao.update(cs);
                android.util.Log.d("CoordinateSystemRepository", "成功更新坐标系: " + cs.getName());
                return true;
            } catch (Exception e) {
                android.util.Log.e("CoordinateSystemRepository", "更新坐标系失败: "
                        + "id=" + cs.getId()
                        + ", name=" + cs.getName()
                        + ", ellipsoid=" + cs.getEllipsoid()
                        + ", projection=" + cs.getProjection()
                        + ", centralMeridian=" + cs.getCentralMeridian()
                        + ", zone=" + cs.getZone()
                        + ", projectionParams=" + cs.getProjectionParams()
                        + ", datumType=" + cs.getDatumType()
                        + ", datumParams=" + cs.getDatumParams()
                        + ", planeParams=" + cs.getPlaneParams()
                        + ", heightParams=" + cs.getHeightParams(), e);
                return false;
            }
        });
        try {
            return f.get();
        } catch (Exception e) {
            android.util.Log.e("CoordinateSystemRepository", "获取更新结果失败", e);
            return false;
        }
    }

    public void delete(CoordinateSystem cs) {
        executor.execute(() -> dao.delete(cs));
    }

    public LiveData<List<CoordinateSystem>> getAll() {
        return dao.observeAll();
    }

    /**
     * 同步获取所有坐标系统（按创建时间倒序）
     */
    public List<CoordinateSystem> getAllSync() {
        Future<List<CoordinateSystem>> f = executor.submit(() -> dao.getAll());
        try {
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CoordinateSystem getById(long id) {
        Future<CoordinateSystem> f = executor.submit(() -> dao.getById(id));
        try {
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 删除所有坐标系统并重置ID序列
     */
    public void deleteAllAndResetSequence() {
        executor.execute(() -> {
            try {
                // 删除所有已知点
                knownPointDao.deleteAll();
                // 重置已知点表的序列
                knownPointDao.resetSequence();

                // 删除所有坐标系统
                dao.deleteAll();
                // 重置坐标系统表的序列
                dao.resetSequence();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 检查坐标系名称是否已存在
     * @param name 坐标系名称
     * @return 如果名称已存在返回true，否则返回false
     */
    public boolean isNameExists(String name) {
        Future<Integer> f = executor.submit(() -> dao.countByName(name));
        try {
            return f.get() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查坐标系名称是否已存在（排除指定ID的记录，用于编辑模式）
     * @param name 坐标系名称
     * @param excludeId 要排除的记录ID
     * @return 如果名称已存在返回true，否则返回false
     */
    public boolean isNameExistsExcludingId(String name, long excludeId) {
        Future<Integer> f = executor.submit(() -> {
            // 获取所有记录
            List<CoordinateSystem> allSystems = dao.getAll();
            for (CoordinateSystem cs : allSystems) {
                if (cs.getId() != excludeId && name.equals(cs.getName())) {
                    return 1;
                }
            }
            return 0;
        });
        try {
            return f.get() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
