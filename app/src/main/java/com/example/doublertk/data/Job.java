package com.example.doublertk.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 作业实体，保存作业信息。
 */
@Entity(tableName = "jobs",
        indices = {@Index(value = {"name"}, unique = true)})
public class Job {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;            // 作业名称
    private long createdAt;         // 创建时间戳
    private String description;     // 作业描述（可选）

    // 无参构造函数供 Room 使用
    public Job() {
        this.createdAt = System.currentTimeMillis();
    }

    @androidx.room.Ignore
    public Job(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
    }

    @androidx.room.Ignore
    public Job(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = System.currentTimeMillis();
    }

    // Getter / Setter
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

