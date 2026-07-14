package com.example.file.domain.service;

import com.example.file.domain.gateway.DeduplicationPort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.DeduplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

  @Mock
  private DeduplicationPort deduplicationPort;

  private DeduplicationService service;
  private DeduplicationConfig config;

  @BeforeEach
  void setUp() {
    service = new DeduplicationService(deduplicationPort);
    config = new DeduplicationConfig(true, List.of("idCard"), "1d");
  }

  @Test
  void testBatchDeduplication() {
    // 使用 lenient() 放开 Mockito 的严格检查限制
    // 当传入 999 时返回 true，传入其他（如 123）时默认返回 false
    org.mockito.Mockito.lenient()
      .when(deduplicationPort.checkAndLockInterFileDuplicate("BIZ", "999", "1d"))
      .thenReturn(true);
    org.mockito.Mockito.lenient()
      .when(deduplicationPort.checkAndLockInterFileDuplicate("BIZ", "123", "1d"))
      .thenReturn(false);

    DataRow row1 = new DataRow(1, new HashMap<>(Map.of("idCard", "123")));
    DataRow row2 = new DataRow(2, new HashMap<>(Map.of("idCard", "123"))); // 文件内重复
    DataRow row3 = new DataRow(3, new HashMap<>(Map.of("idCard", "999"))); // 外部重复

    service.processDeduplication(List.of(row1, row2, row3), config);

    assertFalse(row1.hasErrors());

    assertTrue(row2.hasErrors());
    assertEquals(1, row2.errors().getFirst().conflictRowIndex(), "应该指明与第1行冲突");

    assertTrue(row3.hasErrors());
    assertEquals("历史数据已存在该记录: 999", row3.errors().getFirst().message());
  }
}
