package com.example.file.domain.gateway;

import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;

import java.io.InputStream;
import java.util.List;

public interface ExcelParser {
  List<RegionParseResult> parse(InputStream excelStream, List<RegionDef> regions);
}
