package com.example.doublertk.data;

import android.content.Context;

public class CoordinateTransformService {

    private final CoordinateSystemManager coordinateSystemManager;

    public CoordinateTransformService(Context context) {
        this.coordinateSystemManager = new CoordinateSystemManager(context);
    }

    public CoordinateTransformResult transformToCurrentSystem(double lon, double lat, double height) {
        CoordinateSystem cs = coordinateSystemManager.getCurrentCoordinateSystem();
        if (cs == null) return null;

        double[] neh = coordinateSystemManager.transformCoordinate(lon, lat, height);
        if (neh == null || neh.length < 3) return null;

        return new CoordinateTransformResult(neh[0], neh[1], neh[2], cs.getId());
    }
}
