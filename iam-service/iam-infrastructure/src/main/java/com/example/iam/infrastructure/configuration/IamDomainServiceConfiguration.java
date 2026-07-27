package com.example.iam.infrastructure.configuration;

import com.example.shared.domain.annotation.DomainService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * IAM 领域服务 IoC 注册配置。
 * <p>
 * 通过 {@code @ComponentScan} 扫描 {@code com.example.iam} 包下标注
 * {@link DomainService} 的类,使其被 Spring 容器管理。
 * <p>
 * IAM 服务不依赖 business-core-kernel,需独立维护领域服务的注册入口。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Configuration
@ComponentScan(
    basePackages = {"com.example.iam"},
    includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainService.class)})
public class IamDomainServiceConfiguration {
}
