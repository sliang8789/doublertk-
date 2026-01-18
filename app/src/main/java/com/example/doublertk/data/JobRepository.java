package com.example.doublertk.data;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 作业仓库类，封装作业数据访问逻辑
 */
public class JobRepository {

    private JobDao jobDao;
    private ExecutorService executor;

    public JobRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        this.jobDao = database.jobDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 插入作业
     */
    public Future<Long> insertJob(Job job) {
        return executor.submit(() -> jobDao.insert(job));
    }

    /**
     * 更新作业
     */
    public Future<Integer> updateJob(Job job) {
        return executor.submit(() -> jobDao.update(job));
    }

    /**
     * 删除作业
     */
    public Future<Void> deleteJob(Job job) {
        return executor.submit(() -> {
            jobDao.delete(job);
            return null;
        });
    }

    /**
     * 获取所有作业
     */
    public Future<List<Job>> getAllJobs() {
        return executor.submit(() -> jobDao.getAll());
    }

    /**
     * 根据ID获取作业
     */
    public Future<Job> getJobById(long id) {
        return executor.submit(() -> jobDao.getById(id));
    }

    /**
     * 观察所有作业（LiveData）
     */
    public androidx.lifecycle.LiveData<List<Job>> observeAllJobs() {
        return jobDao.observeAll();
    }
}






