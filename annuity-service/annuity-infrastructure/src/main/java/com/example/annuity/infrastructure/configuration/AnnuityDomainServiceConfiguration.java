package com.example.annuity.infrastructure.configuration;

import com.example.shared.domain.annotation.DomainService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 年金服务领域服务扫描配置
 * <p>
 * kernel 的 {@code DomainServiceConfiguration} 扫描 {@code com.example.core} 包，
 * 需在此补充扫描 {@code com.example.annuity} 包，使标注 {@link DomainService} 的
 * 年金领域服务（如 {@link com.example.annuity.domain.extractor.AnnuityFactExtractor}）
 * 能被 Spring 容器管理。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Configuration
@ComponentScan(
  basePackages = {"com.example.annuity"},
  includeFilters = {@ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ANNOTATION, value = DomainService.class)})
public class AnnuityDomainServiceConfiguration {
}
