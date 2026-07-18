package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.TableMatchBy;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;
import java.util.Map;

public record TableStrategy(
    int headerRows,
    TableMatchBy matchBy,
    Map<String, List<String>> headerAliases,
    HeaderMatching headerMatching,
    int maxRows,
    DataEndRule dataEnd
) implements RegionStrategy, ValueObject {
  public TableStrategy {
    if (headerRows <= 0) headerRows = 1;
    matchBy = matchBy == null ? TableMatchBy.HEADER_NAME : matchBy;
    headerAliases = headerAliases == null ? Map.of() : Map.copyOf(headerAliases);
    headerMatching = headerMatching == null ? HeaderMatching.STRICT : headerMatching;
    if (maxRows < 0) maxRows = 0;
  }
}
