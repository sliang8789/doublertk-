package com.example.doublertk.calculate;

import java.util.List;

/**
 * 三参数求解器: 根据同名控制点计算平移参数(dx, dy, dz)。
 * 支持二维和三维坐标转换
 * 使用公式：X₂ = X₁ + dx, Y₂ = Y₁ + dy, Z₂ = Z₁ + dz
 * 至少需要 1 组同名点，使用最小二乘法求解。
 */
public class ThreeParameterSolver {

    /**
     * 使用两组同名点求解二维三参数。
     * 公式：X₂ = X₁ + dx, Y₂ = Y₁ + dy
     * 使用最小二乘法求解：β̂ = (X^T X)^(-1) X^T Y
     * @param src 源坐标点列表 (x,y)
     * @param dst 目标坐标点列表 (X,Y)
     * @return ThreeParameterTransform
     */
    public static ThreeParameterTransform estimate(List<Point2D> src, List<Point2D> dst) {
        if (src == null || dst == null || src.size() != dst.size() || src.size() < 2) {
            throw new IllegalArgumentException("三参数求解至少需要两对同名点，且数量需一致");
        }

        int n = src.size();
        double sumDx = 0;
        double sumDy = 0;
        for (int i = 0; i < n; i++) {
            sumDx += (dst.get(i).x - src.get(i).x);
            sumDy += (dst.get(i).y - src.get(i).y);
        }
        double dx = sumDx / n;
        double dy = sumDy / n;

        double sumSquaredError = 0;
        for (int i = 0; i < n; i++) {
            double ex = (src.get(i).x + dx) - dst.get(i).x;
            double ey = (src.get(i).y + dy) - dst.get(i).y;
            sumSquaredError += ex * ex + ey * ey;
        }
        double rmse = Math.sqrt(sumSquaredError / (n * 2));

        ThreeParameterTransform transform = new ThreeParameterTransform();
        transform.setDx(dx);
        transform.setDy(dy);
        transform.setDz(0.0);
        transform.setRmse(rmse);
        return transform;
    }

    /**
     * 使用三维同名点求解三参数。
     * 公式：X₂ = X₁ + dx, Y₂ = Y₁ + dy, Z₂ = Z₁ + dz
     * 使用最小二乘法求解：β̂ = (X^T X)^(-1) X^T Y
     * @param src 源坐标点列表 (x,y,z)
     * @param dst 目标坐标点列表 (X,Y,Z)
     * @return ThreeParameterTransform
     */
    public static ThreeParameterTransform estimate3D(List<Vector3D> src, List<Vector3D> dst) {
        if (src == null || dst == null || src.size() != dst.size() || src.size() < 1) {
            throw new IllegalArgumentException("三维三参数求解至少需要一对同名点，且数量需一致");
        }
        int n = src.size();
        double sumDx = 0, sumDy = 0, sumDz = 0;
        for (int i = 0; i < n; i++) {
            sumDx += (dst.get(i).x - src.get(i).x);
            sumDy += (dst.get(i).y - src.get(i).y);
            sumDz += (dst.get(i).z - src.get(i).z);
        }
        double dx = sumDx / n;
        double dy = sumDy / n;
        double dz = sumDz / n;

        double sumSquaredError = 0;
        for (int i = 0; i < n; i++) {
            double ex = (src.get(i).x + dx) - dst.get(i).x;
            double ey = (src.get(i).y + dy) - dst.get(i).y;
            double ez = (src.get(i).z + dz) - dst.get(i).z;
            sumSquaredError += ex * ex + ey * ey + ez * ez;
        }
        double rmse = Math.sqrt(sumSquaredError / (n * 3));

        ThreeParameterTransform transform = new ThreeParameterTransform();
        transform.setDx(dx);
        transform.setDy(dy);
        transform.setDz(dz);
        transform.setRmse(rmse);
        return transform;
    }


    /**
     * 最小二乘法求解：β̂ = (X^T X)^(-1) X^T Y
     * @param X 设计矩阵
     * @param Y 观测值向量
     * @return 参数向量
     */
    private static double[] solveLeastSquares(double[][] X, double[] Y) {
        int m = X.length; // 观测数
        int n = X[0].length; // 参数数

        // 计算 X^T * X
        double[][] XTX = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    XTX[i][j] += X[k][i] * X[k][j];
                }
            }
        }

        // 计算 X^T * Y
        double[] XTY = new double[n];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < m; k++) {
                XTY[i] += X[k][i] * Y[k];
            }
        }

        // 求解线性方程组 (X^T X) * β = X^T Y
        return solveLinearSystem(XTX, XTY);
    }

    /**
     * 求解线性方程组 A * x = b
     * 使用高斯消元法
     * @param A 系数矩阵
     * @param b 常数向量
     * @return 解向量
     */
    private static double[] solveLinearSystem(double[][] A, double[] b) {
        int n = A.length;
        double[][] augmented = new double[n][n + 1];

        // 构建增广矩阵
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n] = b[i];
        }

        // 高斯消元
        for (int i = 0; i < n; i++) {
            // 寻找主元
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i])) {
                    maxRow = k;
                }
            }

            // 交换行
            if (maxRow != i) {
                double[] temp = augmented[i];
                augmented[i] = augmented[maxRow];
                augmented[maxRow] = temp;
            }

            // 消元
            for (int k = i + 1; k < n; k++) {
                double factor = augmented[k][i] / augmented[i][i];
                for (int j = i; j <= n; j++) {
                    augmented[k][j] -= factor * augmented[i][j];
                }
            }
        }

        // 回代求解
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0;
            for (int j = i + 1; j < n; j++) {
                sum += augmented[i][j] * x[j];
            }
            x[i] = (augmented[i][n] - sum) / augmented[i][i];
        }

        return x;
    }
}