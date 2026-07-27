package com.example.iam.application.service;

import com.example.iam.api.command.CreatePlanDelegationCommand;
import com.example.iam.api.command.CreatePlanDelegationCommand.DelegationPermissionItem;
import com.example.iam.api.command.RevokePlanDelegationCommand;
import com.example.iam.api.dto.DelegationPermissionDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PlanDelegationDTO;
import com.example.iam.api.query.GetPlanDelegationDetailQuery;
import com.example.iam.api.query.ListPlanDelegationsQuery;
import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.domain.authorization.repository.PlanDelegationRepository;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.types.PlanDelegationId;
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
 * 计划代办关系管理应用服务。
 *
 * <p>负责计划 A 授权计划 B 代办某些业务的代办关系创建、撤销与查询编排。
 * 创建时支持全部操作员代办或指定操作员代办,代办关系变更后失效相关权限缓存。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanDelegationAppService {

  private final PlanDelegationRepository delegationRepository;
  private final PermissionCachePort permissionCachePort;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建计划代办关系。
   *
   * @param command 创建命令
   * @return 新建代办关系 ID
   */
  @Transactional
  public IdResponseDTO create(CreatePlanDelegationCommand command) {
    if (delegationRepository.existsByDelegationCode(command.delegationCode())) {
      throw new BusinessException(IamAuthzErrorCode.PLAN_DELEGATION_CODE_DUPLICATE)
          .withUserDetail("代办编码已存在")
          .withContext("delegationCode", command.delegationCode());
    }

    DelegationType delegationType = parseDelegationType(command.delegationType());
    Set<DelegationPermission> permissions = command.delegationPermissions().stream()
        .map(this::toDelegationPermission)
        .collect(Collectors.toSet());

    PlanDelegationId delegationId = idService.nextLongId(PlanDelegationId.class, "IAM_PLAN_DELEGATION");
    UserNo operator = UserNo.of(command.operator());

    PlanDelegation delegation = PlanDelegation.create(
        delegationId, command.delegationCode(),
        command.delegatorPlanNo(), command.delegateePlanNo(),
        delegationType, command.designatedOperators(), permissions,
        command.effectiveAt(), command.expireAt(), operator);

    delegationRepository.save(delegation);
    publishEvents(delegation);
    permissionCachePort.evictByPlan(command.delegatorPlanNo());
    permissionCachePort.evictByPlan(command.delegateePlanNo());

    log.info("计划代办关系创建成功: delegationId={}, delegationCode={}, delegator={}, delegatee={}",
        delegationId.value(), command.delegationCode(),
        command.delegatorPlanNo(), command.delegateePlanNo());
    return new IdResponseDTO(delegationId.value());
  }

  /**
   * 撤销计划代办关系(终态)。
   *
   * @param command 撤销命令
   */
  @Transactional
  public void revoke(RevokePlanDelegationCommand command) {
    PlanDelegation delegation = loadDelegationOrThrow(command.delegationId());
    UserNo operator = UserNo.of(command.operator());
    delegation.revoke(operator, command.reason());
    delegationRepository.save(delegation);
    publishEvents(delegation);
    permissionCachePort.evictByPlan(delegation.delegatorPlanNo());
    permissionCachePort.evictByPlan(delegation.delegateePlanNo());
    log.info("计划代办关系撤销: delegationId={}, reason={}",
        command.delegationId(), command.reason());
  }

  /**
   * 计划代办列表分页查询。
   *
   * @param query 列表查询
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<PlanDelegationDTO> list(ListPlanDelegationsQuery query) {
    List<PlanDelegation> all = delegationRepository.loadAll();
    List<PlanDelegation> filtered = all.stream()
        .filter(d -> matchesDelegator(d, query.delegatorPlanNo()))
        .filter(d -> matchesDelegatee(d, query.delegateePlanNo()))
        .filter(d -> matchesType(d, query.delegationType()))
        .filter(d -> matchesStatus(d, query.status()))
        .toList();
    return paginate(filtered, query.pageQuery());
  }

  /**
   * 计划代办详情查询。
   *
   * @param query 详情查询
   * @return 代办关系 DTO
   */
  @Transactional(readOnly = true)
  public PlanDelegationDTO getDetail(GetPlanDelegationDetailQuery query) {
    PlanDelegation delegation = loadDelegationOrThrow(query.delegationId());
    return toDTO(delegation);
  }

  /**
   * 加载代办关系或抛出业务异常。
   */
  private PlanDelegation loadDelegationOrThrow(Long delegationId) {
    return delegationRepository.load(PlanDelegationId.of(delegationId))
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.PLAN_DELEGATION_NOT_FOUND)
            .withUserDetail("计划代办关系不存在")
            .withContext("delegationId", delegationId));
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(PlanDelegation delegation) {
    delegation.getDomainEvents().forEach(eventBus::publish);
    delegation.clearDomainEvents();
  }

  /**
   * 命令权限项转领域值对象。
   */
  private DelegationPermission toDelegationPermission(DelegationPermissionItem item) {
    BusinessCode businessCode = BusinessCode.of(item.businessCode());
    Action action = parseAction(item.actions().iterator().next());
    return DelegationPermission.of(businessCode, action);
  }

  /**
   * 解析代办类型枚举。
   */
  private DelegationType parseDelegationType(String delegationType) {
    try {
      return DelegationType.valueOf(
          Objects.requireNonNull(delegationType, "delegationType cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthzErrorCode.DELEGATION_TYPE_INVALID)
          .withUserDetail("代办类型无效: " + delegationType)
          .withContext("delegationType", delegationType);
    }
  }

  /**
   * 解析动作枚举。
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
   * 列表分页切片。
   */
  private PageData<PlanDelegationDTO> paginate(List<PlanDelegation> delegations, PageQuery pageQuery) {
    int total = delegations.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<PlanDelegationDTO> items = delegations.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesDelegator(PlanDelegation d, String delegatorPlanNo) {
    if (delegatorPlanNo == null || delegatorPlanNo.isBlank()) {
      return true;
    }
    return delegatorPlanNo.equals(d.delegatorPlanNo());
  }

  private boolean matchesDelegatee(PlanDelegation d, String delegateePlanNo) {
    if (delegateePlanNo == null || delegateePlanNo.isBlank()) {
      return true;
    }
    return delegateePlanNo.equals(d.delegateePlanNo());
  }

  private boolean matchesType(PlanDelegation d, String delegationType) {
    if (delegationType == null || delegationType.isBlank()) {
      return true;
    }
    return d.delegationType() != null && d.delegationType().name().equals(delegationType);
  }

  private boolean matchesStatus(PlanDelegation d, String status) {
    if (status == null || status.isBlank()) {
      return true;
    }
    DelegationStatus currentStatus = d.status();
    return currentStatus != null && currentStatus.name().equals(status);
  }

  /**
   * 领域对象转 DTO。
   */
  private PlanDelegationDTO toDTO(PlanDelegation d) {
    Set<DelegationPermissionDTO> permissionDTOs = d.delegatedPermissions().stream()
        .map(p -> new DelegationPermissionDTO(
            p.businessCode().value(),
            p.action().name()))
        .collect(Collectors.toSet());
    return new PlanDelegationDTO(
        d.id().value(),
        d.delegationCode(),
        d.delegatorPlanNo(),
        d.delegateePlanNo(),
        d.delegationType() != null ? d.delegationType().name() : null,
        d.designatedOperators(),
        permissionDTOs,
        d.status() != null ? d.status().name() : null,
        d.effectiveAt(),
        d.expireAt(),
        d.createdAt(),
        d.updatedAt(),
        d.version() != null ? d.version().value() : null
    );
  }
}
