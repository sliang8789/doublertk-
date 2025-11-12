package com.example.doublertk.data;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 已知点数据仓库，封装线程访问与异步操作。
 */
public class KnownPointRepository {

    private final KnownPointDao knownPointDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public KnownPointRepository(Application app) {
        AppDatabase db = AppDatabase.getDatabase(app);
        knownPointDao = db.knownPointDao();
    }

    public void insert(KnownPoint point) {
        executor.execute(() -> knownPointDao.insert(point));
    }

    /**
     * 同步插入已知点
     */
    public long insertSync(KnownPoint point) {
        Future<Long> future = executor.submit(() -> knownPointDao.insert(point));
        try {
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public void update(KnownPoint point) {
        executor.execute(() -> knownPointDao.update(point));
    }

    public void delete(KnownPoint point) {
        executor.execute(() -> knownPointDao.delete(point));
    }

    public LiveData<List<KnownPoint>> getByCs(long csId) {
        return knownPointDao.getByCs(csId);
    }

    public void deleteByCs(long csId) {
        executor.execute(() -> knownPointDao.deleteByCs(csId));
    }

    /**
     * 同步获取指定坐标系的所有已知点
     */
    public List<KnownPoint> getByCsSync(long csId) {
        Future<List<KnownPoint>> future = executor.submit(() -> knownPointDao.getByCsSync(csId));
        try {
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
