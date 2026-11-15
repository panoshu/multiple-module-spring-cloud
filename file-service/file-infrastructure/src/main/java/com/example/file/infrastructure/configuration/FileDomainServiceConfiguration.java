package com.example.file.infrastructure.configuration;

import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.service.KeyValueRegionParser;
import com.example.file.domain.service.RegionStateMachine;
import com.example.file.domain.service.TableRegionParser;
import com.example.shared.domain.annotation.DomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.Map;

/**
 * 文件服务领域服务扫描配置
 * <p>
 * kernel 的 {@code DomainServiceConfiguration} 扫描 {@code com.example.core} 包，
 * 需在此补充扫描 {@code com.example.file} 包，使标注 {@link DomainService} 的
 * 文件领域服务（如 {@link com.example.file.domain.service.FileTokenService}）
 * 能被 Spring 容器管理。
 *
 * @author file-service
 * @since 2026/7/23
 */
@Configuration
@ComponentScan(
  basePackages = {"com.example.file"},
  includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainService.class)})
public class FileDomainServiceConfiguration {

  /**
   * 区域状态机显式装配：Map 的 key 是枚举，Spring 无法按 Map&lt;Enum, Bean&gt; 自动注入，
   * 不显式装配时会得到一个空 Map，解析阶段报 "No parser for region type".
   */
  @Bean
  public RegionStateMachine regionStateMachine(KeyValueRegionParser keyValueRegionParser,
                                               TableRegionParser tableRegionParser) {
    return new RegionStateMachine(Map.of(
      RegionType.KEY_VALUE, keyValueRegionParser,
      RegionType.TABLE, tableRegionParser));
  }
}
