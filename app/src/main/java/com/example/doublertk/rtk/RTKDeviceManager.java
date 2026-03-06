package com.example.doublertk.rtk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * RTK设备管理器
 * 管理双RTK天线设备的连接和数据处理
 * 
 * 功能:
 * 1. 管理船头和船尾两个RTK设备的连接
 * 2. 同步两个设备的定位数据
 * 3. 计算双天线航向
 * 4. 提供统一的数据接口
 */
public class RTKDeviceManager {
    
    private static final String TAG = "RTKDeviceManager";
    
    // 设备标识
    public static final int DEVICE_BOW = 0;    // 船头RTK
    public static final int DEVICE_STERN = 1;  // 船尾RTK
    
    // 双RTK状态
    public enum DualRTKState {
        DISCONNECTED,       // 未连接
        PARTIAL_CONNECTED,  // 部分连接（只有一个设备连接）
        CONNECTED,          // 全部连接
        RTK_FLOAT,          // RTK浮点解
        RTK_FIXED           // RTK固定解
    }
    
    // 双RTK数据监听器
    public interface DualRTKListener {
        void onDualPositionUpdate(RTKPosition bowPosition, RTKPosition sternPosition, double heading);
        void onStateChanged(DualRTKState state);
        void onError(int device, String message);
    }
    
    private Context context;
    private Handler mainHandler;
    
    // 两个RTK接收器
    private RTKDataReceiver bowReceiver;
    private RTKDataReceiver sternReceiver;
    
    // 当前位置数据
    private RTKPosition bowPosition;
    private RTKPosition sternPosition;
    private double currentHeading = 0;
    
    // 天线基线长度（米）
    private double antennaBaseline = 10.0;
    
    // 监听器
    private DualRTKListener listener;
    
    // 状态
    private DualRTKState currentState = DualRTKState.DISCONNECTED;
    
    // 数据同步
    private long lastBowUpdateTime = 0;
    private long lastSternUpdateTime = 0;
    private static final long SYNC_TIMEOUT_MS = 500; // 数据同步超时
    
    public RTKDeviceManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化接收器
        bowReceiver = new RTKDataReceiver();
        sternReceiver = new RTKDataReceiver();
        
