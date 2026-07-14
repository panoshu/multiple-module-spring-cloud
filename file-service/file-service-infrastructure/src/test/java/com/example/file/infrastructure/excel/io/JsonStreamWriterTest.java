package com.example.file.infrastructure.excel.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("基础设施: 流式 JSON 写出器测试")
class JsonStreamWriterTest {

  @Test
  @DisplayName("流式写入测试：应合法生成包含多行的 JSON 数组")
  void testJsonStreamWriting() throws Exception {
    // 1. 准备内存输出流模拟 OSS 接收端
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    // 2. 初始化 JsonStreamWriter
    JsonStreamWriter writer = new JsonStreamWriter(out);

    // 3. 准备测试数据 (使用 LinkedHashMap 保证 JSON 键值对顺序，方便断言)
    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("idCard", "123456");
    row1.put("name", "Alice");

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("idCard", "654321");
    row2.put("name", "Bob");
    row2.put("age", 25);

    // 4. 模拟流式写入
    writer.write(row1);
    writer.write(row2);

    // 5. 关闭流 (这一步极其关键，会写入 ']')
    writer.close();

    // 6. 验证生成的完整 JSON 字符串
    String resultJson = out.toString(StandardCharsets.UTF_8);
    String expectedJson = "[{\"idCard\":\"123456\",\"name\":\"Alice\"},{\"idCard\":\"654321\",\"name\":\"Bob\",\"age\":25}]";

    assertEquals(expectedJson, resultJson, "生成的 JSON 必须是合法的数组对象结构");
  }
}
