# DXF文件和坐标文件解析功能迁移文档

## 📋 概述

本文档说明如何将DXF文件解析和坐标文件解析功能迁移到其他Android项目中。

---

## 一、需要迁移的文件清单

### 1.1 DXF文件解析核心文件（位于 `dwg` 文件夹）

**主要解析器：**
- `com/example/solidifyapp/dwg/DwgDxfParser.java` (566行) - 使用Kabeja库的完整DXF解析器
- `com/example/solidifyapp/dwg/SimpleDxfParser.java` (267行) - 简单的正则表达式DXF解析器

**数据模型类：**
- `com/example/solidifyapp/dwg/ParseResult.java` (52行) - 解析结果封装类
- `com/example/solidifyapp/dwg/LayerInfo.java` (56行) - 图层信息类
- `com/example/solidifyapp/dwg/EntityInfo.java` (46行) - 实体信息类
- `com/example/solidifyapp/dwg/DxfOverlayResult.java` (33行) - 网格覆盖层结果类

**可选扩展：**
- `com/example/solidifyapp/dwg/DwgParser.java` (20266字节) - DWG文件解析器（如需要）

### 1.2 坐标文件解析核心代码

**主要代码位置：**
- `com/example/solidifyapp/view/DataManagementActivity.java` 
  - 第821-899行：`handleImportedCoordinates()` 方法 - 坐标文件解析核心逻辑
  - 第1729-1808行：`addCoordinateFromInput()` 方法 - 手动输入坐标处理
  - 第1813-1878行：`handlePointLibraryResult()` 方法 - 点库导入处理

**辅助工具类（可选）：**
- `com/example/solidifyapp/utils/PointModeEditor.java` - 点和连线管理工具

---

## 二、外部库依赖

### 2.1 DXF解析依赖

**必需的JAR包（位于 `app/libs/` 文件夹）：**
```
libs/
├── kabeja-0.4.jar          (339 KB) - 核心DXF解析库
├── kabeja-svg-0.4.jar      (202 KB) - SVG支持
├── batik-all.jar           (2.9 MB) - 图形处理库
└── xml-apis.jar            (194 KB) - XML API支持（排除miethxml-ui.jar和xml-apis-ext.jar以避免冲突）
```

**Gradle配置（`app/build.gradle`）：**
```gradle
android {
    // 解决 jar 包内重复资源冲突
    packaging {
        resources {
            excludes += ["icons/**"]
        }
    }
}

dependencies {
    // 排除可能冲突的JAR包
    implementation fileTree(dir: 'libs', include: ['*.jar'], 
        exclude: ['miethxml-ui.jar', 'xml-apis-ext.jar'])
}
```

### 2.2 Android框架依赖

```gradle
dependencies {
    // 基础Android库
    implementation 'androidx.appcompat:appcompat:1.6.1'
    
    // 如果使用Log工具
    // Android SDK自带，无需额外依赖
}
```

---

## 三、迁移步骤

### 3.1 DXF解析功能迁移

#### 步骤1：复制文件
```bash
# 1. 复制DXF解析相关的Java文件
源路径: app/src/main/java/com/example/solidifyapp/dwg/
目标路径: 你的项目/src/main/java/你的包名/dwg/

需要复制的文件：
- DwgDxfParser.java
- SimpleDxfParser.java
- ParseResult.java
- LayerInfo.java
- EntityInfo.java
- DxfOverlayResult.java

# 2. 复制JAR库文件
源路径: app/libs/
目标路径: 你的项目/app/libs/

需要复制的文件：
- kabeja-0.4.jar
- kabeja-svg-0.4.jar
- batik-all.jar
- xml-apis.jar (确保只复制这一个版本)
```

#### 步骤2：修改包名
在所有复制的Java文件中，将包名从：
```java
package com.example.solidifyapp.dwg;
```
改为你的项目包名：
```java
package 你的包名.dwg;
```

#### 步骤3：配置Gradle
在 `app/build.gradle` 中添加：
```gradle
android {
    packaging {
        resources {
            excludes += ["icons/**"]
        }
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'], 
        exclude: ['miethxml-ui.jar', 'xml-apis-ext.jar'])
}
```

