package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import com.example.gateway.config.GatewayChannelProperties;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.order.GatewayFilterOrder;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * sa-token 网关层集成配置 - 注册 SaReactorFilter 完成动态鉴权.
 *
 * <p>对应设计文档 3.2 节，在 WebFlux 网关注册 SaReactorFilter，实现：
 * <ol>
 *   <li>yml 白名单放行（从 GatewayProperties 读取，替代硬编码 addExclude）</li>
 *   <li>渠道前缀路径（/internet, /hq, /branch）→ 对应启用渠道 StpLogic.checkLogin()</li>
 *   <li>非渠道前缀路径（/admin/** 等）→ 默认 StpUtil.checkLogin()（识别本网关启用渠道 token）</li>
 *   <li>统一异常响应：NotLoginException → 401, NotPermission/RoleException → 403</li>
 * </ol>
 *
 * <p>启用渠道由 {@link GatewayChannelProperties} 配置驱动，各网关按需注册。
 *
 * @author auth-service
 * @since 2026/7/26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({GatewayProperties.class, GatewayChannelProperties.class, GatewaySessionProperties.class})
public class SaTokenGatewayConfiguration {

    private static final String CODE_NOT_LOGIN = "COMMON.0002";
    private static final String CODE_NO_PERMISSION = "COMMON.0003";
    private static final String CODE_INTERNAL_ERROR = "COMMON.0050";

    private static final int FILTER_ORDER_AUTH = -200;

    private final ChannelAwareSaRouter channelAwareSaRouter;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    /**
     * 启动时配置默认 StpLogic 识别本网关启用渠道 token.
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

                // 2. 启用渠道前缀路径 → 对应渠道 StpLogic 登录校验
                ChannelType channel = channelAwareSaRouter.matchChannel(path);
                if (channel != null) {
                    channelAwareSaRouter.getStpLogic(channel).checkLogin();
                    return;
                }

                // 3. 非渠道前缀路径 → 默认 StpLogic 登录校验（识别本网关启用渠道 token）
                StpUtil.checkLogin();
            })
            .setError(this::handleError);
    }

    /**
     * 统一异常处理:将 sa-token 异常转换为 ApiResult 响应.
     *
     * <p>直接通过 Spring 的 {@link ServerHttpResponse} 设置状态码，绕过 sa-token 1.45.0
     * 的 {@code SaResponseForReactor.setStatus(int)} —— 该方法调用
     * {@code ServerHttpResponse.setStatusCode(HttpStatus)}，在 Spring Framework 6.2+
     * 中方法签名已变更为 {@code setStatusCode(HttpStatusCode)}，运行时会抛
     * {@link NoSuchMethodError}，被 Reactor 视为致命异常导致响应无法返回（客户端超时）。
     *
     * <p>返回值为 JSON 字符串，由 {@code SaReactorFilter} 的 {@code writeResult} 写入响应体。
     */
    private Object handleError(Throwable e) {
        ServerHttpResponse response = (ServerHttpResponse) SaHolder.getResponse().getSource();
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        if (e instanceof NotLoginException) {
            response.setStatusCode(HttpStatusCode.valueOf(401));
            log.warn("[SaTokenGateway] 未登录访问: {}", e.getMessage());
            return toJson(ApiResult.failure(CODE_NOT_LOGIN, "未登录或登录已过期"));
        }
        if (e instanceof NotPermissionException || e instanceof NotRoleException) {
            response.setStatusCode(HttpStatusCode.valueOf(403));
            log.warn("[SaTokenGateway] 权限不足: {}", e.getMessage());
            return toJson(ApiResult.failure(CODE_NO_PERMISSION, "无权限访问"));
        }
        response.setStatusCode(HttpStatusCode.valueOf(500));
        log.error("[SaTokenGateway] 鉴权异常", e);
        return toJson(ApiResult.failure(CODE_INTERNAL_ERROR, "系统内部错误"));
    }

    /**
     * 将对象序列化为 JSON 字符串.
     *
     * @param obj 待序列化对象
     * @return JSON 字符串；序列化失败时返回兜底错误 JSON
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.error("[SaTokenGateway] 响应 JSON 序列化失败", ex);
            return "{\"code\":\"" + CODE_INTERNAL_ERROR + "\",\"message\":\"系统内部错误\",\"data\":null}";
        }
    }
}