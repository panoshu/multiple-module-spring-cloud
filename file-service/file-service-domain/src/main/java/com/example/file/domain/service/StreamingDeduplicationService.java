package com.example.file.domain.service;

import com.example.file.domain.gateway.DeduplicationPort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.DeduplicationConfig;
import com.example.file.domain.model.schema.ValidationError;

import java.util.HashMap;
import java.util.Map;

public class StreamingDeduplicationService {

  // 内存去重表，适合流式处理
  private final Map<String, Integer> seenKeys = new HashMap<>();
  private final DeduplicationPort deduplicationPort;

  public StreamingDeduplicationService(DeduplicationPort port) {
    this.deduplicationPort = port;
  }

  public void processDeduplication(DataRow row, DeduplicationConfig config) {
    if (!config.enabled() || config.uniqueKeys().isEmpty()) {
      return;
    }

    String uniqueKey = config.uniqueKeys().stream()
      .map(k -> String.valueOf(row.data().getOrDefault(k, "")))
      .reduce("", String::concat);

    // 1. 本地查重
    Integer conflictRow = seenKeys.putIfAbsent(uniqueKey, row.rowIndex());
    if (conflictRow != null) {
      row.errors().add(new ValidationError(row.rowIndex(), "GLOBAL", "文件内部数据重复", conflictRow));
      return;
    }

    // 2. 外部查重
    if (deduplicationPort.checkAndLockInterFileDuplicate("BIZ", uniqueKey, config.ttl())) {
      row.errors().add(new ValidationError(row.rowIndex(), "GLOBAL", "历史数据已存在", null));
    }
  }
}
