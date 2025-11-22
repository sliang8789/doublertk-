package com.example.doublertk.data;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 作业仓库，负责线程调度 & DAO 封装。
 */
public class JobRepository {

    private final JobDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public JobRepository(Application app) {
        dao = AppDatabase.getDatabase(app).jobDao();
    }

    public long insertAndReturnId(Job job) {
        Future<Long> f = executor.submit(() -> dao.insert(job));
        try {
            return f.get();
        } catch (Exception e) {
            android.util.Log.e("JobRepository", "insert job failed: "
                    + "name=" + job.getName(), e);
            return -1;
        }
    }

    public void update(Job job) {
        executor.execute(() -> {
            try {
                dao.update(job);
                android.util.Log.d("JobRepository", "成功更新作业: " + job.getName());
            } catch (Exception e) {
                android.util.Log.e("JobRepository", "更新作业失败: "
                        + "id=" + job.getId()
                        + ", name=" + job.getName(), e);
            }
        });
    }

    public boolean updateAndReturnResult(Job job) {
        Future<Boolean> f = executor.submit(() -> {
            try {
                dao.update(job);
                android.util.Log.d("JobRepository", "成功更新作业: " + job.getName());
                return true;
            } catch (Exception e) {
                android.util.Log.e("JobRepository", "更新作业失败: "
                        + "id=" + job.getId()
                        + ", name=" + job.getName(), e);
                return false;
            }
        });
        try {
            return f.get();
        } catch (Exception e) {
            android.util.Log.e("JobRepository", "获取更新结果失败", e);
            return false;
        }
    }

    public void delete(Job job) {
        executor.execute(() -> dao.delete(job));
    }

    public LiveData<List<Job>> getAll() {
        return dao.observeAll();
    }

    /**
     * 同步获取所有作业（按创建时间倒序）
     */
    public List<Job> getAllSync() {
        Future<List<Job>> f = executor.submit(() -> dao.getAll());
        try {
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Job getById(long id) {
        Future<Job> f = executor.submit(() -> dao.getById(id));
        try {
            return f.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 检查作业名称是否已存在
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
     * 检查作业名称是否已存在（排除指定ID的记录，用于编辑模式）
     */
    public boolean isNameExistsExcludingId(String name, long excludeId) {
        Future<Integer> f = executor.submit(() -> {
            List<Job> allJobs = dao.getAll();
            for (Job job : allJobs) {
                if (job.getId() != excludeId && name.equals(job.getName())) {
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

