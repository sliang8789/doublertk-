package com.example.doublertk.model;

/**
 * 作业项数据模型
 */
public class JobItem {
    private String jobName;
    private String jobTime;

    public JobItem(String jobName, String jobTime) {
        this.jobName = jobName;
        this.jobTime = jobTime;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobTime() {
        return jobTime;
    }

    public void setJobTime(String jobTime) {
        this.jobTime = jobTime;
    }
}

