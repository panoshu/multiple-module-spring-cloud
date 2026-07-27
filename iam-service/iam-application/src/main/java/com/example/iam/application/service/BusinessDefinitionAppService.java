package com.example.iam.application.service;

import com.example.iam.api.command.CreateBusinessDefinitionCommand;
import com.example.iam.api.command.CreateBusinessDefinitionCommand.BusinessActionItem;
import com.example.iam.api.command.DisableBusinessDefinitionCommand;
import com.example.iam.api.command.EnableBusinessDefinitionCommand;
import com.example.iam.api.dto.BusinessActionDTO;
import com.example.iam.api.dto.BusinessDefinitionDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListBusinessDefinitionsQuery;
import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.domain.authorization.repository.BusinessDefinitionRepository;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.types.BusinessDefinitionId;
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
 * 业务定义管理应用服务。
 *
 * <p>负责系统支持的某类业务及其支持动作的元数据管理。
 * 业务定义是权限规则与代办关系的引用基础,通常通过 SQL 脚本预置初始化数据,
 * 亦支持运行时通过本服务增删启停。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDefinitionAppService {

  private final BusinessDefinitionRepository businessDefinitionRepository;
  private final PermissionCachePort permissionCachePort;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建业务定义。
   *
   * @param command 创建命令
   * @return 新建业务定义 ID
   */
  @Transactional
  public IdResponseDTO create(CreateBusinessDefinitionCommand command) {
    BusinessCode businessCode = BusinessCode.of(command.businessCode());
    if (businessDefinitionRepository.existsByBusinessCode(businessCode)) {
      throw new BusinessException(IamAuthzErrorCode.BUSINESS_CODE_DUPLICATE)
          .withUserDetail("业务编码已存在")
          .withContext("businessCode", command.businessCode());
    }

    Set<BusinessAction> supportedActions = command.supportedActions().stream()
        .map(this::toBusinessAction)
        .collect(Collectors.toSet());

    BusinessDefinitionId definitionId = idService.nextLongId(
        BusinessDefinitionId.class, "IAM_BIZ_DEFINITION");
    UserNo operator = UserNo.of(command.operator());

    BusinessDefinition definition = BusinessDefinition.create(
        definitionId, businessCode, command.businessName(),
        command.description(), supportedActions, operator);

    businessDefinitionRepository.save(definition);
    publishEvents(definition);
    permissionCachePort.evictAll();

    log.info("业务定义创建成功: definitionId={}, businessCode={}",
        definitionId.value(), command.businessCode());
    return new IdResponseDTO(definitionId.value());
  }

  /**
   * 禁用业务定义。
   *
   * @param command 禁用命令
   */
  @Transactional
  public void disable(DisableBusinessDefinitionCommand command) {
    BusinessDefinition definition = loadDefinitionOrThrow(command.definitionId());
    UserNo operator = UserNo.of(command.operator());
    definition.disable(operator);
    businessDefinitionRepository.save(definition);
    publishEvents(definition);
    permissionCachePort.evictAll();
    log.info("业务定义禁用成功: definitionId={}", command.definitionId());
  }

  /**
   * 启用业务定义。
   *
   * @param command 启用命令
   */
  @Transactional
  public void enable(EnableBusinessDefinitionCommand command) {
    BusinessDefinition definition = loadDefinitionOrThrow(command.definitionId());
    UserNo operator = UserNo.of(command.operator());
    definition.enable(operator);
    businessDefinitionRepository.save(definition);
    publishEvents(definition);
    permissionCachePort.evictAll();
    log.info("业务定义启用成功: definitionId={}", command.definitionId());
  }

  /**
   * 业务定义列表分页查询。
   *
   * <p>当查询未指定分页参数时,返回全部业务定义。
   *
   * @param query 列表查询
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<BusinessDefinitionDTO> list(ListBusinessDefinitionsQuery query) {
    List<BusinessDefinition> all = businessDefinitionRepository.findAll();
    List<BusinessDefinition> filtered = all.stream()
        .filter(d -> matchesBusinessCode(d, query.businessCode()))
        .filter(d -> matchesBusinessName(d, query.businessName()))
        .filter(d -> matchesActive(d, query.active()))
        .toList();
    PageQuery pageQuery = query.pageQuery();
    if (pageQuery == null) {
      return new PageData<>(filtered.size(), 0, filtered.size(), false,
          filtered.stream().map(this::toDTO).toList());
    }
    return paginate(filtered, pageQuery);
  }

  /**
   * 加载业务定义或抛出业务异常。
   */
  private BusinessDefinition loadDefinitionOrThrow(Long definitionId) {
    return businessDefinitionRepository.load(BusinessDefinitionId.of(definitionId))
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.BUSINESS_DEFINITION_NOT_FOUND)
            .withUserDetail("业务定义不存在")
            .withContext("definitionId", definitionId));
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(BusinessDefinition definition) {
    definition.getDomainEvents().forEach(eventBus::publish);
    definition.clearDomainEvents();
  }

  /**
   * 命令动作项转领域值对象。
   */
  private BusinessAction toBusinessAction(BusinessActionItem item) {
    Action action = parseAction(item.action());
    return BusinessAction.of(action, item.description());
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
  private PageData<BusinessDefinitionDTO> paginate(List<BusinessDefinition> definitions,
                                                    PageQuery pageQuery) {
    int total = definitions.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<BusinessDefinitionDTO> items = definitions.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesBusinessCode(BusinessDefinition d, String businessCode) {
    if (businessCode == null || businessCode.isBlank()) {
      return true;
    }
    return d.businessCode() != null && businessCode.equals(d.businessCode().value());
  }

  private boolean matchesBusinessName(BusinessDefinition d, String businessName) {
    if (businessName == null || businessName.isBlank()) {
      return true;
    }
    return d.businessName() != null && d.businessName().contains(businessName);
  }

  private boolean matchesActive(BusinessDefinition d, Boolean active) {
    if (active == null) {
      return true;
    }
    return d.isActive() == active;
  }

  /**
   * 领域对象转 DTO。
   */
  private BusinessDefinitionDTO toDTO(BusinessDefinition d) {
    Set<BusinessActionDTO> actionDTOs = d.supportedActions().stream()
        .map(ba -> new BusinessActionDTO(ba.action().name(), ba.description()))
        .collect(Collectors.toSet());
    return new BusinessDefinitionDTO(
        d.id().value(),
        d.businessCode() != null ? d.businessCode().value() : null,
        d.businessName(),
        d.description(),
        actionDTOs,
        d.isActive(),
        d.createdAt(),
        d.updatedAt(),
        d.version() != null ? d.version().value() : null
    );
  }
}
