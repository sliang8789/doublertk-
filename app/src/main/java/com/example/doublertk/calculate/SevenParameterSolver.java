package com.example.doublertk.calculate;

import com.example.doublertk.calculate.SevenParameterTransform;
import com.example.doublertk.calculate.Vector3D;

import java.util.List;
import java.util.ArrayList;

/**
 * 七参数(三维)求解器: 根据三维同名控制点计算7个转换参数。
 *
 * 新实现：基于线性化模型的高斯-牛顿最小二乘迭代（小角度），稳定可靠，支持任意>=3个控制点。
 */
public class SevenParameterSolver {

    private static final double PI = 3.1415926535897932;

    /** 控制点（目标/已知 <- 源/测量） */
    public static class ControlPoint {
        public double knownX, knownY, knownZ;      // 目标坐标系
        public double measuredX, measuredY, measuredZ; // 源坐标系

        public ControlPoint(double knownX, double knownY, double knownZ,
                            double measuredX, double measuredY, double measuredZ) {
            this.knownX = knownX;
            this.knownY = knownY;
            this.knownZ = knownZ;
            this.measuredX = measuredX;
            this.measuredY = measuredY;
            this.measuredZ = measuredZ;
        }

        @Override
        public String toString() {
            return String.format("测量点(%.6f, %.6f, %.6f) -> 已知点(%.3f, %.3f, %.3f)",
                    measuredX, measuredY, measuredZ, knownX, knownY, knownZ);
        }
    }

    /** 七参数结果 */
    public static class SevenParameterResult {
        public double scale;            // m（无量纲，通常很小）
        public double[] rotation;       // [wx, wy, wz] 弧度
        public double[] translation;    // [tx, ty, tz]
        public double rmse;             // 均方根误差
        public List<Double> residuals;  // 每点三维残差模长

        public SevenParameterResult(double scale, double[] rotation, double[] translation,
                                    double rmse, List<Double> residuals) {
            this.scale = scale;
            this.rotation = rotation;
            this.translation = translation;
            this.rmse = rmse;
            this.residuals = residuals;
        }

        @Override
        public String toString() {
            return String.format("七参数结果:\n  尺度参数: %.9f\n  旋转参数: (%.9f, %.9f, %.9f) 弧度\n  旋转参数: (%.6f, %.6f, %.6f) 度\n  平移参数: (%.6f, %.6f, %.6f)\n  均方根误差: %.6f",
                    scale,
                    rotation[0], rotation[1], rotation[2],
                    rotation[0] * 180 / PI, rotation[1] * 180 / PI, rotation[2] * 180 / PI,
                    translation[0], translation[1], translation[2], rmse);
        }
    }

    /** 简单矩阵（支持乘法与逆） */
    public static class SimpleMatrix {
        private final double[][] data;
        private final int rows, cols;

        public SimpleMatrix(int r, int c) {
            rows = r;
            cols = c;
            data = new double[r][c];
        }

        public double get(int i, int j) { return data[i][j]; }
        public void set(int i, int j, double v) { data[i][j] = v; }
        public int rows() { return rows; }
        public int cols() { return cols; }

