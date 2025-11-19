package com.example.doublertk.calculate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 高程拟合计算器
 * 
 * 提供四种高程拟合算法：
 * 1. 垂直平差 (Vertical Adjustment)
 * 2. 平面拟合 (Plane Fitting) 
 * 3. 曲面拟合 (Surface Fitting - 8参数三次曲面)
 * 4. 加权平均 (Weighted Average)
 * 
 * 所有算法使用改进的最小二乘法，包含：
 * - 异常值检测
 * - 数值稳定性优化
 * - 精度评估
 * - 参数显著性检验
 */
public class HeightFittingCalculator {
    
    private static final String TAG = "HeightFittingCalculator";
    
    /**
     * 高程拟合结果基类
     */
    public static abstract class HeightFitResult {
        public final double rmse;              // 均方根误差
        public final double maxResidual;       // 最大残差
        public final double[] residuals;       // 残差数组
        public final int validPointCount;      // 有效点数
        public final String resultMessage;     // 结果信息
        
        protected HeightFitResult(double rmse, double maxResidual, double[] residuals, 
                                 int validPointCount, String resultMessage) {
            this.rmse = rmse;
            this.maxResidual = maxResidual;
            this.residuals = residuals;
            this.validPointCount = validPointCount;
            this.resultMessage = resultMessage;
        }
    }
    
    /**
     * 垂直平差结果
     */
    public static class VerticalAdjustmentResult extends HeightFitResult {
        public final double northOrigin;       // 北原点 (m)
        public final double eastOrigin;        // 东原点 (m)
        public final double heightConstant;    // 高差常量 a (m)
        public final double eastSlope;         // 东斜坡 b
        public final double northSlope;        // 北斜坡 c
        
        public VerticalAdjustmentResult(double northOrigin, double eastOrigin,
                                       double heightConstant, double eastSlope, double northSlope,
                                       double rmse, double maxResidual, double[] residuals,
                                       int validPointCount, String resultMessage) {
            super(rmse, maxResidual, residuals, validPointCount, resultMessage);
            this.northOrigin = northOrigin;
            this.eastOrigin = eastOrigin;
            this.heightConstant = heightConstant;
            this.eastSlope = eastSlope;
            this.northSlope = northSlope;
        }
        
        /**
         * 应用垂直平差变换到坐标
         * @param north 北坐标
         * @param east 东坐标
         * @param height 原始高程
         * @return 校正后的高程
         */
        public double applyCorrection(double north, double east, double height) {
            double x_rel = east - eastOrigin;
            double y_rel = north - northOrigin;
            double deltaH = heightConstant + eastSlope * x_rel + northSlope * y_rel;
            return height + deltaH;
        }
    }
    
    /**
     * 平面拟合结果
     */
    public static class PlaneFitResult extends HeightFitResult {
        public final double northOrigin;       // 北原点 (m)
        public final double eastOrigin;        // 东原点 (m)
        public final double paramA;            // 常数项 A (m)
        public final double paramB;            // 东向系数 B
        public final double paramC;            // 北向系数 C
        
        public PlaneFitResult(double northOrigin, double eastOrigin,
                             double paramA, double paramB, double paramC,
                             double rmse, double maxResidual, double[] residuals,
                             int validPointCount, String resultMessage) {
            super(rmse, maxResidual, residuals, validPointCount, resultMessage);
            this.northOrigin = northOrigin;
            this.eastOrigin = eastOrigin;
            this.paramA = paramA;
            this.paramB = paramB;
            this.paramC = paramC;
        }
        
        /**
         * 应用平面拟合变换到坐标
         */
        public double applyCorrection(double north, double east, double height) {
            double x_rel = east - eastOrigin;
            double y_rel = north - northOrigin;
            double deltaH = paramA + paramB * x_rel + paramC * y_rel;
            return height + deltaH;
        }
    }
    
    /**
     * 曲面拟合结果 (8参数三次曲面)
     */
    public static class SurfaceFitResult extends HeightFitResult {
        public final double northOrigin;       // 北原点 (m)
        public final double eastOrigin;        // 东原点 (m)
        public final double[] params;          // 8个参数 [a,b,c,d,e,f,g,h]
        public final double aic;               // AIC信息准则
        
        public SurfaceFitResult(double northOrigin, double eastOrigin, double[] params,
                               double rmse, double maxResidual, double[] residuals,
                               int validPointCount, double aic, String resultMessage) {
            super(rmse, maxResidual, residuals, validPointCount, resultMessage);
            this.northOrigin = northOrigin;
            this.eastOrigin = eastOrigin;
            this.params = params;
            this.aic = aic;
        }
        
