package com.example.core.infrastructure.engine.configuration;

import com.example.shared.domain.annotation.DomainService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 基于 Spring 的 IoC 管理机制注入领域服务
 * <p>
 * 通过 {@code @ComponentScan} 扫描 {@code com.example.core} 包下标注
 * {@link DomainService} 的类，使其被 Spring 容器管理。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 12:29
 */
@Configuration
@ComponentScan(
  basePackages = {"com.example.core"},
  includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainService.class)})
public class DomainServiceConfiguration {
}