#### 步骤4：同步项目
在Android Studio中执行：
```
File -> Sync Project with Gradle Files
```

### 3.2 坐标文件解析功能迁移

#### 步骤1：创建工具类
创建新文件 `你的包名/utils/CoordinateFileParser.java`：

```java
package 你的包名.utils;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 坐标文件解析工具类
 * 支持格式：每行 xNorth,yEast[,z]
 * 支持分隔符：逗号(,，)、分号(;；)、顿号(、)、空白符
 */
public class CoordinateFileParser {
    private static final String TAG = "CoordinateFileParser";

    /**
     * 解析坐标文件
     * @param uri 文件URI
     * @param contentResolver ContentResolver实例
     * @return 坐标点列表，每项为 [north, east, altitude]
     */
    public static List<double[]> parseCoordinateFile(Uri uri, ContentResolver contentResolver) {
        List<double[]> points = new ArrayList<>();
        
        try {
            InputStream is = contentResolver.openInputStream(uri);
            if (is == null) {
                Log.e(TAG, "无法打开坐标文件");
                return points;
            }
            
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            int lineNumber = 0;
            
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // 去除可能存在的 UTF-8 BOM
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue; // 跳过空行和注释行
                }
                
                try {
                    // 支持英文/中文标点分隔：逗号(,，)、分号(;；)、顿号(、)及空白
                    String[] tokens = line.split("[\\s,，,;；、]+");
                    if (tokens.length < 2) {
                        Log.w(TAG, "第" + lineNumber + "行格式错误，至少需要2个数值");
                        continue;
                    }
                    
                    String t0 = tokens[0].trim();
                    String t1 = tokens[1].trim();
                    String t2 = tokens.length >= 3 ? tokens[2].trim() : "0";
                    
                    double north = Double.parseDouble(t0);
                    double east = Double.parseDouble(t1);
                    double alt = Double.parseDouble(t2);
                    
                    points.add(new double[]{north, east, alt});
                    
                } catch (NumberFormatException e) {
                    Log.w(TAG, "第" + lineNumber + "行数值解析失败: " + line);
                }
            }
            
            br.close();
            Log.d(TAG, "成功解析 " + points.size() + " 个坐标点");
            
        } catch (Exception e) {
            Log.e(TAG, "坐标文件解析失败", e);
        }
        
        return points;
    }
    
    /**
     * 从字符串解析单个坐标点
     * @param coordinateStr 坐标字符串，格式: "x,y" 或 "x,y,z"
     * @return 坐标数组 [north, east, altitude]，解析失败返回null
     */
    public static double[] parseCoordinateString(String coordinateStr) {
        if (coordinateStr == null || coordinateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            String[] tokens = coordinateStr.trim().split("[\\s,，,;；、]+");
            if (tokens.length < 2) {
                return null;
            }
            
            double north = Double.parseDouble(tokens[0].trim());
            double east = Double.parseDouble(tokens[1].trim());
            double alt = tokens.length >= 3 ? Double.parseDouble(tokens[2].trim()) : 0.0;
            
            return new double[]{north, east, alt};
            
        } catch (NumberFormatException e) {
            Log.e(TAG, "坐标字符串解析失败: " + coordinateStr, e);
            return null;
        }
    }
}
```

#### 步骤2：使用方式
在你的Activity中使用：

```java
import android.net.Uri;
import 你的包名.utils.CoordinateFileParser;

// 解析坐标文件
Uri fileUri = ... // 从文件选择器获取
List<double[]> coordinates = CoordinateFileParser.parseCoordinateFile(
    fileUri, 
    getContentResolver()
);

// 使用解析结果
for (double[] point : coordinates) {
    double north = point[0];
    double east = point[1];
    double altitude = point[2];
    // 处理坐标数据
}

// 或解析单个坐标字符串
double[] point = CoordinateFileParser.parseCoordinateString("123.456, 789.012, 10.5");
```

---

