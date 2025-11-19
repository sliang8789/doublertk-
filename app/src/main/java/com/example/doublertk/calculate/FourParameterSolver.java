package com.example.doublertk.calculate;

import java.util.List;

/**
 * 四参数(二维)求解器: 根据两组同名控制点计算四个转换参数。
 * 
 * 完全基于 FourParameterWithThreePoints.java 的实现，不做任何修改。
 * 使用最小二乘法和高斯-约当消元法求解四参数。
 */
public class FourParameterSolver {

    /**
     * 四参数转换类（完全按照 FourParameterWithThreePoints.java 中的实现）
     */
    public static class FourParameterTransform {
        private double deltaX, deltaY, scale, theta;

        /**
         * 构造函数：从源坐标数组和目标坐标数组计算四参数
         * @param src 源坐标系坐标数组 [x1, y1, x2, y2, ...]
         * @param dst 目标坐标系坐标数组 [X1, Y1, X2, Y2, ...]
         */
        public FourParameterTransform(double[] src, double[] dst) {
            int n = src.length / 2;
            if (n < 2) {
                throw new IllegalArgumentException("至少需要2个控制点");
            }
            
            // 调试日志：输出输入数据
            android.util.Log.d("FourParameterSolver", "=== 开始计算四参数 ===");
            android.util.Log.d("FourParameterSolver", "控制点数量: " + n);
            for (int i = 0; i < n; i++) {
                double x = src[2*i], y = src[2*i+1];
                double X = dst[2*i], Y = dst[2*i+1];
                android.util.Log.d("FourParameterSolver", String.format("点%d: 源坐标(地方平面)(%.10f, %.10f) -> 目标坐标(国家投影)(%.10f, %.10f)", 
                    i+1, x, y, X, Y));
            }
            
            double[][] A = new double[2*n][4];
            double[] L = new double[2*n];

            for (int i = 0; i < n; i++) {
                double x = src[2*i], y = src[2*i+1];
                double X = dst[2*i], Y = dst[2*i+1];

                A[2*i][0] = 1;   A[2*i][1] = 0;   A[2*i][2] = x;    A[2*i][3] = -y;   L[2*i] = X;
                A[2*i+1][0] = 0; A[2*i+1][1] = 1; A[2*i+1][2] = y;  A[2*i+1][3] = x;  L[2*i+1] = Y;
            }

            double[] p = solveLeastSquares(A, L);
            deltaX = p[0]; deltaY = p[1];
            double a = p[2], b = p[3];
            scale = Math.sqrt(a*a + b*b);
            theta = Math.atan2(b, a);
            
            // 调试日志：输出计算结果
            android.util.Log.d("FourParameterSolver", "=== 四参数计算结果 ===");
            android.util.Log.d("FourParameterSolver", String.format("deltaX = %.10f", deltaX));
            android.util.Log.d("FourParameterSolver", String.format("deltaY = %.10f", deltaY));
            android.util.Log.d("FourParameterSolver", String.format("a = %.10f", a));
            android.util.Log.d("FourParameterSolver", String.format("b = %.10f", b));
            android.util.Log.d("FourParameterSolver", String.format("scale = %.10f", scale));
            android.util.Log.d("FourParameterSolver", String.format("theta = %.10f 弧度 (%.10f 度)", theta, Math.toDegrees(theta)));
            
            // 验证：对每个控制点进行转换验证
            android.util.Log.d("FourParameterSolver", "=== 转换验证 ===");
            for (int i = 0; i < n; i++) {
                double x = src[2*i], y = src[2*i+1];
                double X_expected = dst[2*i], Y_expected = dst[2*i+1];
                double[] transformed = transform(x, y);
                double errorX = transformed[0] - X_expected;
                double errorY = transformed[1] - Y_expected;
                double error = Math.sqrt(errorX * errorX + errorY * errorY);
                android.util.Log.d("FourParameterSolver", String.format("点%d: 误差 = (%.10f, %.10f), 总误差 = %.10f", 
                    i+1, errorX, errorY, error));
            }
        }

