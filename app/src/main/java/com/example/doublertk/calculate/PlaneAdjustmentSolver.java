package com.example.doublertk.calculate;

import java.util.List;

/**
 * 平面平差求解器：使用最小二乘法从控制点数据中计算六个参数
 * 
 * 参数定义：
 * - Δx, Δy: 平移参数（北平移、东平移）
 * - μ: 比例尺参数
 * - θ: 旋转角参数
 * - ax, ay: 线性变形参数（对应北原点、东原点的影响）
 * 
 * 变换模型：
 * x' = Δx + μ(x*cos(θ) - y*sin(θ)) + ax*x
 * y' = Δy + μ(x*sin(θ) + y*cos(θ)) + ay*y
 * 
 * 误差方程：
 * vx_i = x'_i - (Δx + μ(x_i*cos(θ) - y_i*sin(θ)) + ax*x_i)
 * vy_i = y'_i - (Δy + μ(x_i*sin(θ) + y_i*cos(θ)) + ay*y_i)
 */
public class PlaneAdjustmentSolver {

    /**
     * 使用最小二乘法估计平面平差的六个参数
     * 
     * @param src 源点坐标列表（测量坐标）
     * @param dst 目标点坐标列表（已知坐标）
     * @return 平面平差变换参数
     */
    public static PlaneAdjustmentTransform estimate(List<Point2D> src, List<Point2D> dst) {
        if (src == null || dst == null || src.size() != dst.size() || src.size() < 3) {
            throw new IllegalArgumentException("平面平差求解至少需要三对同名点，且数量需一致");
        }

        int n = src.size();
        
        // 步骤1: 计算北原点和东原点（控制点平均坐标）
        double northOrigin = 0, eastOrigin = 0;
        for (int i = 0; i < n; i++) {
            northOrigin += src.get(i).x; // x为北坐标
            eastOrigin += src.get(i).y;  // y为东坐标
        }
        northOrigin /= n;
        eastOrigin /= n;
        
        // 步骤2: 初始参数估计
        double deltaX = 0, deltaY = 0; // 平移参数
        double mu = 1.0;               // 比例尺参数
        double theta = 0.0;            // 旋转角参数
        double ax = 0.0, ay = 0.0;     // 线性变形参数
        
        // 初步估计平移参数
        double sumDx = 0, sumDy = 0;
        for (int i = 0; i < n; i++) {
            sumDx += dst.get(i).x - src.get(i).x;
            sumDy += dst.get(i).y - src.get(i).y;
        }
        deltaX = sumDx / n;
        deltaY = sumDy / n;
        
        // 步骤3: 迭代求解（高斯-牛顿法）
        int maxIterations = 100;
        double tolerance = 1e-10;
        
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // 构建设计矩阵A (2n × 6)
            double[][] A = new double[2 * n][6];
            double[] L = new double[2 * n]; // 观测向量
            
            for (int i = 0; i < n; i++) {
                double x = src.get(i).x;
                double y = src.get(i).y;
                double xObs = dst.get(i).x;
                double yObs = dst.get(i).y;
                
                double cosTheta = Math.cos(theta);
                double sinTheta = Math.sin(theta);
                
                // 第i个点的x方向误差方程
                int row1 = 2 * i;
                A[row1][0] = 1.0;                                    // ∂/∂Δx
                A[row1][1] = 0.0;                                    // ∂/∂Δy
                A[row1][2] = x * cosTheta - y * sinTheta;            // ∂/∂μ
                A[row1][3] = -mu * (x * sinTheta + y * cosTheta);    // ∂/∂θ
                A[row1][4] = x;                                      // ∂/∂ax
                A[row1][5] = 0.0;                                    // ∂/∂ay
                
                // 第i个点的y方向误差方程
                int row2 = 2 * i + 1;
                A[row2][0] = 0.0;                                    // ∂/∂Δx
                A[row2][1] = 1.0;                                    // ∂/∂Δy
                A[row2][2] = x * sinTheta + y * cosTheta;            // ∂/∂μ
                A[row2][3] = mu * (x * cosTheta - y * sinTheta);     // ∂/∂θ
                A[row2][4] = 0.0;                                    // ∂/∂ax
                A[row2][5] = y;                                      // ∂/∂ay
                
                // 观测值减去计算值
                double calcX = deltaX + mu * (x * cosTheta - y * sinTheta) + ax * x;
                double calcY = deltaY + mu * (x * sinTheta + y * cosTheta) + ay * y;
                
                L[row1] = xObs - calcX;
                L[row2] = yObs - calcY;
            }
            
            // 求解法方程: (A^T * A) * δβ = A^T * L
            double[][] AtA = matrixMultiply(transpose(A), A);
            double[] AtL = matrixVectorMultiply(transpose(A), L);
            
            // 解线性方程组
            double[] deltaParams = solveLinearSystem(AtA, AtL);
            
            // 更新参数
            deltaX += deltaParams[0];
            deltaY += deltaParams[1];
            mu += deltaParams[2];
            theta += deltaParams[3];
            ax += deltaParams[4];
            ay += deltaParams[5];
            
            // 检查收敛性
            double norm = 0;
            for (double dp : deltaParams) {
                norm += dp * dp;
            }
            norm = Math.sqrt(norm);
            
            if (norm < tolerance) {
                // 🔧 优化：移除控制台输出，改为日志
                android.util.Log.d("PlaneAdjustmentSolver", "平面平差收敛于第 " + (iteration + 1) + " 次迭代");
                break;
            }
        }
        