## 四、使用示例

### 4.1 DXF文件解析示例

```java
import 你的包名.dwg.DwgDxfParser;
import 你的包名.dwg.ParseResult;
import 你的包名.dwg.SimpleDxfParser;
import 你的包名.dwg.DxfOverlayResult;

// 方法1：使用Kabeja库解析DXF（功能完整）
public void parseDxfWithKabeja(Uri fileUri) {
    try {
        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        ParseResult result = DwgDxfParser.parseDxfStream(inputStream);
        
        if (result.isSuccess()) {
            // 获取图层信息
            List<LayerInfo> layers = result.getLayers();
            for (LayerInfo layer : layers) {
                Log.d(TAG, "图层: " + layer.getName() + 
                           ", 颜色: " + layer.getColor());
            }
            
            // 获取实体信息
            List<EntityInfo> entities = result.getEntities();
            for (EntityInfo entity : entities) {
                Log.d(TAG, "实体类型: " + entity.getType() + 
                           ", 所在图层: " + entity.getLayerName());
            }
        } else {
            Log.e(TAG, "解析失败: " + result.getErrorMessage());
        }
        
        inputStream.close();
        
    } catch (Exception e) {
        Log.e(TAG, "DXF解析异常", e);
    }
}

// 方法2：使用SimpleDxfParser（轻量级）
public void parseDxfSimple(Uri fileUri) {
    try {
        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        ParseResult result = SimpleDxfParser.parse(inputStream);
        
        if (result.isSuccess()) {
            Log.d(TAG, "图层数: " + result.getLayers().size());
            Log.d(TAG, "实体数: " + result.getEntities().size());
        }
        
        inputStream.close();
        
    } catch (Exception e) {
        Log.e(TAG, "SimpleDxfParser解析异常", e);
    }
}

// 方法3：提取DXF为网格覆盖层（点和连线）
public void extractDxfOverlay(Uri fileUri) {
    try {
        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        DxfOverlayResult overlay = DwgDxfParser.parseDxfToOverlay(inputStream);
        
        // 获取点列表
        List<float[]> points = overlay.getPoints();
        for (float[] point : points) {
            float north = point[0];
            float east = point[1];
            // 处理点坐标
        }
        
        // 获取连线列表
        List<int[]> links = overlay.getLinks();
        for (int[] link : links) {
            int startIndex = link[0];
            int endIndex = link[1];
            // 处理连线（索引对应points列表）
        }
        
        inputStream.close();
        
    } catch (Exception e) {
        Log.e(TAG, "提取DXF覆盖层失败", e);
    }
}
```

### 4.2 坐标文件解析示例

```java
import 你的包名.utils.CoordinateFileParser;

// 示例1：从文件选择器解析坐标文件
ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
    new ActivityResultContracts.StartActivityForResult(),
    result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri fileUri = result.getData().getData();
            List<double[]> coordinates = CoordinateFileParser.parseCoordinateFile(
                fileUri, 
                getContentResolver()
            );
            
            Toast.makeText(this, 
                "成功导入 " + coordinates.size() + " 个坐标点", 
                Toast.LENGTH_SHORT).show();
                
            // 处理坐标数据
            for (double[] coord : coordinates) {
                processCoordinate(coord[0], coord[1], coord[2]);
            }
        }
    }
);

// 打开文件选择器
Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
intent.setType("text/*");
filePickerLauncher.launch(intent);

// 示例2：手动输入坐标
String userInput = "123.456, 789.012, 10.5";
double[] point = CoordinateFileParser.parseCoordinateString(userInput);
if (point != null) {
    processCoordinate(point[0], point[1], point[2]);
}
```

---

## 五、坐标文件格式说明

### 5.1 支持的文件格式

**文件扩展名：** `.txt`, `.csv`, `.dat` 或任意文本文件

**编码支持：** UTF-8, GBK（自动去除BOM）

### 5.2 坐标格式

每行一个坐标点，格式为：
```
北坐标,东坐标[,高程]
```

