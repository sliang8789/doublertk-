package com.example.doublertk.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.doublertk.R;
import com.example.doublertk.dwg.DwgDxfParser;
import com.example.doublertk.dwg.EntityInfo;
import com.example.doublertk.dwg.LayerInfo;
import com.example.doublertk.dwg.ParseResult;
import com.example.doublertk.dwg.SimpleDxfParser;

import java.io.InputStream;
import java.util.List;

/**
 * DXF文件查看器Activity
 * 显示DXF文件的解析结果和实体渲染
 */
public class DxfViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "DxfViewer";
    
    private DxfEntityRenderer entityRenderer;
    private TextView tvFileInfo;
    private TextView tvEntityList;
    private Button btnSelectFile;
    private View scrollViewContainer;
    
    private ActivityResultLauncher<Intent> filePicker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dxf_viewer);
        
        initViews();
        initFilePicker();
        
        // 检查是否从Intent传入了文件路径
        String dxfPath = getIntent().getStringExtra("dxf_path");
        if (dxfPath != null && !dxfPath.isEmpty()) {
            // 从其他Activity跳转过来，隐藏文件选择按钮
            btnSelectFile.setVisibility(View.GONE);
            
            // 调整布局权重，使渲染视图占据更大空间
            LinearLayout.LayoutParams rendererParams = 
                (LinearLayout.LayoutParams) entityRenderer.getLayoutParams();
            rendererParams.weight = 5; // 增大渲染视图权重
            entityRenderer.setLayoutParams(rendererParams);
            
            LinearLayout.LayoutParams scrollParams = 
                (LinearLayout.LayoutParams) scrollViewContainer.getLayoutParams();
            scrollParams.weight = 1; // 减小信息区域权重
            scrollViewContainer.setLayoutParams(scrollParams);
            
            parseDxfFileFromPath(dxfPath);
        } else {
            // 独立打开，显示文件选择按钮
            btnSelectFile.setVisibility(View.VISIBLE);
        }
    }
    
    private void initViews() {
        entityRenderer = findViewById(R.id.dxf_entity_renderer);
        tvFileInfo = findViewById(R.id.tv_file_info);
        tvEntityList = findViewById(R.id.tv_entity_list);
        btnSelectFile = findViewById(R.id.btn_select_file);
        scrollViewContainer = findViewById(R.id.scroll_view_container);
        
        btnSelectFile.setOnClickListener(v -> openFilePicker());
    }
    
    private void initFilePicker() {
        filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    parseDxfFile(fileUri);
                }
            }
        );
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePicker.launch(intent);
    }
    
    private void parseDxfFile(Uri fileUri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    showToast("无法打开文件");
                    return;
                }
                
                // 使用SimpleDxfParser解析（更完整的解析）
                ParseResult result = SimpleDxfParser.parse(inputStream);
                
                if (result.isSuccess()) {
                    List<LayerInfo> layers = result.getLayers();
                    List<EntityInfo> entities = result.getEntities();
                    
                    runOnUiThread(() -> {
                        displayParseResult(result);
                        entityRenderer.setEntities(entities);
                        showToast("解析成功！实体数: " + (entities != null ? entities.size() : 0));
                    });
                } else {
                    runOnUiThread(() -> {
                        tvFileInfo.setText("解析失败: " + result.getErrorMessage());
                        showToast("解析失败");
                    });
                }
                
                inputStream.close();
                
            } catch (Exception e) {
                Log.e(TAG, "DXF文件解析异常", e);
                runOnUiThread(() -> {
                    tvFileInfo.setText("解析异常: " + e.getMessage());
                    showToast("解析异常: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void parseDxfFileFromPath(String filePath) {
        new Thread(() -> {
            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(filePath);
                
                // 使用SimpleDxfParser解析（更完整的解析）
                ParseResult result = SimpleDxfParser.parse(fis);
                
                if (result.isSuccess()) {
                    List<LayerInfo> layers = result.getLayers();
                    List<EntityInfo> entities = result.getEntities();
                    
                    runOnUiThread(() -> {
                        displayParseResult(result);
                        entityRenderer.setEntities(entities);
                        showToast("解析成功！实体数: " + (entities != null ? entities.size() : 0));
                    });
                } else {
                    runOnUiThread(() -> {
                        tvFileInfo.setText("解析失败: " + result.getErrorMessage());
                        showToast("解析失败");
                    });
                }
                
                fis.close();
                
            } catch (Exception e) {
                Log.e(TAG, "DXF文件解析异常", e);
                runOnUiThread(() -> {
                    tvFileInfo.setText("解析异常: " + e.getMessage());
                    showToast("解析异常: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void displayParseResult(ParseResult result) {
        List<LayerInfo> layers = result.getLayers();
        List<EntityInfo> entities = result.getEntities();
        
        // 检查是否从Intent传入了文件路径
        String dxfPath = getIntent().getStringExtra("dxf_path");
        boolean fromIntent = (dxfPath != null && !dxfPath.isEmpty());
        
        if (fromIntent) {
            // 从其他Activity跳转过来，只显示简要信息
            StringBuilder info = new StringBuilder();
            info.append("实体总数: ").append(entities != null ? entities.size() : 0);
            info.append(" | 图层: ").append(layers != null ? layers.size() : 0);
            tvFileInfo.setText(info.toString());
            tvEntityList.setText(""); // 清空详细列表
        } else {
            // 独立打开，显示完整信息
            StringBuilder info = new StringBuilder();
            info.append("=== DXF解析结果 ===\n\n");
            info.append("图层数量: ").append(layers != null ? layers.size() : 0).append("\n");
            info.append("实体数量: ").append(entities != null ? entities.size() : 0).append("\n\n");
            
            // 显示图层信息
            if (layers != null && !layers.isEmpty()) {
                info.append("--- 图层列表 ---\n");
                for (int i = 0; i < Math.min(10, layers.size()); i++) {
                    LayerInfo layer = layers.get(i);
                    info.append(String.format("%d. %s (颜色:%d)\n", 
                        i + 1, layer.getName(), layer.getColor()));
                }
                if (layers.size() > 10) {
                    info.append("... 还有 ").append(layers.size() - 10).append(" 个图层\n");
                }
                info.append("\n");
            }
            
            tvFileInfo.setText(info.toString());
            
            // 显示实体列表
            displayEntityList(entities);
        }
    }
    
    private void displayEntityList(List<EntityInfo> entities) {
        if (entities == null || entities.isEmpty()) {
            tvEntityList.setText("暂无实体数据");
            return;
        }
        
        StringBuilder list = new StringBuilder();
        list.append("=== 实体列表 ===\n\n");
        
        // 统计实体类型
        int lineCount = 0, circleCount = 0, pointCount = 0, otherCount = 0;
        for (EntityInfo entity : entities) {
            if (entity == null) continue;
            String type = entity.getType();
            if ("LINE".equals(type)) lineCount++;
            else if ("CIRCLE".equals(type)) circleCount++;
            else if ("POINT".equals(type)) pointCount++;
            else otherCount++;
        }
        
        list.append("统计:\n");
        list.append("  直线(LINE): ").append(lineCount).append("\n");
        list.append("  圆(CIRCLE): ").append(circleCount).append("\n");
        list.append("  点(POINT): ").append(pointCount).append("\n");
        list.append("  其他: ").append(otherCount).append("\n\n");
        
        // 显示前20个实体的详细信息
        list.append("--- 实体详情 (前20个) ---\n");
        for (int i = 0; i < Math.min(20, entities.size()); i++) {
            EntityInfo entity = entities.get(i);
            if (entity == null) continue;
            
            list.append(String.format("%d. %s\n", i + 1, entity.getType()));
            list.append("   图层: ").append(entity.getLayerName()).append("\n");
            if (entity.getBounds() != null && !entity.getBounds().isEmpty()) {
                list.append("   ").append(entity.getBounds()).append("\n");
            }
            list.append("\n");
        }
        
        if (entities.size() > 20) {
            list.append("... 还有 ").append(entities.size() - 20).append(" 个实体\n");
        }
        
        tvEntityList.setText(list.toString());
    }
    
    private void showToast(String message) {
        runOnUiThread(() -> 
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        );
    }
}
