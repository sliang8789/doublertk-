package com.example.doublertk.filter;

/**
 * 卡尔曼滤波器
 * 用于GNSS定位数据的滤波和融合
 * 
 * 状态向量: [x, y, vx, vy] (位置和速度)
 * 观测向量: [x, y] (位置)
 * 
 * 状态转移方程: X(k) = F * X(k-1) + w
 * 观测方程: Z(k) = H * X(k) + v
 * 
 * 其中:
 * - F: 状态转移矩阵
 * - H: 观测矩阵
 * - Q: 过程噪声协方差
 * - R: 观测噪声协方差
 * - P: 状态估计协方差
 */
public class KalmanFilter {
    
    // 状态维度
    private static final int STATE_DIM = 4;  // [x, y, vx, vy]
    private static final int MEAS_DIM = 2;   // [x, y]
    
    // 状态向量 [x, y, vx, vy]
    private double[] state;
    
    // 状态协方差矩阵 P (4x4)
    private double[][] P;
    
    // 状态转移矩阵 F (4x4)
    private double[][] F;
    
    // 观测矩阵 H (2x4)
    private double[][] H;
    
    // 过程噪声协方差 Q (4x4)
    private double[][] Q;
    
    // 观测噪声协方差 R (2x2)
    private double[][] R;
    
    // 单位矩阵 I (4x4)
    private double[][] I;
    
    // 时间步长（秒）
    private double dt = 0.1;
    
    // 过程噪声标准差
    private double processNoiseStd = 0.5;  // m/s²
    
    // 观测噪声标准差
    private double measurementNoiseStd = 2.0;  // m (RTK精度)
    
    // 是否已初始化
    private boolean initialized = false;
    
    // 上次更新时间
    private long lastUpdateTime = 0;
    
    public KalmanFilter() {
        initializeMatrices();
    }
    
    public KalmanFilter(double processNoiseStd, double measurementNoiseStd) {
        this.processNoiseStd = processNoiseStd;
        this.measurementNoiseStd = measurementNoiseStd;
        initializeMatrices();
    }
    
    private void initializeMatrices() {
        // 初始化状态向量
        state = new double[STATE_DIM];
        
        // 初始化状态协方差矩阵（初始不确定性较大）
        P = new double[STATE_DIM][STATE_DIM];
        for (int i = 0; i < STATE_DIM; i++) {
            P[i][i] = 1000.0;  // 初始方差
        }
        
        // 初始化状态转移矩阵
        F = new double[STATE_DIM][STATE_DIM];
        updateStateTransitionMatrix(dt);
        
        // 初始化观测矩阵 H = [1 0 0 0; 0 1 0 0]
        H = new double[MEAS_DIM][STATE_DIM];
        H[0][0] = 1;
        H[1][1] = 1;
        
        // 初始化过程噪声协方差
        Q = new double[STATE_DIM][STATE_DIM];
        updateProcessNoiseMatrix(dt);
        
        // 初始化观测噪声协方差
        R = new double[MEAS_DIM][MEAS_DIM];
        R[0][0] = measurementNoiseStd * measurementNoiseStd;
        R[1][1] = measurementNoiseStd * measurementNoiseStd;
        
        // 初始化单位矩阵
        I = new double[STATE_DIM][STATE_DIM];
        for (int i = 0; i < STATE_DIM; i++) {
            I[i][i] = 1;
        }
    }
    
    /**
     * 更新状态转移矩阵
     * F = [1 0 dt 0 ]
     *     [0 1 0  dt]
     *     [0 0 1  0 ]
     *     [0 0 0  1 ]
     */
    private void updateStateTransitionMatrix(double dt) {
        for (int i = 0; i < STATE_DIM; i++) {
            for (int j = 0; j < STATE_DIM; j++) {
                F[i][j] = (i == j) ? 1 : 0;
            }
        }
        F[0][2] = dt;  // x += vx * dt
        F[1][3] = dt;  // y += vy * dt
    }
    
    /**
     * 更新过程噪声协方差矩阵
     * 使用离散白噪声加速度模型
     */
    private void updateProcessNoiseMatrix(double dt) {
        double dt2 = dt * dt;
        double dt3 = dt2 * dt;
        double dt4 = dt3 * dt;
        double q = processNoiseStd * processNoiseStd;
        
        // Q矩阵（离散白噪声加速度模型）
        Q[0][0] = dt4 / 4 * q;
        Q[0][2] = dt3 / 2 * q;
        Q[1][1] = dt4 / 4 * q;
        Q[1][3] = dt3 / 2 * q;
        Q[2][0] = dt3 / 2 * q;
        Q[2][2] = dt2 * q;
        Q[3][1] = dt3 / 2 * q;
        Q[3][3] = dt2 * q;
    }
    
