package com.example.file.domain.service;

import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.SplitStrategyConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SplitService {

  public Map<String, List<Map<String, Object>>> splitData(List<DataRow> rows, ExcelSchema schema) {
    SplitStrategyConfig config = schema.splitStrategy();

    // 提取需要最终导出的业务数据（附带行号 _rowIndex）
    List<Map<String, Object>> exportableData = rows.stream()
      .map(DataRow::getExportableData)
      .toList();

    // 如果未开启拆分，直接返回全量文件
    if (config == null || !config.enabled()) {
      return Map.of(schema.bizType().name() + "_all.json", exportableData);
    }

    // 核心拆分逻辑：根据指定的维度动态分组
    return exportableData.stream().collect(Collectors.groupingBy(dataMap -> {

      // 提取拆分值（例如将 idType 和 idCard 的值拼接作为维度）
      String splitValue = config.splitBy().stream()
        .map(key -> String.valueOf(dataMap.getOrDefault(key, "UNKNOWN")))
        .collect(Collectors.joining("_"));

      // 根据配置模板动态渲染文件名
      return config.outputNaming()
        .replace("${bizType}", schema.bizType().name())
        .replace("${splitValue}", splitValue);
    }));
  }
}
