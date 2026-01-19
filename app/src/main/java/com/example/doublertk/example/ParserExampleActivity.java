// ============================================================================
// 快速开始示例：DXF和坐标文件解析
// ============================================================================

package com.example.doublertk.example;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.List;

// 导入DXF解析相关类
import com.example.doublertk.dwg.DwgDxfParser;
import com.example.doublertk.dwg.SimpleDxfParser;
import com.example.doublertk.dwg.ParseResult;
import com.example.doublertk.dwg.LayerInfo;
import com.example.doublertk.dwg.EntityInfo;
import com.example.doublertk.dwg.DxfOverlayResult;

// 导入坐标文件解析类
import com.example.doublertk.utils.CoordinateFileParser;

/**
 * 快速开始示例：演示如何使用DXF和坐标文件解析功能
 */
public class ParserExampleActivity extends AppCompatActivity {
    
    private static final String TAG = "ParserExample";
    
    // 文件选择器启动器
    private ActivityResultLauncher<Intent> dxfFilePicker;
    private ActivityResultLauncher<Intent> coordFilePicker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化文件选择器
        initFilePickers();
        
        // 创建简单UI
        setupUI();
    }
    
    // ========================================================================
    // 1. 初始化文件选择器
    // ========================================================================
    
    private void initFilePickers() {
        // DXF文件选择器
        dxfFilePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    parseDxfFile(fileUri);
                }
            }
        );
        
        // 坐标文件选择器
        coordFilePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    parseCoordinateFile(fileUri);
                }
            }
        );
    }
    
    // ========================================================================
    // 2. 创建UI（示例）
    // ========================================================================
    
    private void setupUI() {
        // 在实际应用中，你应该使用XML布局
        // 这里只是演示代码逻辑
        
        // Button btnParseDxf = findViewById(R.id.btn_parse_dxf);
        // btnParseDxf.setOnClickListener(v -> openDxfFilePicker());
        
        // Button btnParseCoord = findViewById(R.id.btn_parse_coord);
        // btnParseCoord.setOnClickListener(v -> openCoordFilePicker());
    }
    
    // ========================================================================
    // 3. DXF文件解析示例
    // ========================================================================
    
    /**
     * 打开DXF文件选择器
     */
    private void openDxfFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");  // 或 "application/dxf"
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        dxfFilePicker.launch(intent);
    }
    
    /**
     * 解析DXF文件（使用Kabeja库 - 功能完整）
     */
    private void parseDxfFile(Uri fileUri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    showToast("无法打开文件");
                    return;
                }
                
                // 使用Kabeja解析器
                ParseResult result = DwgDxfParser.parseDxfStream(inputStream);
                
                if (result.isSuccess()) {
                    // 获取图层信息
                    List<LayerInfo> layers = result.getLayers();
                    List<EntityInfo> entities = result.getEntities();
                    
                    runOnUiThread(() -> {
                        Log.d(TAG, "DXF解析成功！");
                        Log.d(TAG, "图层数量: " + layers.size());
                        Log.d(TAG, "实体数量: " + entities.size());
                        
                        // 显示图层信息
                        for (LayerInfo layer : layers) {
                            Log.d(TAG, String.format(
                                "图层: %s, 颜色: %d, 线型: %s, 可见: %b",
                                layer.getName(),
                                layer.getColor(),
                                layer.getLineType(),
                                layer.isVisible()
                            ));
                        }
                        
                        // 显示实体信息
                        for (EntityInfo entity : entities) {
                            Log.d(TAG, String.format(
                                "实体类型: %s, 图层: %s, 边界: %s",
                                entity.getType(),
                                entity.getLayerName(),
                                entity.getBounds()
                            ));
                        }
                        
                        showToast("DXF解析成功！图层: " + layers.size() + 
                                 ", 实体: " + entities.size());
                    });
                    
                } else {
                    runOnUiThread(() -> {
                        Log.e(TAG, "DXF解析失败: " + result.getErrorMessage());
                        showToast("解析失败: " + result.getErrorMessage());
                    });
                }
                
                inputStream.close();
                
            } catch (Exception e) {
                Log.e(TAG, "DXF文件解析异常", e);
                runOnUiThread(() -> showToast("解析异常: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * 解析DXF文件（使用简单解析器 - 轻量级）
     */
    private void parseDxfFileSimple(Uri fileUri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    showToast("无法打开文件");
                    return;
                }
                
                // 使用简单解析器（不依赖Kabeja）
                ParseResult result = SimpleDxfParser.parse(inputStream);
                
                if (result.isSuccess()) {
                    runOnUiThread(() -> {
                        showToast("SimpleDxfParser解析成功！");
                    });
                }
                
                inputStream.close();
                
            } catch (Exception e) {
                Log.e(TAG, "SimpleDxfParser解析异常", e);
                runOnUiThread(() -> showToast("解析异常: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * 提取DXF为网格覆盖层（点和连线）
     */
    private void extractDxfOverlay(Uri fileUri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    showToast("无法打开文件");
                    return;
                }
                
                DxfOverlayResult overlay = DwgDxfParser.parseDxfToOverlay(inputStream);
                
                List<float[]> points = overlay.getPoints();
                List<int[]> links = overlay.getLinks();
                
                runOnUiThread(() -> {
                    Log.d(TAG, "提取覆盖层成功！");
                    Log.d(TAG, "点数量: " + points.size());
                    Log.d(TAG, "连线数量: " + links.size());
                    
                    // 遍历点
                    for (int i = 0; i < Math.min(5, points.size()); i++) {
                        float[] point = points.get(i);
                        Log.d(TAG, String.format(
                            "点%d: north=%.3f, east=%.3f",
                            i, point[0], point[1]
                        ));
                    }
                    
                    // 遍历连线
                    for (int i = 0; i < Math.min(5, links.size()); i++) {
                        int[] link = links.get(i);
                        Log.d(TAG, String.format(
                            "连线%d: 从点%d到点%d",
                            i, link[0], link[1]
                        ));
                    }
                    
                    showToast("覆盖层提取成功！点: " + points.size() + 
                             ", 线: " + links.size());
                });
                
                inputStream.close();
                
            } catch (Exception e) {
                Log.e(TAG, "提取覆盖层异常", e);
                runOnUiThread(() -> showToast("提取异常: " + e.getMessage()));
            }
        }).start();
    }
    
    // ========================================================================
    // 4. 坐标文件解析示例
    // ========================================================================
    
    /**
     * 打开坐标文件选择器
     */
    private void openCoordFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");  // txt, csv等文本文件
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        coordFilePicker.launch(intent);
    }
    
    /**
     * 解析坐标文件
     */
    private void parseCoordinateFile(Uri fileUri) {
        new Thread(() -> {
            // 使用坐标文件解析器
            List<double[]> coordinates = CoordinateFileParser.parseCoordinateFile(
                fileUri, 
                getContentResolver()
            );
            
            runOnUiThread(() -> {
                if (coordinates.isEmpty()) {
                    showToast("未能解析到坐标数据");
                    return;
                }
                
                Log.d(TAG, "坐标文件解析成功！");
                Log.d(TAG, "坐标点数量: " + coordinates.size());
                
                // 显示前几个坐标点
                for (int i = 0; i < Math.min(10, coordinates.size()); i++) {
                    double[] coord = coordinates.get(i);
                    Log.d(TAG, String.format(
                        "坐标%d: N=%.6f, E=%.6f, H=%.3f",
                        i + 1, coord[0], coord[1], coord[2]
                    ));
                }
                
                showToast("成功解析 " + coordinates.size() + " 个坐标点");
                
                // 处理坐标数据
                processCoordinates(coordinates);
            });
        }).start();
    }
    
    /**
     * 解析单个坐标字符串（手动输入）
     */
    private void parseCoordinateString(String inputText) {
        double[] point = CoordinateFileParser.parseCoordinateString(inputText);
        
        if (point != null) {
            Log.d(TAG, String.format(
                "解析坐标: N=%.6f, E=%.6f, H=%.3f",
                point[0], point[1], point[2]
            ));
            showToast("坐标解析成功");
            
            // 处理单个坐标点
            processSingleCoordinate(point[0], point[1], point[2]);
        } else {
            showToast("坐标格式错误");
        }
    }
    
    // ========================================================================
    // 5. 数据处理示例
    // ========================================================================
    
    /**
     * 处理坐标数据（根据实际需求实现）
     */
    private void processCoordinates(List<double[]> coordinates) {
        // TODO: 在这里实现你的业务逻辑
        // 例如：显示在地图上、保存到数据库、进行计算等
        
        for (double[] coord : coordinates) {
            double north = coord[0];
            double east = coord[1];
            double altitude = coord[2];
            
            // 你的处理逻辑...
        }
    }
    
    /**
     * 处理单个坐标点
     */
    private void processSingleCoordinate(double north, double east, double altitude) {
        // TODO: 处理单个坐标点
        Log.d(TAG, "处理坐标: " + north + ", " + east + ", " + altitude);
    }
    
    // ========================================================================
    // 6. 工具方法
    // ========================================================================
    
    private void showToast(String message) {
        runOnUiThread(() -> 
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        );
    }
}

