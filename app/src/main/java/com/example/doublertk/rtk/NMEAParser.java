package com.example.doublertk.rtk;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * NMEA-0183协议解析器
 * 支持解析GPS/GNSS设备输出的标准NMEA语句
 * 
 * 支持的语句类型:
 * - GGA: 定位信息（经纬度、高程、定位质量、卫星数、HDOP）
 * - RMC: 推荐最小定位信息（经纬度、速度、航向、日期时间）
 * - GSA: 卫星状态（PDOP、HDOP、VDOP、定位模式）
 * - VTG: 地面速度信息（航向、速度）
 * - GLL: 地理位置信息
 * - GSV: 可见卫星信息
 */
public class NMEAParser {
    
    private static final String TAG = "NMEAParser";
    
    // 当前解析状态
    private RTKPosition currentPosition;
    private long lastGGATime = 0;
    private long lastRMCTime = 0;
    
    public NMEAParser() {
        currentPosition = new RTKPosition();
    }
    
    /**
     * 解析NMEA语句
     * @param sentence 完整的NMEA语句（包含$和校验和）
     * @return 解析后的RTKPosition，如果解析失败返回null
     */
    public RTKPosition parse(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return null;
        }
        
        // 去除首尾空白
        sentence = sentence.trim();
        
        // 验证语句格式
        if (!sentence.startsWith("$")) {
            return null;
        }
        
        // 验证校验和
        if (!verifyChecksum(sentence)) {
            return null;
        }
        
        // 移除校验和部分
        int checksumIndex = sentence.indexOf('*');
        if (checksumIndex > 0) {
            sentence = sentence.substring(0, checksumIndex);
        }
        
        // 移除$符号
        sentence = sentence.substring(1);
        
        // 分割字段
        String[] fields = sentence.split(",", -1);
        if (fields.length < 1) {
            return null;
        }
        
        // 获取语句类型（去除前缀如GP、GN、BD等）
        String type = fields[0];
        if (type.length() >= 3) {
            type = type.substring(type.length() - 3);
        }
        
