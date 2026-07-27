package com.example.iam.application.service;

import com.example.iam.api.command.CreateRouteRuleCommand;
import com.example.iam.api.command.DisableRouteRuleCommand;
import com.example.iam.api.command.EnableRouteRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.RouteRuleDTO;
import com.example.iam.api.query.GetRouteRuleDetailQuery;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.domain.authorization.repository.RouteRuleRepository;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 路由规则管理应用服务。
 *
 * <p>负责网关层动态鉴权的路由规则配置管理。
 * demo-gateway 启动时通过 Repository 加载所有启用的 RouteRule 用于请求路径匹配鉴权。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteRuleAppService {

  private final RouteRuleRepository routeRuleRepository;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建路由规则。
   *
   * @param command 创建命令
   * @return 新建规则 ID
   */
  @Transactional
  public IdResponseDTO create(CreateRouteRuleCommand command) {
    RouteCheckType checkType = parseCheckType(command.checkType());

    RouteRuleId ruleId = idService.nextLongId(RouteRuleId.class, "IAM_ROUTE_RULE");
    UserNo operator = UserNo.of(command.operator());

    RouteRule rule = RouteRule.create(
        ruleId, command.routePattern(), checkType,
        command.checkValue(), command.description(),
        command.priority(), operator);

    routeRuleRepository.save(rule);
    publishEvents(rule);

    log.info("路由规则创建成功: ruleId={}, routePattern={}, checkType={}",
        ruleId.value(), command.routePattern(), checkType);
    return new IdResponseDTO(ruleId.value());
  }

  /**
   * 禁用路由规则。
   *
   * @param command 禁用命令
   */
  @Transactional
  public void disable(DisableRouteRuleCommand command) {
    RouteRule rule = loadRuleOrThrow(command.ruleId());
    UserNo operator = UserNo.of(command.operator());
    rule.disable(operator);
    routeRuleRepository.save(rule);
    publishEvents(rule);
    log.info("路由规则禁用成功: ruleId={}", command.ruleId());
  }

  /**
   * 启用路由规则。
   *
   * @param command 启用命令
   */
  @Transactional
  public void enable(EnableRouteRuleCommand command) {
    RouteRule rule = loadRuleOrThrow(command.ruleId());
    UserNo operator = UserNo.of(command.operator());
    rule.enable(operator);
    routeRuleRepository.save(rule);
    publishEvents(rule);
    log.info("路由规则启用成功: ruleId={}", command.ruleId());
  }

  /**
   * 路由规则列表分页查询。
   *
   * @param query 列表查询
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<RouteRuleDTO> list(ListRouteRulesQuery query) {
    List<RouteRule> all = routeRuleRepository.findAll();
    List<RouteRule> filtered = all.stream()
        .filter(r -> matchesRoutePattern(r, query.routePattern()))
        .filter(r -> matchesCheckType(r, query.checkType()))
        .filter(r -> matchesEnabled(r, query.enabled()))
        .toList();
    return paginate(filtered, query.pageQuery());
  }

  /**
   * 路由规则详情查询。
   *
   * @param query 详情查询
   * @return 规则 DTO
   */
  @Transactional(readOnly = true)
  public RouteRuleDTO getDetail(GetRouteRuleDetailQuery query) {
    RouteRule rule = loadRuleOrThrow(query.ruleId());
    return toDTO(rule);
  }

  /**
   * 加载规则或抛出业务异常。
   */
  private RouteRule loadRuleOrThrow(Long ruleId) {
    return routeRuleRepository.load(RouteRuleId.of(ruleId))
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.ROUTE_RULE_NOT_FOUND)
            .withUserDetail("路由规则不存在")
            .withContext("ruleId", ruleId));
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(RouteRule rule) {
    rule.getDomainEvents().forEach(eventBus::publish);
    rule.clearDomainEvents();
  }

  /**
   * 解析校验类型枚举。
   */
  private RouteCheckType parseCheckType(String checkType) {
    try {
      return RouteCheckType.valueOf(
          Objects.requireNonNull(checkType, "checkType cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthzErrorCode.ROUTE_RULE_CHECK_TYPE_INVALID)
          .withUserDetail("路由校验类型无效: " + checkType)
          .withContext("checkType", checkType);
    }
  }

  /**
   * 列表分页切片。
   */
  private PageData<RouteRuleDTO> paginate(List<RouteRule> rules, PageQuery pageQuery) {
    int total = rules.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<RouteRuleDTO> items = rules.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesRoutePattern(RouteRule r, String routePattern) {
    if (routePattern == null || routePattern.isBlank()) {
      return true;
    }
    return r.routePattern() != null && r.routePattern().contains(routePattern);
  }

  private boolean matchesCheckType(RouteRule r, String checkType) {
    if (checkType == null || checkType.isBlank()) {
      return true;
    }
    return r.checkType() != null && r.checkType().name().equals(checkType);
  }

  private boolean matchesEnabled(RouteRule r, Boolean enabled) {
    if (enabled == null) {
      return true;
    }
    return r.isEnabled() == enabled;
  }

  /**
   * 领域对象转 DTO。
   */
  private RouteRuleDTO toDTO(RouteRule r) {
    return new RouteRuleDTO(
        r.id().value(),
        r.routePattern(),
        r.checkType() != null ? r.checkType().name() : null,
        r.checkValue(),
        r.description(),
        r.priority(),
        r.isEnabled(),
        r.createdAt(),
        r.updatedAt(),
        r.version() != null ? r.version().value() : null
    );
  }
}
