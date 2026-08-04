package com.example.file.domain.service;

import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.CanonicalData;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalModelBuilderTest {

  @Test
  void should_build_canonical_data_from_kv_and_table_regions() {
    List<RegionDef> defs = List.of(
      new RegionDef("header", RegionType.KEY_VALUE, "properties", null,
        new KvStrategy(KvValuePosition.RIGHT, Map.of(), 3)),
      new RegionDef("items", RegionType.TABLE, "items", null,
        new TableStrategy(1, 0, null, Map.of(), null, 0, null))
    );
    List<RegionParseResult> regions = List.of(
      new KvRegionResult("header", Map.of("applicant", "张三", "idCard", "110")),
      new TableRegionResult("items", List.of("code", "qty"), List.of(
        Map.of("code", "A1", "qty", "5"),
        Map.of("code", "A2", "qty", "10")))
    );

    CanonicalModelBuilder builder = new CanonicalModelBuilder();
    CanonicalData data = builder.build(regions, defs);

    assertThat(data.properties()).containsEntry("applicant", "张三");
    assertThat(data.properties()).containsEntry("idCard", "110");
    assertThat(data.tables()).containsKey("items");
    assertThat(data.tables().get("items")).hasSize(2);
  }

  @Test
  void should_skip_region_without_bindTo() {
    List<RegionDef> defs = List.of(
      new RegionDef("header", RegionType.KEY_VALUE, null, null,
        new KvStrategy(KvValuePosition.RIGHT, Map.of(), 3))
    );
    List<RegionParseResult> regions = List.of(
      new KvRegionResult("header", Map.of("k", "v"))
    );

    CanonicalModelBuilder builder = new CanonicalModelBuilder();
    CanonicalData data = builder.build(regions, defs);

    assertThat(data.properties()).isEmpty();
    assertThat(data.tables()).isEmpty();
  }
}