    /**
     * 初始化滤波器状态
     * @param x 初始X坐标
     * @param y 初始Y坐标
     */
    public void initialize(double x, double y) {
        state[0] = x;
        state[1] = y;
        state[2] = 0;  // 初始速度为0
        state[3] = 0;
        
        // 重置协方差
        for (int i = 0; i < STATE_DIM; i++) {
            for (int j = 0; j < STATE_DIM; j++) {
                P[i][j] = (i == j) ? 100.0 : 0;
            }
        }
        
        initialized = true;
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 预测步骤
     * @param dt 时间步长（秒）
     */
    public void predict(double dt) {
        if (!initialized) return;
        
        this.dt = dt;
        updateStateTransitionMatrix(dt);
        updateProcessNoiseMatrix(dt);
        
        // 状态预测: x = F * x
        double[] newState = matrixVectorMultiply(F, state);
        state = newState;
        
        // 协方差预测: P = F * P * F' + Q
        double[][] FP = matrixMultiply(F, P);
        double[][] FT = transpose(F);
        double[][] FPFT = matrixMultiply(FP, FT);
        P = matrixAdd(FPFT, Q);
    }
    
    /**
     * 更新步骤
     * @param measX 观测X坐标
     * @param measY 观测Y坐标
     */
    public void update(double measX, double measY) {
        if (!initialized) {
            initialize(measX, measY);
            return;
        }
        
        double[] measurement = new double[]{measX, measY};
        
        // 计算残差: y = z - H * x
        double[] Hx = matrixVectorMultiply(H, state);
        double[] residual = vectorSubtract(measurement, Hx);
        
        // 计算残差协方差: S = H * P * H' + R
        double[][] HP = matrixMultiply(H, P);
        double[][] HT = transpose(H);
        double[][] HPHT = matrixMultiply(HP, HT);
        double[][] S = matrixAdd(HPHT, R);
        
        // 计算卡尔曼增益: K = P * H' * S^(-1)
        double[][] PHT = matrixMultiply(P, HT);
        double[][] SInv = inverse2x2(S);
        double[][] K = matrixMultiply(PHT, SInv);
        
        // 状态更新: x = x + K * y
        double[] Ky = matrixVectorMultiply(K, residual);
        state = vectorAdd(state, Ky);
        
        // 协方差更新: P = (I - K * H) * P
        double[][] KH = matrixMultiply(K, H);
        double[][] IKH = matrixSubtract(I, KH);
        P = matrixMultiply(IKH, P);
        
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 执行完整的预测-更新循环
     * @param measX 观测X坐标
     * @param measY 观测Y坐标
     * @param timestamp 时间戳（毫秒）
     */
    public void process(double measX, double measY, long timestamp) {
        if (!initialized) {
            initialize(measX, measY);
            lastUpdateTime = timestamp;
            return;
        }
        
        // 计算时间步长
        double dt = (timestamp - lastUpdateTime) / 1000.0;
        if (dt <= 0) dt = 0.1;
        if (dt > 5.0) dt = 5.0;  // 限制最大时间步长
        
        // 预测
        predict(dt);
        
        // 更新
        update(measX, measY);
        
        lastUpdateTime = timestamp;
    }
    
    /**
     * 根据观测质量调整观测噪声
     * @param quality 质量因子（0-1，1为最好）
     */
    public void setMeasurementQuality(double quality) {
        quality = Math.max(0.1, Math.min(1.0, quality));
        double noise = measurementNoiseStd / quality;
        R[0][0] = noise * noise;
        R[1][1] = noise * noise;
    }
    
    // ========== 矩阵运算辅助方法 ==========
    
    private double[] matrixVectorMultiply(double[][] A, double[] x) {
        int rows = A.length;
        int cols = A[0].length;
        double[] result = new double[rows];
        for (int i = 0; i < rows; i++) {
            result[i] = 0;
            for (int j = 0; j < cols; j++) {
                result[i] += A[i][j] * x[j];
            }
        }
        return result;
    }
    
    private double[][] matrixMultiply(double[][] A, double[][] B) {
        int rowsA = A.length;
        int colsA = A[0].length;
        int colsB = B[0].length;
        double[][] result = new double[rowsA][colsB];
        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsB; j++) {
                result[i][j] = 0;
                for (int k = 0; k < colsA; k++) {
                    result[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return result;
    }
    
    private double[][] transpose(double[][] A) {
        int rows = A.length;
        int cols = A[0].length;
        double[][] result = new double[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[j][i] = A[i][j];
            }
        }
        return result;
    }
    
    private double[][] matrixAdd(double[][] A, double[][] B) {
        int rows = A.length;
        int cols = A[0].length;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = A[i][j] + B[i][j];
            }
        }
        return result;
    }
    
    private double[][] matrixSubtract(double[][] A, double[][] B) {
        int rows = A.length;
        int cols = A[0].length;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = A[i][j] - B[i][j];
            }
        }
        return result;
    }
    
    private double[] vectorAdd(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
        return result;
    }
    
    private double[] vectorSubtract(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] - b[i];
        }
        return result;
    }
    
    private double[][] inverse2x2(double[][] A) {
        double det = A[0][0] * A[1][1] - A[0][1] * A[1][0];
        if (Math.abs(det) < 1e-10) {
            det = 1e-10;
        }
        double[][] result = new double[2][2];
        result[0][0] = A[1][1] / det;
        result[0][1] = -A[0][1] / det;
        result[1][0] = -A[1][0] / det;
        result[1][1] = A[0][0] / det;
        return result;
    }
    
    // ========== Getters ==========
    
    public double getX() {
        return state[0];
    }
    
    public double getY() {
        return state[1];
    }
    
    public double getVx() {
        return state[2];
    }
    
    public double getVy() {
        return state[3];
    }
    
    public double getSpeed() {
        return Math.sqrt(state[2] * state[2] + state[3] * state[3]);
    }
    
    public double getCourse() {
        double course = Math.toDegrees(Math.atan2(state[2], state[3]));
        if (course < 0) course += 360;
        return course;
    }
    
    public double[] getState() {
        return state.clone();
    }
    
    public double getPositionVariance() {
        return Math.sqrt(P[0][0] + P[1][1]);
    }
    
    public double getVelocityVariance() {
        return Math.sqrt(P[2][2] + P[3][3]);
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public void reset() {
        initialized = false;
        state = new double[STATE_DIM];
        initializeMatrices();
    }
    
    public void setProcessNoiseStd(double std) {
        this.processNoiseStd = std;
        updateProcessNoiseMatrix(dt);
    }
    
    public void setMeasurementNoiseStd(double std) {
        this.measurementNoiseStd = std;
        R[0][0] = std * std;
        R[1][1] = std * std;
    }
}
