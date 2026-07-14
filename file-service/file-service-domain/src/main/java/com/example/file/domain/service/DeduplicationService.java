package com.example.file.domain.service;

import com.example.file.domain.gateway.DeduplicationPort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.DeduplicationConfig;
import com.example.file.domain.model.schema.ValidationError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeduplicationService {

  private final DeduplicationPort deduplicationPort;

  public DeduplicationService(DeduplicationPort deduplicationPort) {
    this.deduplicationPort = deduplicationPort;
  }

  public void processDeduplication(List<DataRow> rows, DeduplicationConfig config) {
    if (!config.enabled() || config.uniqueKeys().isEmpty()) {
      return;
    }

    // Key: 联合主键, Value: 首次出现的行号
    Map<String, Integer> localSeenKeys = new HashMap<>();

    for (DataRow row : rows) {
      String uniqueKey = config.uniqueKeys().stream()
        .map(k -> String.valueOf(row.data().getOrDefault(k, "")))
        .reduce("", String::concat);

      // 1. 文件内查重
      Integer conflictRow = localSeenKeys.putIfAbsent(uniqueKey, row.rowIndex());
      if (conflictRow != null) {
        row.errors().add(new ValidationError(
          row.rowIndex(), "GLOBAL",
          "文件内部存在重复数据: " + uniqueKey,
          conflictRow
        ));
        continue;
      }

      // 2. 文件间 (外部系统) 查重
      if (deduplicationPort.checkAndLockInterFileDuplicate("BIZ", uniqueKey, config.ttl())) {
        row.errors().add(new ValidationError(
          row.rowIndex(), "GLOBAL",
          "历史数据已存在该记录: " + uniqueKey,
          null
        ));
      }
    }
  }
}
