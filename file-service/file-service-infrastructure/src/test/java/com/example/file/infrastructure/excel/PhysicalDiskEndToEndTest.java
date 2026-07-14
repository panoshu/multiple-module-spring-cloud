package com.example.file.infrastructure.excel;

import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.infrastructure.excel.persistence.YamlExcelSchemaRepositoryAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("集成测试: 基于物理磁盘真实文件的 Read & Write 大闭环")
class PhysicalDiskEndToEndTest {

  private final YamlExcelSchemaRepositoryAdapter repository = new YamlExcelSchemaRepositoryAdapter();
  private final FesodBatchEngineAdapter readEngine = new FesodBatchEngineAdapter();
  private final FesodWriteEngineAdapter writeEngine = new FesodWriteEngineAdapter();

  @Test
  @DisplayName("硬核磁盘IO测试：读取 Classpath 真实文件 -> 解析 -> 落盘生成真实文件")
  void testPhysicalFileReadAndWrite() throws Exception {
    // ==========================================
    // 阶段一：从 Classpath 加载你准备好的真实 Excel 文件
    // ==========================================
    InputStream in = getClass().getResourceAsStream("/示例表单.xlsx");
    if (in == null) {
      fail("🚨 找不到文件！请确保将 '示例表单.xlsx' 放置在 src/test/resources/ 目录下！");
    }

    // ==========================================
    // 阶段二：读取与解析
    // ==========================================
    ExcelSchema readSchema = repository.loadSchema("read_emp_real");
    List<DataRow> parsedRows = readEngine.readExcel(in, readSchema);
    in.close(); // 真实文件流记得关闭

    System.out.println("✅ 成功从真实文件中读取出 " + parsedRows.size() + " 条明细数据！");

    // 提取离散数据（主体区）和横表数据（明细区）
    Map<String, Object> discreteData = Map.of();
    List<Map<String, Object>> tableData = List.of();

    if (!parsedRows.isEmpty()) {
      discreteData = Map.of(
        "planNumber", parsedRows.get(0).data().getOrDefault("planNumber", ""),
        "planName", parsedRows.get(0).data().getOrDefault("planName", ""),
        "customerNumber", parsedRows.get(0).data().getOrDefault("customerNumber", ""),
        "customerName", parsedRows.get(0).data().getOrDefault("customerName", "")
      );
      tableData = parsedRows.stream().map(DataRow::data).toList();
    }

    // ==========================================
    // 阶段三：在工程的 target 目录下创建物理文件用于输出
    // ==========================================
    // 使用 Maven/Gradle 的默认构建输出目录 (target)，避免在项目源码里制造垃圾
    Path outputDir = Paths.get("target", "test-outputs");
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir);
    }

    File outputFile = outputDir.resolve("真实输出_示例表单_" + System.currentTimeMillis() + ".xlsx").toFile();

    // ==========================================
    // 阶段四：调用写入引擎，实打实地把字节刷入磁盘
    // ==========================================
    ExcelSchema writeSchema = repository.loadSchema("write_emp_real");

    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
      writeEngine.writeExcel(fos, writeSchema, discreteData, tableData);
    }

    // ==========================================
    // 阶段五：物理断言
    // ==========================================
    assertTrue(outputFile.exists(), "🚨 生成的 Excel 文件必须存在于磁盘上");
    assertTrue(outputFile.length() > 0, "🚨 生成的 Excel 文件体积不能为 0");

    // 在控制台高亮打印文件绝对路径，方便你直接 Ctrl + Click (或 Cmd + Click) 去打开它！
    System.out.println("======================================================");
    System.out.println("🎉 物理文件生成成功！请复制以下路径到资源管理器，或在 IDE 中直接打开查看排版效果：");
    System.out.println("📂 " + outputFile.getAbsolutePath());
    System.out.println("======================================================");
  }
}
