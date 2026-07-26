package com.example.core.configuration;

import com.example.core.adapter.context.BusinessMetaContextAssembler;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.guard.DefaultBusinessAccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * business-core-kernel 自动配置
 *
 * <p>业务服务引入 {@code business-core-starter} 或 {@code business-core-adapter} 后,
 * 自动注册 kernel 组件:
 * <ul>
 *   <li>SessionContextResolver / BusinessMetaContextAssembler(会话基础设施)</li>
 *   <li>DefaultBusinessAccessGuard(默认权限守门人,业务服务可覆盖)</li>
 *   <li>5 类公共 API 的 Controller(Batch/Form/Application/Material/Progress)</li>
 *   <li>@RequireBusinessPermission 的 AOP 切面</li>
 *   <li>SupportedBusinessTypeValidator(业务类型校验)</li>
 * </ul>
 *
 * <p>业务服务只需:
 * <ol>
 *   <li>提供 {@link com.example.core.api.registrar.BusinessTypeRegistrar} Bean 声明支持的业务类型</li>
 *   <li>(可选)提供自定义 {@link BusinessAccessGuard} 覆盖默认实现</li>
 * </ol>
 *
 * <p>后续新增自动注册组件流程:
 * <ol>
 *   <li>在对应模块标注 {@code @Component}/{@code @Service}/{@code @RestController}</li>
 *   <li>本类的 {@code @ComponentScan} 自动扫描 {@code com.example.core} 包</li>
 *   <li>需要兜底默认实现的,在本类添加 {@code @Bean} + {@code @ConditionalOnMissingBean} 方法</li>
 * </ol>
 *
 * @author panoshu
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.example.core")
public class CoreKernelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SessionContextResolver sessionContextResolver(ObjectMapper objectMapper) {
        return new SessionContextResolver(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public BusinessMetaContextAssembler businessMetaContextAssembler() {
        return new BusinessMetaContextAssembler();
    }

    @Bean
    @ConditionalOnMissingBean(BusinessAccessGuard.class)
    public BusinessAccessGuard defaultBusinessAccessGuard() {
        return new DefaultBusinessAccessGuard();
    }
}