        // 计算精度指标
        double rmse = calculateRMSE(src, dst, deltaX, deltaY, mu, theta, ax, ay);
        
        return new PlaneAdjustmentTransform(northOrigin, eastOrigin, deltaX, deltaY, mu * Math.cos(theta), mu, rmse);
    }
    
    /**
     * 计算均方根误差
     */
    private static double calculateRMSE(List<Point2D> src, List<Point2D> dst, 
                                       double deltaX, double deltaY, double mu, double theta, double ax, double ay) {
        double sumSquaredErrors = 0;
        int n = src.size();
        
        for (int i = 0; i < n; i++) {
            double x = src.get(i).x;
            double y = src.get(i).y;
            double xObs = dst.get(i).x;
            double yObs = dst.get(i).y;
            
            double cosTheta = Math.cos(theta);
            double sinTheta = Math.sin(theta);
            
            double calcX = deltaX + mu * (x * cosTheta - y * sinTheta) + ax * x;
            double calcY = deltaY + mu * (x * sinTheta + y * cosTheta) + ay * y;
            
            double errorX = xObs - calcX;
            double errorY = yObs - calcY;
            
            sumSquaredErrors += errorX * errorX + errorY * errorY;
        }
        
        return Math.sqrt(sumSquaredErrors / (2 * n));
    }
    
    /**
     * 矩阵转置
     */
    private static double[][] transpose(double[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[][] result = new double[cols][rows];
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[j][i] = matrix[i][j];
            }
        }
        return result;
    }
    
    /**
     * 矩阵乘法
     */
    private static double[][] matrixMultiply(double[][] A, double[][] B) {
        int rowsA = A.length;
        int colsA = A[0].length;
        int colsB = B[0].length;
        
        double[][] result = new double[rowsA][colsB];
        
        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsB; j++) {
                for (int k = 0; k < colsA; k++) {
                    result[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return result;
    }
    
    /**
     * 矩阵向量乘法
     */
    private static double[] matrixVectorMultiply(double[][] A, double[] b) {
        int rows = A.length;
        int cols = A[0].length;
        double[] result = new double[rows];
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i] += A[i][j] * b[j];
            }
        }
        return result;
    }
    
    /**
     * 解线性方程组 Ax = b (使用高斯消元法)
     */
    private static double[] solveLinearSystem(double[][] A, double[] b) {
        int n = A.length;
        
        // 创建增广矩阵
        double[][] augmented = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, augmented[i], 0, n);
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
            double[] temp = augmented[i];
            augmented[i] = augmented[maxRow];
            augmented[maxRow] = temp;
            
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
            x[i] = augmented[i][n];
            for (int j = i + 1; j < n; j++) {
                x[i] -= augmented[i][j] * x[j];
            }
            x[i] /= augmented[i][i];
        }
        
        return x;
    }
}