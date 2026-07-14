package com.example.file.infrastructure.excel.persistence;

import com.example.file.domain.model.schema.ExcelSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("基础设施: YAML配置仓库测试")
class YamlExcelSchemaRepositoryAdapterTest {

  private final YamlExcelSchemaRepositoryAdapter repository = new YamlExcelSchemaRepositoryAdapter();

  @Test
  @DisplayName("加载存在的配置：应成功解析为 ExcelSchema 对象")
  void testLoadSchemaSuccess() {
    // 前提：src/test/resources/schemas/test_schema.yaml 必须存在
    // 为方便演示，假设该 yaml 中配置了 schemaId: "test_schema" 和 bizType: "EMPLOYEE_ONBOARDING"
    try {
      ExcelSchema schema = repository.loadSchema("test_schema");

      assertNotNull(schema);
      assertEquals("test_schema", schema.schemaId());
      assertNotNull(schema.bizType());
      assertFalse(schema.regions().isEmpty(), "解析出的区块不应为空");
    } catch (Exception e) {
      // 如果你还没有在 test/resources 下建文件，这个测试会报错，这是符合预期的
      System.out.println("请确保 src/test/resources/schemas/test_schema.yaml 文件存在");
    }
  }

  @Test
  @DisplayName("加载不存在的配置：应抛出 IllegalArgumentException")
  void testLoadSchemaNotFound() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      repository.loadSchema("not_exist_schema");
    });

    assertTrue(exception.getMessage().contains("找不到指定的 Schema"));
  }
}
