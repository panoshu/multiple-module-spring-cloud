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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamingDeduplicationServiceTest {

  @Mock
  private DeduplicationPort deduplicationPort;

  private StreamingDeduplicationService service;
  private DeduplicationConfig config;

  @BeforeEach
  void setUp() {
    service = new StreamingDeduplicationService(deduplicationPort);
    config = new DeduplicationConfig(true, List.of("phone"), "1d");
  }

  @Test
  void testStreamingDeduplication() {
    // 第一行正常
    DataRow row1 = new DataRow(1, new HashMap<>(Map.of("phone", "13800")));
    service.processDeduplication(row1, config);
    assertFalse(row1.hasErrors());

    // 第二行手机号相同，发生内部冲突
    DataRow row2 = new DataRow(2, new HashMap<>(Map.of("phone", "13800")));
    service.processDeduplication(row2, config);
    assertTrue(row2.hasErrors());
    assertEquals(1, row2.errors().get(0).conflictRowIndex());

    // 第三行不重复，但外部接口返回冲突
    when(deduplicationPort.checkAndLockInterFileDuplicate("BIZ", "13900", "1d")).thenReturn(true);
    DataRow row3 = new DataRow(3, new HashMap<>(Map.of("phone", "13900")));
    service.processDeduplication(row3, config);
    assertTrue(row3.hasErrors());
    assertEquals("历史数据已存在", row3.errors().get(0).message());
  }
}
