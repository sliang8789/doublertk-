package com.example.doublertk.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.example.doublertk.base.BaseActivity;
import com.example.doublertk.dwg.DwgDxfParser;
import com.example.doublertk.dwg.DxfOverlayResult;
import com.example.doublertk.utils.CoordinateFileParser;

import java.io.InputStream;
import java.util.List;

/**
 * 精简版 DataManagementActivity
 *
 * 原始版本来自其它工程（solidifyapp），依赖大量本项目不存在的类（network/db/rtk 等），会导致编译失败。
 * 这里保留一个可编译的最小实现，方便你先把 DXF/坐标解析跑通：
 * - 选择 DXF 文件 -> 解析 overlay（目前主要支持 LINE）并 Toast 点/线数量
 * - 选择坐标文件 -> 解析坐标点并 Toast 数量
 */
public class DataManagementActivity extends BaseActivity {

    private ActivityResultLauncher<Intent> dxfPicker;
    private ActivityResultLauncher<Intent> coordPicker;

    @Override
    protected int getLayoutId() {
        // 该页面使用纯代码动态布局，因此不使用 XML layout
        return 0;
    }

    @Override
    protected void initView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        Button btnPickDxf = new Button(this);
        btnPickDxf.setText("选择DXF文件并解析");
        root.addView(btnPickDxf);

        Button btnPickCoord = new Button(this);
        btnPickCoord.setText("选择坐标文件并解析");
        root.addView(btnPickCoord);

        setContentView(root);

        dxfPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    parseDxf(uri);
                }
        );

        coordPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    parseCoords(uri);
                }
        );

        btnPickDxf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            dxfPicker.launch(intent);
        });

        btnPickCoord.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/*");
            coordPicker.launch(intent);
        });
    }

    private void parseDxf(Uri uri) {
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    runOnUiThread(() -> Toast.makeText(this, "无法打开DXF文件", Toast.LENGTH_SHORT).show());
                    return;
                }
                DxfOverlayResult overlay = DwgDxfParser.parseDxfToOverlay(is);
                int pCount = overlay.getPoints() == null ? 0 : overlay.getPoints().size();
                int lCount = overlay.getLinks() == null ? 0 : overlay.getLinks().size();
                runOnUiThread(() -> Toast.makeText(this, "DXF解析完成：点=" + pCount + " 线=" + lCount, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "DXF解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void parseCoords(Uri uri) {
        new Thread(() -> {
            List<double[]> pts = CoordinateFileParser.parseCoordinateFile(uri, getContentResolver());
            runOnUiThread(() -> Toast.makeText(this, "坐标解析完成：点数=" + (pts == null ? 0 : pts.size()), Toast.LENGTH_SHORT).show());
        }).start();
    }
}