// ============================================================================
// 使用说明
// ============================================================================

/*

1. 在你的Activity中使用DXF解析：
   
   Uri dxfFileUri = ...; // 从文件选择器获取
   InputStream is = getContentResolver().openInputStream(dxfFileUri);
   ParseResult result = DwgDxfParser.parseDxfStream(is);
   
   if (result.isSuccess()) {
       List<LayerInfo> layers = result.getLayers();
       List<EntityInfo> entities = result.getEntities();
       // 处理数据...
   }

2. 在你的Activity中使用坐标文件解析：
   
   Uri coordFileUri = ...; // 从文件选择器获取
   List<double[]> coords = CoordinateFileParser.parseCoordinateFile(
       coordFileUri, 
       getContentResolver()
   );
   
   for (double[] coord : coords) {
       double north = coord[0];
       double east = coord[1];
       double altitude = coord[2];
       // 处理坐标...
   }

3. 解析单个坐标字符串：
   
   String input = "123.456, 789.012, 10.5";
   double[] point = CoordinateFileParser.parseCoordinateString(input);
   
   if (point != null) {
       // 使用point[0], point[1], point[2]
   }

4. 提取DXF为覆盖层：
   
   Uri dxfFileUri = ...;
   InputStream is = getContentResolver().openInputStream(dxfFileUri);
   DxfOverlayResult overlay = DwgDxfParser.parseDxfToOverlay(is);
   
   List<float[]> points = overlay.getPoints();  // 点坐标列表
   List<int[]> links = overlay.getLinks();      // 连线索引列表

*/

