package com.example.file.domain.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.IdentifyMode;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.parse.RawRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@DomainService
public class SourceTemplateIdentifier {

  private static final int DEFAULT_SCAN_ROWS = 10;

  public Optional<SourceTemplateDef> identify(TemplateConfig config, RawRowStream stream) {
    return identify(config, stream, DEFAULT_SCAN_ROWS);
  }

  public Optional<SourceTemplateDef> identify(TemplateConfig config, RawRowStream stream, int scanRows) {
    List<String> headers = extractHeaders(stream, scanRows);
    if (headers.isEmpty()) return Optional.empty();

    SourceTemplateDef best = null;
    double bestRatio = 0.0;

    for (SourceTemplateDef def : config.sourceTemplates()) {
      if (def.identifyMode() != IdentifyMode.AUTO) continue;
      List<String> fp = def.fingerprint();
      if (fp.isEmpty()) continue;

      long matchCount = fp.stream().filter(headers::contains).count();
      double ratio = (double) matchCount / fp.size();
      double threshold = 0.5;

      if (ratio >= threshold && ratio > bestRatio) {
        bestRatio = ratio;
        best = def;
      }
    }
    return Optional.ofNullable(best);
  }

  private List<String> extractHeaders(RawRowStream stream, int scanRows) {
    List<String> result = new ArrayList<>();
    int count = 0;
    while (stream.hasNext() && count < scanRows) {
      RawRow row = stream.next();
      if (row.isBlank()) continue;
      for (String cell : row.cells().values()) {
        if (cell != null && !cell.isBlank()) {
          result.add(cell.trim());
        }
      }
      count++;
    }
    return result;
  }
}
