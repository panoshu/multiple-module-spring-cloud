package com.example.file.domain.service;

import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.config.SplitConfig;
import com.example.file.domain.model.valueobject.config.SplitKeyDef;
import com.example.shared.domain.annotation.DomainService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DomainService
public class TaskSplitter {

  @SuppressWarnings("unchecked")
  public List<SplitUnit> split(Map<String, Object> data, SplitConfig config) {
    SplitKeyDef keyDef = config.splitKey();
    if (keyDef == null) {
      return List.of(new SplitUnit("default", new LinkedHashMap<>(data)));
    }

    String sourcePath = keyDef.sourcePath();
    int dot = sourcePath.indexOf('.');
    if (dot < 0) {
      return List.of(new SplitUnit("default", new LinkedHashMap<>(data)));
    }
    String regionName = sourcePath.substring(0, dot);
    String field = sourcePath.substring(dot + 1);

    Object regionData = data.get(regionName);
    if (!(regionData instanceof List<?> rows)) {
      return List.of(new SplitUnit(String.valueOf(data.getOrDefault(field, "default")), new LinkedHashMap<>(data)));
    }

    Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
    for (Object r : rows) {
      if (!(r instanceof Map<?, ?> row)) continue;
      Object k = row.get(field);
      String key = k != null ? k.toString() : "default";
      grouped.computeIfAbsent(key, x -> new ArrayList<>())
        .add((Map<String, Object>) row);
    }
    int limit = config.maxRowsPerSubTask() > 0 ? config.maxRowsPerSubTask() : Integer.MAX_VALUE;

    List<SplitUnit> result = new ArrayList<>();
    for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
      List<Map<String, Object>> bucket = e.getValue();
      for (int i = 0; i < bucket.size(); i += limit) {
        List<Map<String, Object>> chunk = bucket.subList(i, Math.min(i + limit, bucket.size()));
        Map<String, Object> subData = new LinkedHashMap<>(data);
        subData.put(regionName, new ArrayList<>(chunk));
        result.add(new SplitUnit(e.getKey(), subData));
      }
    }
    return result;
  }
}
