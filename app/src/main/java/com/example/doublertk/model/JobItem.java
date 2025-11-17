package com.example.doublertk.model;

public class JobItem {
    private final String name;
    private final long timestamp;

    public JobItem(String name, long timestamp) {
        this.name = name;
        this.timestamp = timestamp;
    }

    public String getName() {
        return name;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

