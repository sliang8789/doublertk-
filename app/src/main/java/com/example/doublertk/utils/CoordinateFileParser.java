package com.example.doublertk.utils;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 坐标文件解析工具类
 * 支持格式：每行 xNorth,yEast[,z]
 * 支持分隔符：逗号(,，)、分号(;；)、顿号(、)、空白符
 */
public final class CoordinateFileParser {
    private static final String TAG = "CoordinateFileParser";

    private CoordinateFileParser() {}

    /**
     * 解析坐标文件
     *
     * @param uri 文件URI
     * @param contentResolver ContentResolver实例
     * @return 坐标点列表，每项为 [north, east, altitude]
     */
    public static List<double[]> parseCoordinateFile(Uri uri, ContentResolver contentResolver) {
        List<double[]> points = new ArrayList<>();
        if (uri == null || contentResolver == null) return points;

        try (InputStream is = contentResolver.openInputStream(uri)) {
            if (is == null) {
                Log.e(TAG, "无法打开坐标文件");
                return points;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // 去除可能存在的 UTF-8 BOM
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }

                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue; // 跳过空行和注释行
                }

                try {
                    // 支持英文/中文标点分隔：逗号(,，)、分号(;；)、顿号(、)及空白
                    String[] tokens = line.split("[\\s,，,;；、]+");
                    if (tokens.length < 2) {
                        Log.w(TAG, "第" + lineNumber + "行格式错误，至少需要2个数值: " + line);
                        continue;
                    }

                    String t0 = tokens[0].trim();
                    String t1 = tokens[1].trim();
                    String t2 = tokens.length >= 3 ? tokens[2].trim() : "0";

                    double north = Double.parseDouble(t0);
                    double east = Double.parseDouble(t1);
                    double alt = Double.parseDouble(t2);

                    points.add(new double[]{north, east, alt});
                } catch (NumberFormatException e) {
                    Log.w(TAG, "第" + lineNumber + "行数值解析失败: " + line);
                }
            }

            Log.d(TAG, "成功解析 " + points.size() + " 个坐标点");
        } catch (Exception e) {
            Log.e(TAG, "坐标文件解析失败", e);
        }

        return points;
    }

    /**
     * 从字符串解析单个坐标点
     * @param coordinateStr 坐标字符串，格式: "x,y" 或 "x,y,z"
     * @return 坐标数组 [north, east, altitude]，解析失败返回null
     */
    public static double[] parseCoordinateString(String coordinateStr) {
        if (coordinateStr == null || coordinateStr.trim().isEmpty()) return null;

        try {
            String[] tokens = coordinateStr.trim().split("[\\s,，,;；、]+");
            if (tokens.length < 2) return null;

            double north = Double.parseDouble(tokens[0].trim());
            double east = Double.parseDouble(tokens[1].trim());
            double alt = tokens.length >= 3 ? Double.parseDouble(tokens[2].trim()) : 0.0;

            return new double[]{north, east, alt};
        } catch (NumberFormatException e) {
            Log.e(TAG, "坐标字符串解析失败: " + coordinateStr, e);
            return null;
        }
    }
}









