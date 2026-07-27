package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import com.example.gateway.order.GatewayFilterOrder;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * sa-token 网关层集成配置 - 注册 SaReactorFilter 完成动态鉴权。
 *
 * <p>对应设计文档 4.5 节:在 WebFlux 网关注册 SaReactorFilter,实现:
 * <ol>
 *   <li>渠道识别 + 登录校验:基于路径前缀(/internet, /hq, /branch)分派到对应 StpLogic</li>
 *   <li>动态路由权限校验:从 iam-service 加载 RouteRule,按 priority 倒序匹配请求路径,
 *       命中后按 checkType(LOGIN/PERMISSION/ROLE/CHANNEL/SKIP)执行对应校验</li>
 *   <li>统一异常响应:NotLoginException → 401, NotPermission/RoleException → 403</li>
 * </ol>
 *
 * <p>白名单:登录接口、二次授权发起/确认接口、actuator、favicon 等公共路径不经过鉴权。
 * 渠道路径(/internet/**, /hq/**, /branch/**)外的其他路径视为公共接口,跳过登录校验。
 *
 * <p>避免使用默认 StpUtil:sa-token 的 StpUtil 是默认 StpLogic 的快捷方式,无法识别多渠道。
 * 所有权限/角色校验通过 {@link ChannelAwareSaRouter#getStpLogic(ChannelType)} 分派到对应渠道。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenGatewayConfiguration {

  /** 错误码:未登录或登录已过期 */
  private static final String CODE_NOT_LOGIN = "COMMON.0002";

  /** 错误码:无权限访问 */
  private static final String CODE_NO_PERMISSION = "COMMON.0003";

  /** 错误码:系统内部错误 */
  private static final String CODE_INTERNAL_ERROR = "COMMON.0050";

  /**
   * SaReactorFilter 执行顺序(对应 {@link GatewayFilterOrder#AUTH} = -200)。
   *
   * <p>注解属性要求编译期常量,无法直接使用枚举方法调用,故显式定义为 static final int。
   */
  private static final int FILTER_ORDER_AUTH = -200;

  private final ChannelAwareSaRouter channelAwareSaRouter;
  private final RouteRuleLoader routeRuleLoader;

  /**
   * 注册 SaReactorFilter,在 WebFlux 过滤器链中执行 sa-token 鉴权。
   *
   * <p>执行顺序:位于 IP_BLOCK/EXCLUDE_ROUTE 之后,RATE_LIMIT/TENANT_RESOLVE/CRYPTO 之前。
   *
   * @return SaReactorFilter 实例
   */
  @Bean
  @Order(FILTER_ORDER_AUTH)
  public SaReactorFilter saReactorFilter() {
    return new SaReactorFilter()
        .addInclude("/**")
        .addExclude(
            // actuator 监控端点
            "/actuator/**",
            "/actuator",
            // 静态资源
            "/favicon.ico",
            // 三渠道登录接口(无需登录即可访问)
            "/internet/auth/login",
            "/hq/auth/login",
            "/branch/auth/login",
            // 网点二次授权发起/确认(经办人未登录场景)
            "/branch/auth/secondary-auth/initiate",
            "/branch/auth/secondary-auth/confirm",
            "/branch/auth/secondary-auth/status/**"
        )
        .setAuth(obj -> {
          // 1. 渠道识别 + 登录校验(基于路径前缀分派到对应 StpLogic)
          ChannelType channel = channelAwareSaRouter.matchAndCheckLogin();
          if (channel == null) {
            // 公共接口(非 /internet, /hq, /branch 前缀),跳过路由级校验
            return;
          }

          // 2. 动态路由权限校验:加载 RouteRule,按 priority 倒序匹配
          List<RouteRule> rules = routeRuleLoader.loadRules();
          StpLogic stpLogic = channelAwareSaRouter.getStpLogic(channel);
          for (RouteRule rule : rules) {
            SaRouter.match(rule.routePattern()).check(r -> applyRuleCheck(rule, channel, stpLogic));
          }
        })
        .setError(this::handleError);
  }

  /**
   * 应用单条路由规则的校验逻辑。
   *
   * <p>根据 checkType 分派:
   * <ul>
   *   <li>{@code LOGIN} - 已在 matchAndCheckLogin 阶段校验,跳过</li>
   *   <li>{@code PERMISSION} - 调用渠道 StpLogic.checkPermission(checkValue)</li>
   *   <li>{@code ROLE} - 调用渠道 StpLogic.checkRole(checkValue)</li>
   *   <li>{@code CHANNEL} - 校验当前渠道是否匹配 checkValue(大小写不敏感)</li>
   *   <li>{@code SKIP} - 白名单,不校验</li>
   * </ul>
   *
   * @param rule     路由规则
   * @param channel  当前请求渠道
   * @param stpLogic 当前渠道 StpLogic
   */
  private void applyRuleCheck(RouteRule rule, ChannelType channel, StpLogic stpLogic) {
    switch (rule.checkType()) {
      case "LOGIN" -> { /* 已在 matchAndCheckLogin 阶段校验 */ }
      case "PERMISSION" -> stpLogic.checkPermission(rule.checkValue());
      case "ROLE" -> stpLogic.checkRole(rule.checkValue());
      case "CHANNEL" -> checkChannel(channel, rule.checkValue());
      case "SKIP" -> { /* 白名单,不校验 */ }
      default -> log.warn("[SaTokenGateway] 未知 checkType,跳过: rule={}, checkType={}",
          rule.routePattern(), rule.checkType());
    }
  }

  /**
   * 校验当前渠道是否匹配规则要求。
   *
   * @param current    当前请求渠道
   * @param checkValue 规则要求的渠道名(大小写不敏感)
   */
  private void checkChannel(ChannelType current, String checkValue) {
    if (checkValue == null || checkValue.isBlank()) {
      return;
    }
    if (!current.name().equalsIgnoreCase(checkValue)) {
      throw new NotPermissionException(checkValue, channelAwareSaRouter.getStpLogic(current).getLoginType());
    }
  }

  /**
   * 统一异常处理:将 sa-token 异常转换为 ApiResult 响应。
   *
   * <p>异常类型与 HTTP 状态码映射:
   * <ul>
   *   <li>{@link NotLoginException} → 401 (未登录或登录已过期)</li>
   *   <li>{@link NotPermissionException} / {@link NotRoleException} → 403 (无权限访问)</li>
   *   <li>其他异常 → 500 (系统内部错误)</li>
   * </ul>
   *
   * @param e 抛出的异常
   * @return ApiResult 响应体
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
