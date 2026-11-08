package com.example.file.domain.gateway;

import com.example.file.domain.model.parse.valueobject.RawRowStream;
import com.example.file.domain.model.parse.valueobject.config.RegionDef;
import com.example.file.domain.model.parse.valueobject.RegionParseResult;

import java.io.InputStream;
import java.util.List;

public interface ExcelParser {
  RawRowStream openStream(InputStream excelStream);

  List<RegionParseResult> parse(InputStream excelStream, List<RegionDef> regions);
}