        /**
         * 兼容性构造函数：从参数创建（支持传统参数格式）
         * @param dx 平移量 X
         * @param dy 平移量 Y
         * @param theta 旋转角（弧度）
         * @param k 尺度因子
         * @param useTraditional 是否使用传统参数格式
         */
        public FourParameterTransform(double dx, double dy, double theta, double k, boolean useTraditional) {
            if (useTraditional) {
                this.deltaX = dx;
                this.deltaY = dy;
                this.theta = theta;
                this.scale = k;
            } else {
                // 如果不使用传统格式，则从数组创建（这里不支持，需要提供数组）
                throw new IllegalArgumentException("useTraditional=false requires array constructor");
            }
        }

        /**
         * 坐标转换（完全按照 FourParameterWithThreePoints.java 中的 transformForward 方法）
         * @param x 源坐标系 X 坐标
         * @param y 源坐标系 Y 坐标
         * @return 转换后的坐标 [X, Y]
         */
        public double[] transform(double x, double y) {
            // 完全按照 FourParameterWithThreePoints.java 中的 transformForward 方法（逐字复制）
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double X = deltaX + scale * (x * cos - y * sin);
            double Y = deltaY + scale * (x * sin + y * cos);
            
            // 调试日志：验证转换过程
            android.util.Log.d("FourParameterTransform", String.format(
                "transform: 输入(%.6f, %.6f), theta=%.10f弧度(%.6f度), scale=%.10f, cos=%.10f, sin=%.10f, 输出(%.6f, %.6f)",
                x, y, theta, Math.toDegrees(theta), scale, cos, sin, X, Y));
            
            return new double[]{X, Y};
        }

        // Getter 方法
        public double getDeltaX() { return deltaX; }
        public double getDeltaY() { return deltaY; }
        public double getScale() { return scale; }
        public double getTheta() { return theta; }
        public double getThetaDegrees() { return Math.toDegrees(theta); }

        // 兼容性 getter（对应旧接口）
        public double getDx() { return deltaX; }
        public double getDy() { return deltaY; }
        public double getK() { return scale; }

        /**
         * 计算反向转换的参数
         * 如果当前参数是从 A 转换到 B，那么反向参数是从 B 转换到 A
         * 
         * 公式推导：
         * 正向：X = dx + k * (x * cos(θ) - y * sin(θ))
         *      Y = dy + k * (x * sin(θ) + y * cos(θ))
         * 
         * 反向：从 X, Y 计算 x, y
         * x * cos(θ) - y * sin(θ) = (X - dx) / k  ... (1)
         * x * sin(θ) + y * cos(θ) = (Y - dy) / k  ... (2)
         * 
         * 矩阵形式：[cos(θ)  -sin(θ)] [x]   [(X-dx)/k]
         *          [sin(θ)   cos(θ)] [y] = [(Y-dy)/k]
         * 
         * 旋转矩阵的逆（转置）：
         * [x]   [cos(θ)   sin(θ)] [(X-dx)/k]
         * [y] = [-sin(θ)  cos(θ)] [(Y-dy)/k]
         * 
         * 展开：
         * x = cos(θ)*(X-dx)/k + sin(θ)*(Y-dy)/k = (cos(θ)*X + sin(θ)*Y)/k - (dx*cos(θ) + dy*sin(θ))/k
         * y = -sin(θ)*(X-dx)/k + cos(θ)*(Y-dy)/k = (-sin(θ)*X + cos(θ)*Y)/k - (-dx*sin(θ) + dy*cos(θ))/k
         * 
         * 写成标准形式：x = dx' + k' * (X * cos(θ') - Y * sin(θ'))
         *                y = dy' + k' * (X * sin(θ') + Y * cos(θ'))
         * 
         * 其中：θ' = -θ, k' = 1/k
         *      dx' = -(dx * cos(θ) + dy * sin(θ)) / k
         *      dy' = (dx * sin(θ) - dy * cos(θ)) / k
         * 
         * @return 反向转换的四参数对象
         */
        public FourParameterTransform inverse() {
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double invK = 1.0 / scale;
            
            // 计算反向转换的参数
            double invDx = -(deltaX * cos + deltaY * sin) * invK;
            double invDy = (deltaX * sin - deltaY * cos) * invK;
            double invTheta = -theta;
            double invScale = invK;
            
            android.util.Log.d("FourParameterTransform", String.format(
                "反向转换参数计算: 正向(dx=%.6f, dy=%.6f, theta=%.6f°, k=%.10f) -> 反向(dx=%.6f, dy=%.6f, theta=%.6f°, k=%.10f)",
                deltaX, deltaY, Math.toDegrees(theta), scale,
                invDx, invDy, Math.toDegrees(invTheta), invScale));
            
            return new FourParameterTransform(invDx, invDy, invTheta, invScale, true);
        }