        // 设置监听器
        setupReceiverListeners();
    }
    
    private void setupReceiverListeners() {
        // 船头RTK监听器
        bowReceiver.setListener(new RTKDataReceiver.RTKDataListener() {
            @Override
            public void onPositionUpdate(RTKPosition position) {
                bowPosition = position;
                lastBowUpdateTime = System.currentTimeMillis();
                checkAndNotifyUpdate();
            }
            
            @Override
            public void onConnectionStateChanged(RTKDataReceiver.ConnectionState state) {
                updateDualState();
            }
            
            @Override
            public void onError(String message) {
                if (listener != null) {
                    listener.onError(DEVICE_BOW, message);
                }
            }
            
            @Override
            public void onRawData(String nmea) {
                // 可用于调试
            }
        });
        
        // 船尾RTK监听器
        sternReceiver.setListener(new RTKDataReceiver.RTKDataListener() {
            @Override
            public void onPositionUpdate(RTKPosition position) {
                sternPosition = position;
                lastSternUpdateTime = System.currentTimeMillis();
                checkAndNotifyUpdate();
            }
            
            @Override
            public void onConnectionStateChanged(RTKDataReceiver.ConnectionState state) {
                updateDualState();
            }
            
            @Override
            public void onError(String message) {
                if (listener != null) {
                    listener.onError(DEVICE_STERN, message);
                }
            }
            
            @Override
            public void onRawData(String nmea) {
                // 可用于调试
            }
        });
    }
    
    /**
     * 设置监听器
     */
    public void setListener(DualRTKListener listener) {
        this.listener = listener;
    }
    
    /**
     * 设置天线基线长度
     * @param baseline 基线长度（米）
     */
    public void setAntennaBaseline(double baseline) {
        this.antennaBaseline = baseline;
    }
    
    /**
     * 连接船头RTK设备
     * @param host 主机地址
     * @param port 端口号
     */
    public void connectBow(String host, int port) {
        bowReceiver.connect(host, port);
    }
    
    /**
     * 连接船尾RTK设备
     * @param host 主机地址
     * @param port 端口号
     */
    public void connectStern(String host, int port) {
        sternReceiver.connect(host, port);
    }
    
    /**
     * 连接双RTK设备
     * @param bowHost 船头设备地址
     * @param bowPort 船头设备端口
     * @param sternHost 船尾设备地址
     * @param sternPort 船尾设备端口
     */
    public void connectDual(String bowHost, int bowPort, String sternHost, int sternPort) {
        connectBow(bowHost, bowPort);
        connectStern(sternHost, sternPort);
    }
    
    /**
     * 断开所有连接
     */
    public void disconnect() {
        bowReceiver.disconnect();
        sternReceiver.disconnect();
    }
    
    /**
     * 检查数据同步并通知更新
     */
    private void checkAndNotifyUpdate() {
        if (bowPosition == null || sternPosition == null) {
            return;
        }
        
        long now = System.currentTimeMillis();
        
        // 检查数据是否同步（两个设备的数据时间差不超过阈值）
        if (Math.abs(lastBowUpdateTime - lastSternUpdateTime) > SYNC_TIMEOUT_MS) {
            return;
        }
        
        // 计算航向
        currentHeading = calculateHeading(bowPosition, sternPosition);
        
        // 通知更新
        if (listener != null) {
            listener.onDualPositionUpdate(bowPosition, sternPosition, currentHeading);
        }
    }
    
    /**
     * 计算双天线航向
     * @param bow 船头位置
     * @param stern 船尾位置
     * @return 航向角（度，0=北，顺时针）
     */
    private double calculateHeading(RTKPosition bow, RTKPosition stern) {
        // 将经纬度转换为平面坐标（简化计算，使用局部切平面）
        double latMid = (bow.getLatitude() + stern.getLatitude()) / 2;
        double cosLat = Math.cos(Math.toRadians(latMid));
        
        // 经度差转换为东向距离（米）
        double dx = (bow.getLongitude() - stern.getLongitude()) * 111319.9 * cosLat;
        // 纬度差转换为北向距离（米）
        double dy = (bow.getLatitude() - stern.getLatitude()) * 111319.9;
        
        // 计算航向角（从船尾指向船头）
        double heading = Math.toDegrees(Math.atan2(dx, dy));
        
        // 归一化到0-360度
        if (heading < 0) {
            heading += 360;
        }
        
        return heading;
    }
    
    /**
     * 更新双RTK状态
     */
    private void updateDualState() {
        boolean bowConnected = bowReceiver.isConnected();
        boolean sternConnected = sternReceiver.isConnected();
        
        DualRTKState newState;
        
        if (!bowConnected && !sternConnected) {
            newState = DualRTKState.DISCONNECTED;
        } else if (!bowConnected || !sternConnected) {
            newState = DualRTKState.PARTIAL_CONNECTED;
        } else {
            // 两个都连接了，检查定位质量
            if (bowPosition != null && sternPosition != null) {
                if (bowPosition.isRtkFixed() && sternPosition.isRtkFixed()) {
                    newState = DualRTKState.RTK_FIXED;
                } else if (bowPosition.isRtkSolution() && sternPosition.isRtkSolution()) {
                    newState = DualRTKState.RTK_FLOAT;
                } else {
                    newState = DualRTKState.CONNECTED;
                }
            } else {
                newState = DualRTKState.CONNECTED;
            }
        }
        
        if (currentState != newState) {
            currentState = newState;
            if (listener != null) {
                listener.onStateChanged(newState);
            }
        }
    }
    
    // Getters
    public RTKPosition getBowPosition() {
        return bowPosition;
    }
    
    public RTKPosition getSternPosition() {
        return sternPosition;
    }
    
    public double getCurrentHeading() {
        return currentHeading;
    }
    
    public DualRTKState getCurrentState() {
        return currentState;
    }
    
    public boolean isBowConnected() {
        return bowReceiver.isConnected();
    }
    
    public boolean isSternConnected() {
        return sternReceiver.isConnected();
    }
    
    public boolean isDualConnected() {
        return bowReceiver.isConnected() && sternReceiver.isConnected();
    }
    
    public boolean isRtkFixed() {
        return currentState == DualRTKState.RTK_FIXED;
    }
    
    /**
     * 获取船舶中心位置（两个RTK的中点）
     */
    public RTKPosition getCenterPosition() {
        if (bowPosition == null || sternPosition == null) {
            return bowPosition != null ? bowPosition : sternPosition;
        }
        
        RTKPosition center = new RTKPosition();
        center.setLatitude((bowPosition.getLatitude() + sternPosition.getLatitude()) / 2);
        center.setLongitude((bowPosition.getLongitude() + sternPosition.getLongitude()) / 2);
        center.setAltitude((bowPosition.getAltitude() + sternPosition.getAltitude()) / 2);
        
        // 使用较差的定位质量
        center.setFixQuality(Math.min(bowPosition.getFixQuality(), sternPosition.getFixQuality()));
        center.setSatellites(Math.min(bowPosition.getSatellites(), sternPosition.getSatellites()));
        center.setHdop(Math.max(bowPosition.getHdop(), sternPosition.getHdop()));
        
        return center;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        bowReceiver.release();
        sternReceiver.release();
    }
}
