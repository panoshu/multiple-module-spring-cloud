package com.example.iam.application.service;

import com.example.iam.api.command.CreatePermissionRuleCommand;
import com.example.iam.api.command.DisablePermissionRuleCommand;
import com.example.iam.api.command.EnablePermissionRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PermissionRuleDTO;
import com.example.iam.api.query.GetPermissionRuleDetailQuery;
import com.example.iam.api.query.ListPermissionRulesQuery;
import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.domain.authorization.repository.BusinessDefinitionRepository;
import com.example.iam.domain.authorization.repository.PermissionRuleRepository;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.types.PermissionRuleId;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限规则管理应用服务。
 *
 * <p>负责权限规则的创建、启用/禁用与查询编排。创建时校验业务编码存在性与动作合法性。
 * 状态变更后通过 {@link PermissionCachePort} 失效相关缓存。
 *
 * <p>本服务仅编排业务流程,规则状态机校验、动作集合校验由 PermissionRule 聚合根负责。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionRuleAppService {

  private final PermissionRuleRepository ruleRepository;
  private final BusinessDefinitionRepository businessDefinitionRepository;
  private final PermissionCachePort permissionCachePort;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建权限规则。
   *
   * <p>流程:
   * <ol>
   *   <li>校验 ruleCode 唯一性</li>
   *   <li>加载业务定义,校验 businessCode 存在且支持所有 allowedActions</li>
   *   <li>调用 PermissionRule.create 工厂方法创建聚合根</li>
   *   <li>保存并发布事件</li>
   * </ol>
   *
   * @param command 创建命令
   * @return 新建规则 ID
   */
  @Transactional
  public IdResponseDTO create(CreatePermissionRuleCommand command) {
    if (ruleRepository.existsByRuleCode(command.ruleCode())) {
      throw new BusinessException(IamAuthzErrorCode.PERMISSION_RULE_CODE_DUPLICATE)
          .withUserDetail("规则编码已存在")
          .withContext("ruleCode", command.ruleCode());
    }

    BusinessCode businessCode = BusinessCode.of(command.businessCode());
    BusinessDefinition businessDefinition = businessDefinitionRepository
        .findByBusinessCode(businessCode)
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.BUSINESS_DEFINITION_NOT_FOUND)
            .withUserDetail("业务定义不存在")
            .withContext("businessCode", command.businessCode()));

    Set<Action> actions = command.allowedActions().stream()
        .map(this::parseAction)
        .collect(Collectors.toSet());
    actions.forEach(action -> businessDefinition.validatePermission(businessCode, action));

    PermissionRuleId ruleId = idService.nextLongId(PermissionRuleId.class, "IAM_PERMISSION_RULE");
    UserNo operator = UserNo.of(command.operator());
    SubjectType subjectType = parseSubjectType(command.subjectType());
    OverrideMode overrideMode = parseOverrideMode(command.overrideMode());

    PermissionRule rule = PermissionRule.create(
        ruleId, command.ruleCode(), command.ruleName(),
        subjectType, command.subjectId(),
        businessCode, actions,
        command.inheritToChildren(), overrideMode, command.priority(),
        command.effectiveAt(), command.expireAt(), operator);

    ruleRepository.save(rule);
    publishEvents(rule);
    permissionCachePort.evictAll();

    log.info("权限规则创建成功: ruleId={}, ruleCode={}, businessCode={}",
        ruleId.value(), command.ruleCode(), command.businessCode());
    return new IdResponseDTO(ruleId.value());
  }

  /**
   * 禁用权限规则。
   *
   * @param command 禁用命令
   */
  @Transactional
  public void disable(DisablePermissionRuleCommand command) {
    PermissionRule rule = loadRuleOrThrow(command.ruleId());
    UserNo operator = UserNo.of(command.operator());
    rule.disable(operator);
    ruleRepository.save(rule);
    publishEvents(rule);
    permissionCachePort.evictAll();
    log.info("权限规则禁用成功: ruleId={}", command.ruleId());
  }

  /**
   * 启用权限规则。
   *
   * @param command 启用命令
   */
  @Transactional
  public void enable(EnablePermissionRuleCommand command) {
    PermissionRule rule = loadRuleOrThrow(command.ruleId());
    UserNo operator = UserNo.of(command.operator());
    rule.enable(operator);
    ruleRepository.save(rule);
    publishEvents(rule);
    permissionCachePort.evictAll();
    log.info("权限规则启用成功: ruleId={}", command.ruleId());
  }

  /**
   * 权限规则列表分页查询。
   *
   * @param query 列表查询
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<PermissionRuleDTO> list(ListPermissionRulesQuery query) {
    List<PermissionRule> all = ruleRepository.loadAll();
    List<PermissionRule> filtered = all.stream()
        .filter(r -> matchesRuleCode(r, query.ruleCode()))
        .filter(r -> matchesSubjectType(r, query.subjectType()))
        .filter(r -> matchesSubjectId(r, query.subjectId()))
        .filter(r -> matchesBusinessCode(r, query.businessCode()))
        .filter(r -> matchesStatus(r, query.status()))
        .toList();
    return paginate(filtered, query.pageQuery());
  }

  /**
   * 权限规则详情查询。
   *
   * @param query 详情查询
   * @return 规则 DTO
   */
  @Transactional(readOnly = true)
  public PermissionRuleDTO getDetail(GetPermissionRuleDetailQuery query) {
    PermissionRule rule = loadRuleOrThrow(query.ruleId());
    return toDTO(rule);
  }

  /**
   * 加载规则或抛出业务异常。
   */
  private PermissionRule loadRuleOrThrow(Long ruleId) {
    return ruleRepository.load(PermissionRuleId.of(ruleId))
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.PERMISSION_RULE_NOT_FOUND)
            .withUserDetail("权限规则不存在")
            .withContext("ruleId", ruleId));
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(PermissionRule rule) {
    rule.getDomainEvents().forEach(eventBus::publish);
    rule.clearDomainEvents();
  }

  /**
   * 解析动作枚举,无效时抛业务异常。
   */
  private Action parseAction(String action) {
    try {
      return Action.valueOf(Objects.requireNonNull(action, "action cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthzErrorCode.ACTION_EMPTY)
          .withUserDetail("不支持的动作: " + action)
          .withContext("action", action);
    }
  }

  /**
   * 解析主体类型枚举。
   */
  private SubjectType parseSubjectType(String subjectType) {
    try {
      return SubjectType.valueOf(
          Objects.requireNonNull(subjectType, "subjectType cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthzErrorCode.SUBJECT_TYPE_INVALID)
          .withUserDetail("主体类型无效: " + subjectType)
          .withContext("subjectType", subjectType);
    }
  }

  /**
   * 解析覆盖模式枚举。
   */
  private OverrideMode parseOverrideMode(String overrideMode) {
    try {
      return OverrideMode.valueOf(
          Objects.requireNonNull(overrideMode, "overrideMode cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthzErrorCode.OVERRIDE_MODE_INVALID)
          .withUserDetail("覆盖模式无效: " + overrideMode)
          .withContext("overrideMode", overrideMode);
    }
  }

  /**
   * 列表分页切片。
   */
  private PageData<PermissionRuleDTO> paginate(List<PermissionRule> rules, PageQuery pageQuery) {
    int total = rules.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<PermissionRuleDTO> items = rules.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesRuleCode(PermissionRule r, String ruleCode) {
    if (ruleCode == null || ruleCode.isBlank()) {
      return true;
    }
    return ruleCode.equals(r.ruleCode());
  }

  private boolean matchesSubjectType(PermissionRule r, String subjectType) {
    if (subjectType == null || subjectType.isBlank()) {
      return true;
    }
    return r.subjectType() != null && r.subjectType().name().equals(subjectType);
  }

  private boolean matchesSubjectId(PermissionRule r, String subjectId) {
    if (subjectId == null || subjectId.isBlank()) {
      return true;
    }
    return subjectId.equals(r.subjectId());
  }

  private boolean matchesBusinessCode(PermissionRule r, String businessCode) {
    if (businessCode == null || businessCode.isBlank()) {
      return true;
    }
    return r.businessCode() != null && businessCode.equals(r.businessCode().value());
  }

  private boolean matchesStatus(PermissionRule r, String status) {
    if (status == null || status.isBlank()) {
      return true;
    }
    RuleStatus currentStatus = r.status();
    return currentStatus != null && currentStatus.name().equals(status);
  }

  /**
   * 领域对象转 DTO。
   */
  private PermissionRuleDTO toDTO(PermissionRule r) {
    Set<String> actionStrings = r.allowedActions().stream()
        .map(Action::name)
        .collect(Collectors.toSet());
    return new PermissionRuleDTO(
        r.id().value(),
        r.ruleCode(),
        r.ruleName(),
        r.subjectType() != null ? r.subjectType().name() : null,
        r.subjectId(),
        r.businessCode() != null ? r.businessCode().value() : null,
        actionStrings,
        r.isInheritToChildren(),
        r.overrideMode() != null ? r.overrideMode().name() : null,
        r.priority(),
        r.status() != null ? r.status().name() : null,
        r.effectiveAt(),
        r.expireAt(),
        r.createdAt(),
        r.updatedAt(),
        r.version() != null ? r.version().value() : null
    );
  }
}
