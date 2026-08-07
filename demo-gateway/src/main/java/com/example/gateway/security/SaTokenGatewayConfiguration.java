package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import com.example.gateway.order.GatewayFilterOrder;
import com.example.shared.web.core.api.ApiResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * sa-token 网关层集成配置 - 注册 SaReactorFilter 完成动态鉴权.
 *
 * <p>对应设计文档 3.2 节，在 WebFlux 网关注册 SaReactorFilter，实现：
 * <ol>
 *   <li>yml 白名单放行（从 GatewayProperties 读取，替代硬编码 addExclude）</li>
 *   <li>渠道前缀路径（/internet, /hq, /branch）→ 对应渠道 StpLogic.checkLogin()</li>
 *   <li>非渠道前缀路径（/admin/** 等）→ 默认 StpUtil.checkLogin()（识别任一渠道 token）</li>
 *   <li>统一异常响应：NotLoginException → 401, NotPermission/RoleException → 403</li>
 * </ol>
 *
 * @author auth-service
 * @since 2026/7/26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GatewayProperties.class)
public class SaTokenGatewayConfiguration {

    private static final String CODE_NOT_LOGIN = "COMMON.0002";
    private static final String CODE_NO_PERMISSION = "COMMON.0003";
    private static final String CODE_INTERNAL_ERROR = "COMMON.0050";

    private static final int FILTER_ORDER_AUTH = -200;

    private final ChannelAwareSaRouter channelAwareSaRouter;
    private final GatewayProperties gatewayProperties;

    /**
     * 启动时配置默认 StpLogic 识别所有渠道 token.
     */
    @PostConstruct
    public void initDefaultStpLogic() {
        channelAwareSaRouter.configureDefaultStpLogic();
    }

    /**
     * 注册 SaReactorFilter,在 WebFlux 过滤器链中执行 sa-token 鉴权.
     *
     * @return SaReactorFilter 实例
     */
    @Bean
    @Order(FILTER_ORDER_AUTH)
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .setAuth(obj -> {
                String path = SaHolder.getRequest().getRequestPath();

                // 1. yml 白名单放行（从 GatewayProperties 读取）
                if (gatewayProperties.isPublicPath(path)) {
                    return;
                }

                // 2. 渠道前缀路径 → 对应渠道 StpLogic 登录校验
                ChannelType channel = channelAwareSaRouter.matchChannel(path);
                if (channel != null) {
                    channelAwareSaRouter.getStpLogic(channel).checkLogin();
                    return;
                }

                // 3. 非渠道前缀路径 → 默认 StpLogic 登录校验（识别任一渠道 token）
                StpUtil.checkLogin();
            })
            .setError(this::handleError);
    }

    /**
     * 统一异常处理:将 sa-token 异常转换为 ApiResult 响应.
     */
    private Object handleError(Throwable e) {
        if (e instanceof NotLoginException) {
            SaHolder.getResponse().setStatus(401);
            log.warn("[SaTokenGateway] 未登录访问: {}", e.getMessage());
            return ApiResult.failure(CODE_NOT_LOGIN, "未登录或登录已过期");
        }
        if (e instanceof NotPermissionException || e instanceof NotRoleException) {
            SaHolder.getResponse().setStatus(403);
            log.warn("[SaTokenGateway] 权限不足: {}", e.getMessage());
            return ApiResult.failure(CODE_NO_PERMISSION, "无权限访问");
        }
        SaHolder.getResponse().setStatus(500);
        log.error("[SaTokenGateway] 鉴权异常", e);
        return ApiResult.failure(CODE_INTERNAL_ERROR, "系统内部错误");
    }
}
