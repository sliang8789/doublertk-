package com.example.doublertk.data;

public class CoordinateTransformResult {

    private final double north;
    private final double east;
    private final double height;

    private final long coordinateSystemId;

    public CoordinateTransformResult(double north, double east, double height, long coordinateSystemId) {
        this.north = north;
        this.east = east;
        this.height = height;
        this.coordinateSystemId = coordinateSystemId;
    }

    public double getNorth() {
        return north;
    }

    public double getEast() {
        return east;
    }

    public double getHeight() {
        return height;
    }

    public long getCoordinateSystemId() {
        return coordinateSystemId;
    }
}
