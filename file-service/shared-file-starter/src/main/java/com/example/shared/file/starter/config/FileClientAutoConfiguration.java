package com.example.shared.file.starter.config;

import com.example.shared.file.api.FileApi;
import com.example.shared.file.starter.template.FileTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class FileClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public FileTemplate fileTemplate(FileApi fileApi) {
    return new FileTemplate(fileApi);
  }

}
