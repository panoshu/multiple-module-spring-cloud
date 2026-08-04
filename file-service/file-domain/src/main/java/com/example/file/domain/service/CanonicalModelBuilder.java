package com.example.file.domain.service;

import com.example.file.domain.model.valueobject.CanonicalData;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import com.example.shared.domain.annotation.DomainService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DomainService
public class CanonicalModelBuilder {

  public CanonicalData build(List<RegionParseResult> regions, List<RegionDef> regionDefs) {
    CanonicalData data = CanonicalData.empty();
    Map<String, RegionDef> defByName = new LinkedHashMap<>();
    for (RegionDef def : regionDefs) defByName.put(def.name(), def);

    for (RegionParseResult result : regions) {
      RegionDef def = defByName.get(result.regionName());
      if (def == null) continue;
      String bindTo = def.bindTo();
      if (bindTo == null || bindTo.isBlank()) continue;

      if (result instanceof KvRegionResult kv) {
        if ("properties".equals(bindTo)) {
          kv.data().forEach(data::setProperty);
        }
      } else if (result instanceof TableRegionResult table) {
        data.tables().put(bindTo, new ArrayList<>(table.rows()));
      }
    }
    return data;
  }
}
