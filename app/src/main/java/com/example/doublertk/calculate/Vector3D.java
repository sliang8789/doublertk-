package com.example.doublertk.calculate;

public class Vector3D {
    public double x, y, z;

    public Vector3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // 向量减法
    public static Vector3D subtract(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
    }

    // 向量加法
    public static Vector3D add(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
    }

    // 向量归一化
    public Vector3D normalize() {
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        if (magnitude < 1e-10) {
            return new Vector3D(0, 0, 0);
        }
        return new Vector3D(x / magnitude, y / magnitude, z / magnitude);
    }

    // 向量乘以标量
    public Vector3D multiply(double scalar) {
        return new Vector3D(x * scalar, y * scalar, z * scalar);
    }

    // 向量叉乘
    public static Vector3D cross(Vector3D v1, Vector3D v2) {
        return new Vector3D(
                v1.y * v2.z - v1.z * v2.y,
                v1.z * v2.x - v1.x * v2.z,
                v1.x * v2.y - v1.y * v2.x
        );
    }

    // 向量模长
    public double norm() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    // 向量点乘
    public static double dot(Vector3D v1, Vector3D v2) {
        return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z;
    }

    @Override
    public String toString() {
        return String.format("(%.10f, %.10f, %.10f)", x, y, z);
    }
}