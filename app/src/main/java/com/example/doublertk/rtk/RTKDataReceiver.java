package com.example.doublertk.rtk;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RTK数据接收器
 * 支持通过TCP/IP网络连接接收RTK设备的NMEA数据
 * 
 * 使用方式:
 * 1. 创建实例并设置监听器
 * 2. 调用connect()连接到RTK设备
 * 3. 在监听器中接收解析后的位置数据
 * 4. 调用disconnect()断开连接
 */
public class RTKDataReceiver {
    
    private static final String TAG = "RTKDataReceiver";
    
    // 连接状态
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    // 数据接收监听器
    public interface RTKDataListener {
        void onPositionUpdate(RTKPosition position);
        void onConnectionStateChanged(ConnectionState state);
        void onError(String message);
        void onRawData(String nmea);
    }
    
    private String host;
    private int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private BufferedReader reader;
    
    private ExecutorService executor;
    private Handler mainHandler;
    private NMEAParser nmeaParser;
    
    private RTKDataListener listener;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private AtomicBoolean running = new AtomicBoolean(false);
    
    // 重连配置
    private boolean autoReconnect = true;
    private int reconnectDelayMs = 3000;
    private int maxReconnectAttempts = 5;
    private int reconnectAttempts = 0;
    
    // 数据统计
    private long receivedBytes = 0;
    private long receivedMessages = 0;
    private long lastDataTime = 0;
    
    public RTKDataReceiver() {
        this.nmeaParser = new NMEAParser();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 设置数据监听器
     */
    public void setListener(RTKDataListener listener) {
        this.listener = listener;
    }
    
    /**
     * 连接到RTK设备
     * @param host 主机地址
     * @param port 端口号
     */
    public void connect(String host, int port) {
        this.host = host;
        this.port = port;
        this.reconnectAttempts = 0;
        
        executor.execute(this::doConnect);
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        running.set(false);
        autoReconnect = false;
        
        executor.execute(this::doDisconnect);
    }
    
    /**
     * 发送数据到RTK设备
     * @param data 要发送的数据
     */
    public void send(String data) {
        if (connectionState != ConnectionState.CONNECTED || outputStream == null) {
            return;
        }
        
        executor.execute(() -> {
            try {
                outputStream.write(data.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                notifyError("发送数据失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 发送RTCM差分数据
     * @param rtcmData RTCM数据字节
     */
    public void sendRTCM(byte[] rtcmData) {
        if (connectionState != ConnectionState.CONNECTED || outputStream == null) {
            return;
        }
        
        executor.execute(() -> {
            try {
                outputStream.write(rtcmData);
                outputStream.flush();
            } catch (IOException e) {
                notifyError("发送RTCM数据失败: " + e.getMessage());
            }
        });
    }
    
    private void doConnect() {
        if (connectionState == ConnectionState.CONNECTING) {
            return;
        }
        
        setConnectionState(ConnectionState.CONNECTING);
        
        try {
            // 关闭旧连接
            doDisconnect();
            
            // 创建新连接
            socket = new Socket(host, port);
            socket.setSoTimeout(10000); // 10秒超时
            socket.setKeepAlive(true);
            
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));
            
            running.set(true);
            reconnectAttempts = 0;
            setConnectionState(ConnectionState.CONNECTED);
            
            // 开始接收数据
            receiveLoop();
            
        } catch (IOException e) {
            setConnectionState(ConnectionState.ERROR);
            notifyError("连接失败: " + e.getMessage());
            
            // 尝试重连
            if (autoReconnect && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++;
                mainHandler.postDelayed(() -> executor.execute(this::doConnect), reconnectDelayMs);
            }
        }
    }
    
    private void doDisconnect() {
        running.set(false);
        
        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            // 忽略关闭错误
        }
        
        setConnectionState(ConnectionState.DISCONNECTED);
    }
    
    private void receiveLoop() {
        StringBuilder buffer = new StringBuilder();
        
        while (running.get()) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    // 连接断开
                    break;
                }
                
                receivedBytes += line.length();
                lastDataTime = System.currentTimeMillis();
                
                // 通知原始数据
                if (listener != null) {
                    final String rawData = line;
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onRawData(rawData);
                        }
                    });
                }
                
                // 解析NMEA语句
                if (line.startsWith("$")) {
                    RTKPosition position = nmeaParser.parse(line);
                    if (position != null && position.isValid()) {
                        receivedMessages++;
                        notifyPositionUpdate(position);
                    }
                }
                
            } catch (IOException e) {
                if (running.get()) {
                    notifyError("接收数据错误: " + e.getMessage());
                }
                break;
            }
        }
        
        // 连接断开
        if (running.get()) {
            setConnectionState(ConnectionState.ERROR);
            
            // 尝试重连
            if (autoReconnect && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++;
                mainHandler.postDelayed(() -> executor.execute(this::doConnect), reconnectDelayMs);
            }
        }
    }
    
    private void setConnectionState(ConnectionState state) {
        if (this.connectionState != state) {
            this.connectionState = state;
            
            if (listener != null) {
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionStateChanged(state);
                    }
                });
            }
        }
    }
    
    private void notifyPositionUpdate(RTKPosition position) {
        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onPositionUpdate(position);
                }
            });
        }
    }
    
    private void notifyError(String message) {
        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onError(message);
                }
            });
        }
    }
    
    // Getters
    public ConnectionState getConnectionState() {
        return connectionState;
    }
    
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
    
    public long getReceivedBytes() {
        return receivedBytes;
    }
    
    public long getReceivedMessages() {
        return receivedMessages;
    }
    
    public long getLastDataTime() {
        return lastDataTime;
    }
    
    // Setters
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
    
    public void setReconnectDelayMs(int delayMs) {
        this.reconnectDelayMs = delayMs;
    }
    
    public void setMaxReconnectAttempts(int maxAttempts) {
        this.maxReconnectAttempts = maxAttempts;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        disconnect();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