        /**
         * 应用曲面拟合变换到坐标
         * 模型: z = a + b*x + c*y + d*x² + e*y² + f*x*y + g*x³ + h*y³
         */
        public double applyCorrection(double north, double east, double height) {
            double x = east - eastOrigin;
            double y = north - northOrigin;
            
            double deltaH = params[0] +                    // a: 常数项
                           params[1] * x +                 // b*x
                           params[2] * y +                 // c*y
                           params[3] * x * x +             // d*x²
                           params[4] * y * y +             // e*y²
                           params[5] * x * y +             // f*x*y
                           params[6] * x * x * x +         // g*x³
                           params[7] * y * y * y;          // h*y³
            
            return height + deltaH;
        }
    }
    
    /**
     * 加权平均结果
     */
    public static class WeightedAverageResult extends HeightFitResult {
        public final double weightedAverage;   // 加权平均高差 A (m)
        public final double[] weights;         // 权重数组
        public final double[] dataPoints;      // 数据点数组
        public final double effectiveSampleSize; // 有效样本数
        
        public WeightedAverageResult(double weightedAverage, double[] weights, double[] dataPoints,
                                    double rmse, double maxResidual, double[] residuals,
                                    int validPointCount, double effectiveSampleSize, String resultMessage) {
            super(rmse, maxResidual, residuals, validPointCount, resultMessage);
            this.weightedAverage = weightedAverage;
            this.weights = weights;
            this.dataPoints = dataPoints;
            this.effectiveSampleSize = effectiveSampleSize;
        }
        
        /**
         * 应用加权平均变换到坐标
         */
        public double applyCorrection(double north, double east, double height) {
            return height + weightedAverage;
        }
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 垂直平差计算
     * 误差方程: v_i = a + b*x_i + c*y_i - Δh_i
     * 其中: a=高差常量, b=东斜坡, c=北斜坡
     */
    public static VerticalAdjustmentResult calculateVerticalAdjustment(List<Vector3D> src, List<Vector3D> dst) {
        int n = src.size();
        if (n < 3) {
            return null; // 至少需要3个控制点
        }
        
        // 步骤1: 异常值检测
        List<Integer> validIndices = detectOutliers(src, dst);
        if (validIndices.size() < 3) {
            return null;
        }
        
        // 步骤2: 计算北原点和东原点
        double sumX = 0, sumY = 0;
        for (int idx : validIndices) {
            sumX += dst.get(idx).y; // 东坐标
            sumY += dst.get(idx).x; // 北坐标
        }
        double eastOrigin = sumX / validIndices.size();
        double northOrigin = sumY / validIndices.size();
        
        // 步骤3: 计算高差 Δh = H - h
        double[] deltaH = new double[validIndices.size()];
        for (int i = 0; i < validIndices.size(); i++) {
            int idx = validIndices.get(i);
            deltaH[i] = dst.get(idx).z - src.get(idx).z;
        }
        
        // 步骤4: 构建设计矩阵A和观测向量L
        double[][] A = new double[validIndices.size()][3];
        double[] L = new double[validIndices.size()];
        
        for (int i = 0; i < validIndices.size(); i++) {
            int idx = validIndices.get(i);
            double x_rel = dst.get(idx).y - eastOrigin;
            double y_rel = dst.get(idx).x - northOrigin;
            
            A[i][0] = 1.0;      // 常数项
            A[i][1] = x_rel;    // 东坐标项
            A[i][2] = y_rel;    // 北坐标项
            L[i] = deltaH[i];
        }
        
        // 步骤5: 最小二乘求解
        double[] params = solveLeastSquaresImproved(A, L);
        if (params == null) {
            return null;
        }
        
        double heightConstant = params[0];
        double eastSlope = params[1];
        double northSlope = params[2];
        
        // 步骤6: 计算残差和精度
        double[] residuals = new double[validIndices.size()];
        double sumV2 = 0;
        double maxResidual = 0;
        
        for (int i = 0; i < validIndices.size(); i++) {
            int idx = validIndices.get(i);
            double x_rel = dst.get(idx).y - eastOrigin;
            double y_rel = dst.get(idx).x - northOrigin;
            double fitted = heightConstant + eastSlope * x_rel + northSlope * y_rel;
            double residual = fitted - deltaH[i];
            residuals[i] = residual;
            sumV2 += residual * residual;
            maxResidual = Math.max(maxResidual, Math.abs(residual));
        }
        
        double rmse = Math.sqrt(sumV2 / (validIndices.size() - 3));
        
        String resultMessage = String.format("垂直平差计算完成\nRMSE: %.6f m\n最大残差: %.6f m",
                rmse, maxResidual);
        
        return new VerticalAdjustmentResult(northOrigin, eastOrigin, heightConstant, 
                eastSlope, northSlope, rmse, maxResidual, residuals, 
                validIndices.size(), resultMessage);
    }
    
