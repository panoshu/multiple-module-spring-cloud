package com.example.file.domain.service;

import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.parse.valueobject.RawRowStream;
import com.example.file.domain.model.parse.valueobject.config.RegionDef;
import com.example.file.domain.model.parse.valueobject.RegionParseResult;

public interface RegionParser {
  RegionType supportedType();

  RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx);
}