        public SimpleMatrix transpose() {
            SimpleMatrix t = new SimpleMatrix(cols, rows);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    t.data[j][i] = data[i][j];
                }
            }
            return t;
        }

        public SimpleMatrix multiply(SimpleMatrix o) {
            if (cols != o.rows) throw new IllegalArgumentException("维度不匹配");
            SimpleMatrix r = new SimpleMatrix(rows, o.cols);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < o.cols; j++) {
                    double s = 0;
                    for (int k = 0; k < cols; k++) s += data[i][k] * o.data[k][j];
                    r.data[i][j] = s;
                }
            }
            return r;
        }

        public SimpleMatrix inverse() {
            if (rows != cols) throw new IllegalArgumentException("仅支持方阵求逆");
            int n = rows;
            SimpleMatrix aug = new SimpleMatrix(n, 2 * n);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) aug.data[i][j] = data[i][j];
                aug.data[i][i + n] = 1.0;
            }
            for (int i = 0; i < n; i++) {
                int max = i;
                for (int r = i + 1; r < n; r++) {
                    if (Math.abs(aug.data[r][i]) > Math.abs(aug.data[max][i])) max = r;
                }
                if (Math.abs(aug.data[max][i]) < 1e-18) throw new IllegalStateException("矩阵奇异");
                if (max != i) {
                    double[] tmp = aug.data[i];
                    aug.data[i] = aug.data[max];
                    aug.data[max] = tmp;
                }
                double pivot = aug.data[i][i];
                for (int j = 0; j < 2 * n; j++) aug.data[i][j] /= pivot;
                for (int r = 0; r < n; r++) {
                    if (r == i) continue;
                    double f = aug.data[r][i];
                    if (f == 0) continue;
                    for (int j = 0; j < 2 * n; j++) aug.data[r][j] -= f * aug.data[i][j];
                }
            }
            SimpleMatrix inv = new SimpleMatrix(n, n);
            for (int i = 0; i < n; i++) {
                System.arraycopy(aug.data[i], n, inv.data[i], 0, n);
            }
            return inv;
        }
    }

    /**
     * 高斯-牛顿迭代求解七参数
     */
    public static SevenParameterResult calculateSevenParameters(List<ControlPoint> controlPoints) {
        int n = controlPoints == null ? 0 : controlPoints.size();
        if (n < 3) throw new IllegalArgumentException("七参数求解至少需要3个控制点，当前为" + n);

        // 初值：m=0, wx=wy=wz=0，T为质心差
        double sx = 0, sy = 0, sz = 0, dx = 0, dy = 0, dz = 0;
        for (ControlPoint cp : controlPoints) {
            sx += cp.measuredX; sy += cp.measuredY; sz += cp.measuredZ;
            dx += cp.knownX;    dy += cp.knownY;    dz += cp.knownZ;
        }
        double sMeanX = sx / n, sMeanY = sy / n, sMeanZ = sz / n;
        double dMeanX = dx / n, dMeanY = dy / n, dMeanZ = dz / n;

        double m = 0.0, wx = 0.0, wy = 0.0, wz = 0.0;
        double tx = dMeanX - sMeanX;
        double ty = dMeanY - sMeanY;
        double tz = dMeanZ - sMeanZ;

        // 迭代
        for (int iter = 0; iter < 20; iter++) {
            SimpleMatrix B = new SimpleMatrix(3 * n, 7);
            SimpleMatrix L = new SimpleMatrix(3 * n, 1);

            int r = 0;
            double sumSq = 0;
            for (ControlPoint cp : controlPoints) {
                double X0 = cp.measuredX;
                double Y0 = cp.measuredY;
                double Z0 = cp.measuredZ;

                // 预测值（当前参数）
                double scale = 1.0 + m;
                double Xc = tx + scale * X0 - wz * Y0 + wy * Z0;
                double Yc = ty + wz * X0 + scale * Y0 - wx * Z0;
                double Zc = tz - wy * X0 + wx * Y0 + scale * Z0;

                // 观测-计算
                double vX = cp.knownX - Xc;
                double vY = cp.knownY - Yc;
                double vZ = cp.knownZ - Zc;
                L.set(r + 0, 0, vX);
                L.set(r + 1, 0, vY);
                L.set(r + 2, 0, vZ);

                // 雅可比（偏导）
                // X: dTx=1 dTy=0 dTz=0 dm=X0 dwx=0 dwy=Z0 dwz=-Y0
                B.set(r + 0, 0, 1);  B.set(r + 0, 1, 0);  B.set(r + 0, 2, 0);
                B.set(r + 0, 3, X0); B.set(r + 0, 4, 0);  B.set(r + 0, 5, Z0); B.set(r + 0, 6, -Y0);
                // Y: dTx=0 dTy=1 dTz=0 dm=Y0 dwx=-Z0 dwy=0 dwz=X0
                B.set(r + 1, 0, 0);  B.set(r + 1, 1, 1);  B.set(r + 1, 2, 0);
                B.set(r + 1, 3, Y0); B.set(r + 1, 4, -Z0);B.set(r + 1, 5, 0);  B.set(r + 1, 6, X0);
                // Z: dTx=0 dTy=0 dTz=1 dm=Z0 dwx=Y0 dwy=-X0 dwz=0
                B.set(r + 2, 0, 0);  B.set(r + 2, 1, 0);  B.set(r + 2, 2, 1);
                B.set(r + 2, 3, Z0); B.set(r + 2, 4, Y0); B.set(r + 2, 5, -X0);B.set(r + 2, 6, 0);

                r += 3;
                sumSq += vX * vX + vY * vY + vZ * vZ;
            }

            SimpleMatrix BT = B.transpose();
            SimpleMatrix Nn = BT.multiply(B);
            SimpleMatrix UU = BT.multiply(L);
            SimpleMatrix d = Nn.inverse().multiply(UU);

            double dTx = d.get(0, 0);
            double dTy = d.get(1, 0);
            double dTz = d.get(2, 0);
            double dM  = d.get(3, 0);
            double dWx = d.get(4, 0);
            double dWy = d.get(5, 0);
            double dWz = d.get(6, 0);

            tx += dTx; ty += dTy; tz += dTz;
            m  += dM;  wx += dWx; wy += dWy; wz += dWz;

            double maxDelta = Math.max(Math.max(Math.abs(dTx), Math.abs(dTy)), Math.max(Math.abs(dTz),
                    Math.max(Math.abs(dM), Math.max(Math.abs(dWx), Math.max(Math.abs(dWy), Math.abs(dWz))))));
            if (maxDelta < 1e-12) break;
        }

        double[] rotation = new double[]{wx, wy, wz};
        double[] translation = new double[]{tx, ty, tz};

        // 计算残差与RMSE
        List<Double> residuals = new ArrayList<>();
        double sumSquares = 0;
        for (ControlPoint cp : controlPoints) {
            double X0 = cp.measuredX, Y0 = cp.measuredY, Z0 = cp.measuredZ;
            double scale = 1.0 + m;
            double Xc = tx + scale * X0 - wz * Y0 + wy * Z0;
            double Yc = ty + wz * X0 + scale * Y0 - wx * Z0;
            double Zc = tz - wy * X0 + wx * Y0 + scale * Z0;
            double ex = cp.knownX - Xc;
            double ey = cp.knownY - Yc;
            double ez = cp.knownZ - Zc;
            double r = Math.sqrt(ex * ex + ey * ey + ez * ez);
            residuals.add(r);
            sumSquares += ex * ex + ey * ey + ez * ez;
        }
        double rmse = Math.sqrt(sumSquares / (3.0 * n));

        return new SevenParameterResult(m, rotation, translation, rmse, residuals);
    }

    /** 七参数正算（线性化形式） */
    public static void coordTrans7(double m, double[] R, double[] T, double[] X0, double[] X) {
        double wx = R[0], wy = R[1], wz = R[2];
        double tx = T[0], ty = T[1], tz = T[2];
        double scale = 1.0 + m;
        X[0] = tx + scale * X0[0] - wz * X0[1] + wy * X0[2];
        X[1] = ty + wz * X0[0] + scale * X0[1] - wx * X0[2];
        X[2] = tz - wy * X0[0] + wx * X0[1] + scale * X0[2];
    }

    /** 创建控制点便捷方法 */
    public static ControlPoint createControlPoint(double knownX, double knownY, double knownZ,
                                                  double measuredX, double measuredY, double measuredZ) {
        return new ControlPoint(knownX, knownY, knownZ, measuredX, measuredY, measuredZ);
    }

    // 兼容性方法
    public static SevenParameterTransform estimate(List<Vector3D> src, List<Vector3D> dst) {
        if (src == null || dst == null || src.size() != dst.size() || src.size() < 3) {
            throw new IllegalArgumentException("七参数求解至少需要三对同名点，且数量需一致");
        }
        List<ControlPoint> cps = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) {
            cps.add(new ControlPoint(dst.get(i).x, dst.get(i).y, dst.get(i).z,
                    src.get(i).x, src.get(i).y, src.get(i).z));
        }
        SevenParameterResult r = calculateSevenParameters(cps);
        return new SevenParameterTransform(r.translation[0], r.translation[1], r.translation[2],
                r.scale, r.rotation[0], r.rotation[1], r.rotation[2]);
    }

    /** 验证 */
    public static boolean validateTransform(List<Vector3D> src, List<Vector3D> dst,
                                            SevenParameterTransform transform, double tolerance) {
        if (src.size() != dst.size()) return false;
        for (int i = 0; i < src.size(); i++) {
            double[] t = transform.transform(src.get(i).x, src.get(i).y, src.get(i).z);
            double ex = Math.abs(t[0] - dst.get(i).x);
            double ey = Math.abs(t[1] - dst.get(i).y);
            double ez = Math.abs(t[2] - dst.get(i).z);
            if (ex > tolerance || ey > tolerance || ez > tolerance) return false;
        }
        return true;
    }

    /** RMSE */
    public static double calculateRMSE(List<Vector3D> src, List<Vector3D> dst,
                                       SevenParameterTransform transform) {
        if (src.size() != dst.size()) return Double.NaN;
        double sum = 0;
        for (int i = 0; i < src.size(); i++) {
            double[] t = transform.transform(src.get(i).x, src.get(i).y, src.get(i).z);
            double ex = t[0] - dst.get(i).x;
            double ey = t[1] - dst.get(i).y;
            double ez = t[2] - dst.get(i).z;
            sum += ex * ex + ey * ey + ez * ez;
        }
        return Math.sqrt(sum / src.size());
    }
}