        private double[] solveLeastSquares(double[][] A, double[] b) {
            int rows = A.length, cols = 4;
            double[][] AtA = new double[cols][cols];
            double[] Atb = new double[cols];

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    Atb[j] += A[i][j] * b[i];
                    for (int k = 0; k < cols; k++) {
                        AtA[j][k] += A[i][j] * A[i][k];
                    }
                }
            }
            return gaussJordan(AtA, Atb);
        }

        private double[] gaussJordan(double[][] A, double[] b) {
            int n = b.length;
            double[][] aug = new double[n][n+1];
            for (int i = 0; i < n; i++) {
                System.arraycopy(A[i], 0, aug[i], 0, n);
                aug[i][n] = b[i];
            }
            for (int i = 0; i < n; i++) {
                int pivot = i;
                for (int j = i; j < n; j++)
                    if (Math.abs(aug[j][i]) > Math.abs(aug[pivot][i])) pivot = j;
                double[] tmp = aug[i]; aug[i] = aug[pivot]; aug[pivot] = tmp;

                if (Math.abs(aug[i][i]) < 1e-12) throw new RuntimeException("矩阵奇异");

                double div = aug[i][i];
                for (int j = 0; j <= n; j++) aug[i][j] /= div;

                for (int k = 0; k < n; k++) if (k != i) {
                    double factor = aug[k][i];
                    for (int j = 0; j <= n; j++) aug[k][j] -= factor * aug[i][j];
                }
            }
            double[] x = new double[n];
            for (int i = 0; i < n; i++) x[i] = aug[i][n];
            return x;
        }
    }

    /**
     * 从源坐标列表和目标坐标列表估计四参数
     * @param src 源坐标系坐标列表（地方平面坐标）
     * @param dst 目标坐标系坐标列表（国家投影坐标）
     * @return 四参数转换对象
     */
    public static FourParameterTransform estimate(List<Point2D> src, List<Point2D> dst) {
        if (src == null || dst == null || src.size() != dst.size() || src.size() < 2) {
            throw new IllegalArgumentException("四参数求解至少需要两对同名点，且数量需一致");
        }

        android.util.Log.d("FourParameterSolver", "=== estimate 方法调用 ===");
        android.util.Log.d("FourParameterSolver", "源点数量: " + src.size() + ", 目标点数量: " + dst.size());
        
        // 转换为数组格式
        double[] srcArray = new double[2 * src.size()];
        double[] dstArray = new double[2 * dst.size()];

        for (int i = 0; i < src.size(); i++) {
            srcArray[2*i] = src.get(i).x;
            srcArray[2*i+1] = src.get(i).y;
            dstArray[2*i] = dst.get(i).x;
            dstArray[2*i+1] = dst.get(i).y;
            
            android.util.Log.d("FourParameterSolver", String.format("输入点%d: 源坐标(地方平面)(%.10f, %.10f) -> 目标坐标(国家投影)(%.10f, %.10f)", 
                i+1, src.get(i).x, src.get(i).y, dst.get(i).x, dst.get(i).y));
        }

        return new FourParameterTransform(srcArray, dstArray);
    }

    /**
     * 计算变换的RMSE（均方根误差）
     * @param src 源坐标系坐标列表
     * @param dst 目标坐标系坐标列表
     * @param transform 四参数转换对象
     * @return RMSE值
     */
    public static double calculateRMSE(List<Point2D> src, List<Point2D> dst, FourParameterTransform transform) {
        if (src.size() != dst.size()) return Double.NaN;
        double sum = 0;
        for (int i = 0; i < src.size(); i++) {
            double[] t = transform.transform(src.get(i).x, src.get(i).y);
            double ex = t[0] - dst.get(i).x;
            double ey = t[1] - dst.get(i).y;
            sum += ex * ex + ey * ey;
        }
        return Math.sqrt(sum / src.size());
    }
}