        // 根据类型解析
        switch (type) {
            case "GGA":
                return parseGGA(fields);
            case "RMC":
                return parseRMC(fields);
            case "GSA":
                parseGSA(fields);
                return currentPosition;
            case "VTG":
                parseVTG(fields);
                return currentPosition;
            case "GLL":
                return parseGLL(fields);
            default:
                return null;
        }
    }
    
    /**
     * 解析GGA语句 - 全球定位系统定位数据
     * 格式: $GPGGA,hhmmss.ss,llll.ll,a,yyyyy.yy,a,x,xx,x.x,x.x,M,x.x,M,x.x,xxxx*hh
     * 
     * 字段说明:
     * 0: 语句类型 (GPGGA/GNGGA/BDGGA)
     * 1: UTC时间 (hhmmss.ss)
     * 2: 纬度 (ddmm.mmmm)
     * 3: 纬度方向 (N/S)
     * 4: 经度 (dddmm.mmmm)
     * 5: 经度方向 (E/W)
     * 6: 定位质量 (0=无效, 1=GPS, 2=DGPS, 4=RTK固定, 5=RTK浮点)
     * 7: 使用卫星数
     * 8: HDOP
     * 9: 海拔高度
     * 10: 高度单位 (M)
     * 11: 大地水准面高度
     * 12: 单位 (M)
     * 13: 差分数据龄期
     * 14: 差分基站ID
     */
    private RTKPosition parseGGA(String[] fields) {
        if (fields.length < 15) {
            return null;
        }
        
        try {
            RTKPosition pos = new RTKPosition();
            
            // 解析时间
            if (!fields[1].isEmpty()) {
                pos.setTimestamp(parseUTCTime(fields[1]));
            }
            
            // 解析纬度
            if (!fields[2].isEmpty() && !fields[3].isEmpty()) {
                double lat = parseLatitude(fields[2], fields[3]);
                pos.setLatitude(lat);
            }
            
            // 解析经度
            if (!fields[4].isEmpty() && !fields[5].isEmpty()) {
                double lon = parseLongitude(fields[4], fields[5]);
                pos.setLongitude(lon);
            }
            
            // 解析定位质量
            if (!fields[6].isEmpty()) {
                pos.setFixQuality(Integer.parseInt(fields[6]));
            }
            
            // 解析卫星数
            if (!fields[7].isEmpty()) {
                pos.setSatellites(Integer.parseInt(fields[7]));
            }
            
            // 解析HDOP
            if (!fields[8].isEmpty()) {
                pos.setHdop(Double.parseDouble(fields[8]));
            }
            
            // 解析海拔高度
            if (!fields[9].isEmpty()) {
                pos.setAltitude(Double.parseDouble(fields[9]));
            }
            
            // 解析大地水准面高度
            if (!fields[11].isEmpty()) {
                pos.setGeoidHeight(Double.parseDouble(fields[11]));
            }
            
            // 解析差分数据龄期
            if (fields.length > 13 && !fields[13].isEmpty()) {
                pos.setDiffAge(Double.parseDouble(fields[13]));
            }
            
            // 解析差分基站ID
            if (fields.length > 14 && !fields[14].isEmpty()) {
                pos.setDiffStation(fields[14]);
            }
            
            // 更新当前位置
            currentPosition = pos;
            lastGGATime = System.currentTimeMillis();
            
            return pos;
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 解析RMC语句 - 推荐最小定位信息
     * 格式: $GPRMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,x.x,a*hh
     * 
     * 字段说明:
     * 0: 语句类型
     * 1: UTC时间
     * 2: 状态 (A=有效, V=无效)
     * 3: 纬度
     * 4: 纬度方向
     * 5: 经度
     * 6: 经度方向
     * 7: 地面速度 (节)
     * 8: 地面航向 (度)
     * 9: 日期 (ddmmyy)
     * 10: 磁偏角
     * 11: 磁偏角方向
     */
    private RTKPosition parseRMC(String[] fields) {
        if (fields.length < 10) {
            return null;
        }
        
        try {
            // 检查状态
            if (fields[2].isEmpty() || !fields[2].equals("A")) {
                return null;
            }
            
            RTKPosition pos = new RTKPosition();
            
            // 解析时间
            if (!fields[1].isEmpty()) {
                pos.setTimestamp(parseUTCTime(fields[1]));
            }
            
            // 解析纬度
            if (!fields[3].isEmpty() && !fields[4].isEmpty()) {
                double lat = parseLatitude(fields[3], fields[4]);
                pos.setLatitude(lat);
            }
            
            // 解析经度
            if (!fields[5].isEmpty() && !fields[6].isEmpty()) {
                double lon = parseLongitude(fields[5], fields[6]);
                pos.setLongitude(lon);
            }
            
            // 解析速度（节转换为m/s）
            if (!fields[7].isEmpty()) {
                double speedKnots = Double.parseDouble(fields[7]);
                pos.setSpeed(speedKnots * 0.514444); // 1节 = 0.514444 m/s
            }
            
            // 解析航向
            if (!fields[8].isEmpty()) {
                pos.setCourse(Double.parseDouble(fields[8]));
            }
            
            // 如果最近有GGA数据，合并定位质量信息
            if (System.currentTimeMillis() - lastGGATime < 2000 && currentPosition != null) {
                pos.setFixQuality(currentPosition.getFixQuality());
                pos.setSatellites(currentPosition.getSatellites());
                pos.setHdop(currentPosition.getHdop());
                pos.setAltitude(currentPosition.getAltitude());
            }
            
            currentPosition = pos;
            lastRMCTime = System.currentTimeMillis();
            
            return pos;
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 解析GSA语句 - 卫星状态信息
     * 格式: $GPGSA,A,3,xx,xx,xx,xx,xx,xx,xx,xx,xx,xx,xx,xx,x.x,x.x,x.x*hh
     * 
     * 字段说明:
     * 0: 语句类型
     * 1: 模式 (M=手动, A=自动)
     * 2: 定位类型 (1=无定位, 2=2D, 3=3D)
     * 3-14: 使用的卫星PRN号
     * 15: PDOP
     * 16: HDOP
     * 17: VDOP
     */
    private void parseGSA(String[] fields) {
        if (fields.length < 18) {
            return;
        }
        
        try {
            // 解析PDOP
            if (!fields[15].isEmpty()) {
                currentPosition.setPdop(Double.parseDouble(fields[15]));
            }
            
            // 解析HDOP
            if (!fields[16].isEmpty()) {
                currentPosition.setHdop(Double.parseDouble(fields[16]));
            }
            
            // 解析VDOP
            if (!fields[17].isEmpty()) {
                currentPosition.setVdop(Double.parseDouble(fields[17]));
            }
            
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
    }
    
    /**
     * 解析VTG语句 - 地面速度信息
     * 格式: $GPVTG,x.x,T,x.x,M,x.x,N,x.x,K*hh
     * 
     * 字段说明:
     * 0: 语句类型
     * 1: 真北航向 (度)
     * 2: T
     * 3: 磁北航向 (度)
     * 4: M
     * 5: 地面速度 (节)
     * 6: N
     * 7: 地面速度 (km/h)
     * 8: K
     */
    private void parseVTG(String[] fields) {
        if (fields.length < 8) {
            return;
        }
        
        try {
            // 解析真北航向
            if (!fields[1].isEmpty()) {
                currentPosition.setCourse(Double.parseDouble(fields[1]));
            }
            
            // 解析速度（km/h转换为m/s）
            if (!fields[7].isEmpty()) {
                double speedKmh = Double.parseDouble(fields[7]);
                currentPosition.setSpeed(speedKmh / 3.6);
            }
            
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
    }
    
    /**
     * 解析GLL语句 - 地理位置信息
     * 格式: $GPGLL,llll.ll,a,yyyyy.yy,a,hhmmss.ss,A*hh
     */
    private RTKPosition parseGLL(String[] fields) {
        if (fields.length < 7) {
            return null;
        }
        
        try {
            // 检查状态
            if (fields[6].isEmpty() || !fields[6].equals("A")) {
                return null;
            }
            
            RTKPosition pos = new RTKPosition();
            
            // 解析纬度
            if (!fields[1].isEmpty() && !fields[2].isEmpty()) {
                double lat = parseLatitude(fields[1], fields[2]);
                pos.setLatitude(lat);
            }
            
            // 解析经度
            if (!fields[3].isEmpty() && !fields[4].isEmpty()) {
                double lon = parseLongitude(fields[3], fields[4]);
                pos.setLongitude(lon);
            }
            
            // 解析时间
            if (!fields[5].isEmpty()) {
                pos.setTimestamp(parseUTCTime(fields[5]));
            }
            
            return pos;
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 解析纬度
     * @param value 纬度值 (ddmm.mmmm)
     * @param direction 方向 (N/S)
     * @return 纬度（度）
     */
    private double parseLatitude(String value, String direction) {
        if (value.length() < 4) {
            return 0;
        }
        
        // 格式: ddmm.mmmm
        int degreeDigits = 2;
        double degrees = Double.parseDouble(value.substring(0, degreeDigits));
        double minutes = Double.parseDouble(value.substring(degreeDigits));
        
        double latitude = degrees + minutes / 60.0;
        
        if (direction.equalsIgnoreCase("S")) {
            latitude = -latitude;
        }
        
        return latitude;
    }
    
    /**
     * 解析经度
     * @param value 经度值 (dddmm.mmmm)
     * @param direction 方向 (E/W)
     * @return 经度（度）
     */
    private double parseLongitude(String value, String direction) {
        if (value.length() < 5) {
            return 0;
        }
        
        // 格式: dddmm.mmmm
        int degreeDigits = 3;
        double degrees = Double.parseDouble(value.substring(0, degreeDigits));
        double minutes = Double.parseDouble(value.substring(degreeDigits));
        
        double longitude = degrees + minutes / 60.0;
        
        if (direction.equalsIgnoreCase("W")) {
            longitude = -longitude;
        }
        
        return longitude;
    }
    
    /**
     * 解析UTC时间
     * @param timeStr 时间字符串 (hhmmss.ss)
     * @return 时间戳（毫秒）
     */
    private long parseUTCTime(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HHmmss.SS", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(timeStr);
            if (date != null) {
                // 结合当前日期
                long todayStart = System.currentTimeMillis() / 86400000 * 86400000;
                return todayStart + date.getTime() % 86400000;
            }
        } catch (Exception e) {
            // 解析失败，返回当前时间
        }
        return System.currentTimeMillis();
    }
    
    /**
     * 验证NMEA校验和
     * @param sentence 完整的NMEA语句
     * @return 校验和是否正确
     */
    private boolean verifyChecksum(String sentence) {
        int asteriskIndex = sentence.indexOf('*');
        if (asteriskIndex < 0 || asteriskIndex + 2 >= sentence.length()) {
            return true; // 没有校验和，默认通过
        }
        
        // 计算校验和
        int checksum = 0;
        for (int i = 1; i < asteriskIndex; i++) {
            checksum ^= sentence.charAt(i);
        }
        
        // 获取语句中的校验和
        try {
            String checksumStr = sentence.substring(asteriskIndex + 1, asteriskIndex + 3);
            int expectedChecksum = Integer.parseInt(checksumStr, 16);
            return checksum == expectedChecksum;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取当前解析的位置
     */
    public RTKPosition getCurrentPosition() {
        return currentPosition;
    }
    
    /**
     * 重置解析器状态
     */
    public void reset() {
        currentPosition = new RTKPosition();
        lastGGATime = 0;
        lastRMCTime = 0;
    }
}
