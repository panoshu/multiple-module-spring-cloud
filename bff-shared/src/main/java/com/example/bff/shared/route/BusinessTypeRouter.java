package com.example.bff.shared.route;

import com.example.bff.shared.errorcode.BffErrorCode;
import com.example.shared.exception.BusinessException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

/**
 * 业务类型路由器
 *
 * <p>将请求中的 businessType 解析为目标服务名，带 Caffeine 本地缓存。
 * 缓存 TTL 5 分钟，调用 {@link #refresh()} 可主动刷新。
 *
 * <p>Bean 由 {@code BffAutoConfiguration} 通过 {@code @Bean} 注册，
 * 不使用 {@code @Service} 以避免与 {@code @ConditionalOnBean} 条件冲突。
 *
 * @author bff
 */
public class BusinessTypeRouter {

    private final BffRouteConfigRepository routeRepo;
    private final ChannelScope currentScope;
    private final Cache<String, String> routeCache;

    public BusinessTypeRouter(
            BffRouteConfigRepository routeRepo,
            @Value("${bff.channel-scope:ALL}") String channelScope) {
        this.routeRepo = routeRepo;
        this.currentScope = ChannelScope.valueOf(channelScope);
        this.routeCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 解析业务类型对应的服务名。
     *
     * @param businessType 业务类型
     * @return 目标服务名
     * @throws BusinessException 未找到路由配置
     */
    public String resolveServiceName(String businessType) {
        return routeCache.get(businessType, key -> {
            BffRouteConfig config = routeRepo.findByBusinessType(key, currentScope)
                    .orElseThrow(() -> new BusinessException(BffErrorCode.ROUTE_NOT_FOUND)
                            .withUserDetail("未找到业务类型路由: " + key));
            return config.serviceName();
        });
    }

    /**
     * 刷新路由缓存。
     */
    public void refresh() {
        routeCache.invalidateAll();
    }
}
