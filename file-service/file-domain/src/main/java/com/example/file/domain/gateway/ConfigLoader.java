package com.example.file.domain.gateway;

import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.types.BizType;

import java.util.List;

public interface ConfigLoader {
  TemplateConfig loadFromYaml(BizType bizType, String baselineYaml,
                              List<String> sourceTemplateYamls, String version,
                              com.example.shared.primitives.identity.UserNo operator);
}