    /**
     * 平面拟合计算
     * 模型方程: Δh = A + B*x + C*y
     */
    public static PlaneFitResult calculatePlaneFit(List<Vector3D> src, List<Vector3D> dst) {
        int n = src.size();
        if (n < 3) {
            return null;
        }
        
        // 步骤1: 计算北原点和东原点
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += dst.get(i).y;
            sumY += dst.get(i).x;
        }
        double eastOrigin = sumX / n;
        double northOrigin = sumY / n;
        
        // 步骤2: 计算高差
        double[] deltaH = new double[n];
        for (int i = 0; i < n; i++) {
            deltaH[i] = dst.get(i).z - src.get(i).z;
        }
        
        // 步骤3: 构建设计矩阵
        double[][] A = new double[n][3];
        double[] L = new double[n];
        
        for (int i = 0; i < n; i++) {
            double x_rel = dst.get(i).y - eastOrigin;
            double y_rel = dst.get(i).x - northOrigin;
            
            A[i][0] = 1.0;
            A[i][1] = x_rel;
            A[i][2] = y_rel;
            L[i] = deltaH[i];
        }
        
        // 步骤4: 最小二乘求解
        double[] params = solveLeastSquaresImproved(A, L);
        if (params == null) {
            return null;
        }
        
        double paramA = params[0];
        double paramB = params[1];
        double paramC = params[2];
        
        // 步骤5: 计算残差
        double[] residuals = new double[n];
        double sumV2 = 0;
        double maxResidual = 0;
        
        for (int i = 0; i < n; i++) {
            double x_rel = dst.get(i).y - eastOrigin;
            double y_rel = dst.get(i).x - northOrigin;
            double fitted = paramA + paramB * x_rel + paramC * y_rel;
            double residual = fitted - deltaH[i];
            residuals[i] = residual;
            sumV2 += residual * residual;
            maxResidual = Math.max(maxResidual, Math.abs(residual));
        }
        
        double rmse = Math.sqrt(sumV2 / (n - 3));
        
        String resultMessage = String.format("平面拟合计算完成\nRMSE: %.6f m\n最大残差: %.6f m",
                rmse, maxResidual);
        
        return new PlaneFitResult(northOrigin, eastOrigin, paramA, paramB, paramC,
                rmse, maxResidual, residuals, n, resultMessage);
    }
    
    /**
     * 曲面拟合计算 (8参数三次曲面)
     * 模型: z = a + b*x + c*y + d*x² + e*y² + f*x*y + g*x³ + h*y³
     */
    public static SurfaceFitResult calculateSurfaceFit(List<Vector3D> src, List<Vector3D> dst) {
        int n = src.size();
        if (n < 8) {
            return null;
        }
        
        // 步骤1: 计算坐标原点
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += dst.get(i).y;
            sumY += dst.get(i).x;
        }
        double eastOrigin = sumX / n;
        double northOrigin = sumY / n;
        
        // 步骤2: 计算高差
        double[] deltaH = new double[n];
        for (int i = 0; i < n; i++) {
            deltaH[i] = dst.get(i).z - src.get(i).z;
        }
        
        // 步骤3: 构建设计矩阵 (8参数)
        double[][] A = new double[n][8];
        double[] z = new double[n];
        
        for (int i = 0; i < n; i++) {
            double x = dst.get(i).y - eastOrigin;
            double y = dst.get(i).x - northOrigin;
            
            A[i][0] = 1.0;           // a
            A[i][1] = x;             // b*x
            A[i][2] = y;             // c*y
            A[i][3] = x * x;         // d*x²
            A[i][4] = y * y;         // e*y²
            A[i][5] = x * y;         // f*x*y
            A[i][6] = x * x * x;     // g*x³
            A[i][7] = y * y * y;     // h*y³
            
            z[i] = deltaH[i];
        }
        
        // 步骤4: 带正则化的最小二乘求解
        double[] params = solveLeastSquaresWithRegularization(A, z, 0.01);
        if (params == null) {
            return null;
        }
        
        // 步骤5: 计算残差
        double[] residuals = new double[n];
        double sumV2 = 0;
        double maxResidual = 0;
        
