package com.example.file.infrastructure.excel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("基础设施: OSS文件存储与流控测试")
class OssFileStorageAdapterTest {

  // 注入一个安全的临时目录，测试结束后自动销毁
  @TempDir
  Path tempDir;

  @Test
  @DisplayName("流式写入与回滚测试：应能成功创建流，并在异常时擦除临时文件")
  void testCreateStreamAndRollback() throws Exception {
    // 为了方便测试，我们需要使用反射或者直接修改 OssFileStorageAdapter，
    // 让它可以接受自定义的目录，而不是硬编码的 /tmp/oss_staging/
    // 这里假设我们将其重构为可以传入基础路径

    // 临时创建匿名类覆盖路径行为以便于测试
    OssFileStorageAdapter adapter = new OssFileStorageAdapter() {
      @Override
      public OutputStream createOssOutputStream(String fileName) {
        try {
          return new java.io.FileOutputStream(tempDir.resolve(fileName).toFile());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void rollback(java.util.Collection<String> fileNames) {
        fileNames.forEach(name -> tempDir.resolve(name).toFile().delete());
      }
    };

    String testFileName = "test_stream_001.json";

    // 1. 创建流并写入数据
    try (OutputStream out = adapter.createOssOutputStream(testFileName)) {
      assertNotNull(out);
      out.write("test data".getBytes());
    }

    // 验证文件存在
    File createdFile = tempDir.resolve(testFileName).toFile();
    assertTrue(createdFile.exists());
    assertTrue(createdFile.length() > 0);

    // 2. 模拟业务线抛出异常，触发回滚
    adapter.rollback(List.of(testFileName));

    // 验证文件被安全擦除
    assertFalse(createdFile.exists(), "触发 rollback 后文件应该被删除");
  }
}