**示例文件内容：**
```
# 这是注释行
// 这也是注释行

# 格式1：逗号分隔
123.456,789.012,10.5
124.567,790.123,11.2

# 格式2：空格分隔
125.678 791.234 12.3
126.789 792.345 13.4

# 格式3：混合分隔（中文标点）
127.890，793.456，14.5
128.901；794.567；15.6

# 格式4：只有XY坐标（Z默认为0）
129.012,795.678
130.123 796.789
```

**支持的分隔符：**
- 逗号：`,` 或 `，`（中文逗号）
- 分号：`;` 或 `；`（中文分号）
- 顿号：`、`
- 空白：空格、Tab

---

## 六、注意事项

### 6.1 DXF解析注意事项

1. **Kabeja版本兼容性**
   - 当前使用Kabeja 0.4版本
   - 代码中使用反射处理不同版本API差异
   - 如遇到兼容性问题，可使用SimpleDxfParser作为备选

2. **大文件处理**
   - 建议对大文件（>10MB）进行异步解析
   - 可以在后台线程执行解析，避免UI阻塞

3. **坐标系统**
   - DXF文件中：x对应北坐标(north)，y对应东坐标(east)
   - 如需转换坐标系统，需在解析后进行额外处理

4. **JAR包冲突**
   - 务必排除`miethxml-ui.jar`和`xml-apis-ext.jar`
   - 使用`excludes += ["icons/**"]`避免资源冲突

### 6.2 坐标文件解析注意事项

1. **数值精度**
   - 使用`double`类型存储，精度约15-17位有效数字
   - 适合大多数工程测量精度要求

2. **错误处理**
   - 解析器会跳过格式错误的行
   - 在Log中输出警告信息，便于调试

3. **文件编码**
   - 建议使用UTF-8编码
   - 自动处理UTF-8 BOM标记

4. **性能优化**
   - 对于大量坐标点（>10000个），建议使用异步加载
   - 可以分批处理和显示

### 6.3 权限配置

在 `AndroidManifest.xml` 中添加必要权限：
```xml
<!-- 读取外部存储权限（Android 10以下） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- 如需写入文件 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
    android:maxSdkVersion="28" />
```

对于Android 10+，使用作用域存储(Scoped Storage)，通过`ContentResolver`访问文件。

---

## 七、测试建议

### 7.1 DXF解析测试

创建测试用例：
```java
@Test
public void testDxfParsing() {
    // 准备测试DXF文件
    InputStream testFile = getClass().getResourceAsStream("/test.dxf");
    ParseResult result = DwgDxfParser.parseDxfStream(testFile);
    
    assertTrue(result.isSuccess());
    assertNotNull(result.getLayers());
    assertNotNull(result.getEntities());
}
```

### 7.2 坐标解析测试

```java
@Test
public void testCoordinateParsing() {
    String testData = "123.456, 789.012, 10.5";
    double[] point = CoordinateFileParser.parseCoordinateString(testData);
    
    assertNotNull(point);
    assertEquals(123.456, point[0], 0.001);
    assertEquals(789.012, point[1], 0.001);
    assertEquals(10.5, point[2], 0.001);
}
```

---

## 八、常见问题

### Q1: Kabeja库导入后编译失败？
**A:** 检查是否排除了冲突的JAR包，确保build.gradle中配置正确。

### Q2: 解析DXF文件返回空结果？
**A:** 可能是DXF版本不兼容，尝试使用SimpleDxfParser或检查文件是否损坏。

### Q3: 坐标文件解析结果为空？
**A:** 检查文件格式是否正确，是否有足够的列数（至少2列）。

### Q4: Android 11+无法读取文件？
**A:** 使用SAF（Storage Access Framework）通过Uri访问文件，而非直接文件路径。

---

## 九、联系与支持

如有问题，请参考：
- 原项目位置：`e:\solidify`
- 核心代码位置：`app/src/main/java/com/example/solidifyapp/`
- Kabeja官方文档：http://kabeja.sourceforge.net/

---

**版本信息：**
- 文档版本：1.0
- 更新日期：2026-01-18
- 兼容Android版本：API 30+