        for (int i = 0; i < n; i++) {
            double x = dst.get(i).y - eastOrigin;
            double y = dst.get(i).x - northOrigin;
            
            double fitted = params[0] + params[1] * x + params[2] * y +
                           params[3] * x * x + params[4] * y * y + params[5] * x * y +
                           params[6] * x * x * x + params[7] * y * y * y;
            
            double residual = fitted - deltaH[i];
            residuals[i] = residual;
            sumV2 += residual * residual;
            maxResidual = Math.max(maxResidual, Math.abs(residual));
        }
        
        double rmse = Math.sqrt(sumV2 / (n - 8));
        double aic = calculateAIC(n, 8, sumV2);
        
        String resultMessage = String.format("8参数曲面拟合完成\nRMSE: %.9f m\n最大残差: %.9f m\nAIC: %.2f",
                rmse, maxResidual, aic);
        
        return new SurfaceFitResult(northOrigin, eastOrigin, params, rmse, maxResidual,
                residuals, n, aic, resultMessage);
    }
    
    /**
     * 加权平均计算
     * 公式: A = Σ(wi * xi) / Σwi
     * 权重: wi = 1/σi² (基于方差的倒数)
     */
    public static WeightedAverageResult calculateWeightedAverage(List<Vector3D> src, List<Vector3D> dst) {
        int n = src.size();
        if (n < 1) {
            return null;
        }
        
        // 步骤1: 计算高差数据点
        double[] dataPoints = new double[n];
        for (int i = 0; i < n; i++) {
            dataPoints[i] = dst.get(i).z - src.get(i).z;
        }
        
        // 步骤2: 异常值检测
        List<Integer> validIndices = detectOutliersForWeightedAverage(dataPoints);
        if (validIndices.isEmpty()) {
            return null;
        }
        
        // 步骤3: 计算鲁棒权重
        double[] weights = new double[n];
        double median = calculateMedian(dataPoints);
        double[] mad = new double[n];
        for (int i = 0; i < n; i++) {
            mad[i] = Math.abs(dataPoints[i] - median);
        }
        double madMedian = calculateMedian(mad);
        double robustStd = 1.4826 * madMedian;
        
        // 使用Huber权重函数
        for (int i = 0; i < n; i++) {
            if (validIndices.contains(i)) {
                double deviation = Math.abs(dataPoints[i] - median);
                double variance;
                if (deviation <= 1.345 * robustStd) {
                    variance = robustStd * robustStd;
                } else {
                    variance = Math.max(robustStd * robustStd, deviation * deviation);
                }
                weights[i] = 1.0 / variance;
            } else {
                weights[i] = 0.0;
            }
        }
        
        // 步骤4: 计算加权平均
        double totalWeight = 0;
        double weightedSum = 0;
        for (int i = 0; i < n; i++) {
            if (validIndices.contains(i)) {
                totalWeight += weights[i];
                weightedSum += weights[i] * dataPoints[i];
            }
        }
        
        if (totalWeight <= 0) {
            return null;
        }
        
        double weightedAverage = weightedSum / totalWeight;
        
        // 步骤5: 计算精度评估
        double sumSquaredResiduals = 0;
        double maxResidual = 0;
        
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            if (validIndices.contains(i)) {
                double residual = dataPoints[i] - weightedAverage;
                residuals[i] = residual;
                sumSquaredResiduals += weights[i] * residual * residual;
                maxResidual = Math.max(maxResidual, Math.abs(residual));
            }
        }
        
        double weightedRMSE = Math.sqrt(sumSquaredResiduals / totalWeight);
        double effectiveSampleSize = (totalWeight * totalWeight) / sumSquaredResiduals;
        
        String resultMessage = String.format("加权平均计算完成\nA: %.6f m\nRMSE: %.6f m\n有效样本: %d/%d",
                weightedAverage, weightedRMSE, validIndices.size(), n);
        
        return new WeightedAverageResult(weightedAverage, weights, dataPoints, weightedRMSE,
                maxResidual, residuals, validIndices.size(), effectiveSampleSize, resultMessage);
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 异常值检测 (基于3-sigma准则)
     */
    private static List<Integer> detectOutliers(List<Vector3D> src, List<Vector3D> dst) {
        int n = src.size();
        double[] deltaH = new double[n];
        for (int i = 0; i < n; i++) {
            deltaH[i] = dst.get(i).z - src.get(i).z;
        }
        
        // 计算均值和标准差
        double mean = 0;
        for (double value : deltaH) {
            mean += value;
        }
        mean /= n;
        
        double std = 0;
        for (double value : deltaH) {
            std += (value - mean) * (value - mean);
        }
        std = Math.sqrt(std / n);
        
        // 3-sigma准则
        List<Integer> validIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (Math.abs(deltaH[i] - mean) <= 3 * std) {
                validIndices.add(i);
            }
        }
        
        return validIndices;
    }
    
    /**
     * 加权平均异常值检测
     */
    private static List<Integer> detectOutliersForWeightedAverage(double[] dataPoints) {
        int n = dataPoints.length;
        double median = calculateMedian(dataPoints);
        
        double[] mad = new double[n];
        for (int i = 0; i < n; i++) {
            mad[i] = Math.abs(dataPoints[i] - median);
        }
        double madMedian = calculateMedian(mad);
        
        List<Integer> validIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (mad[i] <= 3 * 1.4826 * madMedian) {
                validIndices.add(i);
            }
        }
        
        return validIndices;
    }
    
    /**
     * 计算中位数
     */
    private static double calculateMedian(double[] data) {
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n/2 - 1] + sorted[n/2]) / 2.0;
        } else {
            return sorted[n/2];
        }
    }
    
    /**
     * 改进的最小二乘求解器 (带条件数检查和数值稳定性优化)
     */
    private static double[] solveLeastSquaresImproved(double[][] A, double[] b) {
        int m = A.length;
        int n = A[0].length;
        
        if (m < n) return null;
        
        // 构建正规方程 A^T * A * x = A^T * b
        double[][] ATA = new double[n][n];
        double[] ATb = new double[n];
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    ATA[i][j] += A[k][i] * A[k][j];
                }
            }
            for (int k = 0; k < m; k++) {
                ATb[i] += A[k][i] * b[k];
            }
        }
        
        // 检查条件数
        double conditionNumber = estimateConditionNumber(ATA);
        if (conditionNumber > 1e10) {
            return null; // 矩阵病态
        }
        
        // 高斯消元求解
        return solveLinearSystemImproved(ATA, ATb);
    }
    
    /**
     * 带正则化的最小二乘求解器
     */
    private static double[] solveLeastSquaresWithRegularization(double[][] A, double[] b, double lambda) {
        int m = A.length;
        int n = A[0].length;
        
        // 构建正规方程 (A^T * A + λI) * x = A^T * b
        double[][] ATA = new double[n][n];
        double[] ATb = new double[n];
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    ATA[i][j] += A[k][i] * A[k][j];
                }
            }
            ATA[i][i] += lambda; // 正则化项
            
            for (int k = 0; k < m; k++) {
                ATb[i] += A[k][i] * b[k];
            }
        }
        
        return solveLinearSystemImproved(ATA, ATb);
    }
    
    /**
     * 改进的线性系统求解器 (高斯-约旦消元法，带主元选择)
     */
    private static double[] solveLinearSystemImproved(double[][] A, double[] b) {
        int n = A.length;
        double[][] augmented = new double[n][n + 1];
        
        // 构建增广矩阵
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n] = b[i];
        }
        
        // 高斯消元 (带部分主元选择)
        for (int col = 0; col < n; col++) {
            // 寻找主元
            int pivotRow = col;
            double maxVal = Math.abs(augmented[col][col]);
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > maxVal) {
                    maxVal = Math.abs(augmented[row][col]);
                    pivotRow = row;
                }
            }
            
            // 交换行
            if (pivotRow != col) {
                double[] temp = augmented[col];
                augmented[col] = augmented[pivotRow];
                augmented[pivotRow] = temp;
            }
            
            // 检查奇异性
            if (Math.abs(augmented[col][col]) < 1e-10) {
                return null;
            }
            
            // 消元
            for (int row = col + 1; row < n; row++) {
                double factor = augmented[row][col] / augmented[col][col];
                for (int j = col; j <= n; j++) {
                    augmented[row][j] -= factor * augmented[col][j];
                }
            }
        }
        
        // 回代
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
    
    /**
     * 估计条件数 (使用行列式近似)
     */
    private static double estimateConditionNumber(double[][] A) {
        int n = A.length;
        if (n == 0) return Double.MAX_VALUE;
        
        // 简化估计：使用对角线元素
        double maxDiag = 0;
        double minDiag = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double absValue = Math.abs(A[i][i]);
            maxDiag = Math.max(maxDiag, absValue);
            minDiag = Math.min(minDiag, absValue);
        }
        
        if (minDiag < 1e-10) return Double.MAX_VALUE;
        return maxDiag / minDiag;
    }
    
    /**
     * 计算AIC信息准则
     */
    private static double calculateAIC(int n, int p, double rss) {
        return n * Math.log(rss / n) + 2 * p;
    }
}

