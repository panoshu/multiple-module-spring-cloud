package com.example.iam.infrastructure;

import com.example.iam.infrastructure.test.TestIdGenerationInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * iam-infrastructure 集成测试专用 Spring Boot 启动类。
 *
 * <p>仅扫描 {@code com.example.iam.infrastructure} 包及其子包,加载:
 * <ul>
 *   <li>Repository 实现({@code @Repository})</li>
 *   <li>Converter({@code @Mapper(componentModel = "spring")})</li>
 *   <li>DomainEventPublisher({@code @Component})</li>
 *   <li>Mock Gateway 实现({@code @Component})</li>
 *   <li>IamDomainServiceConfiguration({@code @Configuration},触发 @DomainService 注册)</li>
 * </ul>
 *
 * <p>不加载 adapter / application / starter 层 Bean,避免引入 sa-token / Nacos 等依赖。
 * 通过 @ActiveProfiles("test") 激活 H2 内存数据库与禁用 Redis/Nacos 的配置。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@SpringBootApplication(scanBasePackages = "com.example.iam.infrastructure")
@MapperScan("com.example.iam.infrastructure.mapper")
public class IamInfrastructureTestApplication {

    /**
     * 通过 {@link BeanPostProcessor} 在 {@link SqlSessionFactory} 创建完成后注入
     * 测试专用 ID 生成拦截器,确保 MyBatis-Flex 的 BaseMapper.insert 调用链会经过该拦截器。
     *
     * <p>背景:子表 DO(BusinessActionDO/PlanDelegationOperatorDO 等)的
     * {@code @Id(keyType = KeyType.None)} 且 Converter 对 id 字段 ignore,
     * 正式环境通过应用层 IdService 生成 ID,但测试环境 IdService 不可用。
     */
    @Bean
    public static BeanPostProcessor testInterceptorInjector() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof SqlSessionFactory sqlSessionFactory) {
                    sqlSessionFactory.getConfiguration()
                            .addInterceptor(new TestIdGenerationInterceptor());
                }
                return bean;
            }
        };
    }
}

