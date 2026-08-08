package com.example.bff.shared.config;

import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.bff.shared.route.BffRouteConfigRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * BFF 公共组件自动配置
 *
 * <p>注册 {@link BusinessTypeRouter} 和 {@link KernelApiRegistry}。
 * {@link BffRouteConfigRepository} 由各 BFF 的 infrastructure 层提供实现。
 *
 * <p>{@code @LoadBalanced RestClient.Builder} 由 Spring Cloud LoadBalancer 的
 * {@code LoadBalancerRestClientAutoConfiguration} 自动注册，无需在此手动创建。
 *
 * @author bff
 */
@AutoConfiguration
public class BffAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(BffRouteConfigRepository.class)
    public BusinessTypeRouter businessTypeRouter(
            BffRouteConfigRepository routeConfigRepository,
            Environment environment) {
        String channelScope = environment.getProperty("bff.channel-scope", "ALL");
        return new BusinessTypeRouter(routeConfigRepository, channelScope);
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelApiRegistry kernelApiRegistry(@LoadBalanced RestClient.Builder restClientBuilder) {
        return new KernelApiRegistry(restClientBuilder);
    }
}
