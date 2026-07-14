package com.example.core.infrastructure.configuration;

import com.example.shared.domain.annotation.DomainService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 基于 Spring 的 IoC 管理机制注入领域服务
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 12:29
 */
@ComponentScan(
  basePackages = {"com.example.core", "com.example.business"},
  includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainService.class)})
public class DomainServiceConfiguration {
}
