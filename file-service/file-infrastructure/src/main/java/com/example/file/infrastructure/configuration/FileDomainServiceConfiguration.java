package com.example.file.infrastructure.configuration;

import com.example.shared.domain.annotation.DomainService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

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
}
