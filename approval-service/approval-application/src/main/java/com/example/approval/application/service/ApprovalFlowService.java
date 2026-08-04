package com.example.approval.application.service;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.*;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.service.ApprovalFlowMatcher;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.types.ApprovalFlowId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 审批流应用服务
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalFlowService {

  private final ApprovalFlowRepository flowRepository;
  private final ApprovalFlowMatcher flowMatcher;
  private final EventBus eventBus;

  /**
   * 创建审批流
   *
   * @param request 创建请求
   * @return 审批流ID
   */
  @Transactional
  public ApprovalFlowId createApprovalFlow(CreateApprovalFlowRequest request) {
    // 生成审批流ID
    ApprovalFlowId flowId = ApprovalFlowId.of(System.currentTimeMillis());

    // 转换匹配规则
    MatchRules matchRules = convertMatchRules(request.matchRules());

    // 转换审批节点
    List<ApprovalNode> nodes = convertNodes(request.nodes());

    // 创建审批流聚合根
    ApprovalFlow flow = ApprovalFlow.create(
      flowId,
      FlowName.of(request.flowName()),
      matchRules,
      nodes,
      UserNo.of(request.createdBy())
    );

    // 保存审批流
    flowRepository.save(flow);

    // 发布领域事件
    flow.domainEvents().forEach(eventBus::publish);
    flow.clearDomainEvents();

    log.info("审批流创建成功: flowId={}, flowName={}", flowId, request.flowName());
    return flowId;
  }

  /**
   * 更新审批流
   *
   * @param request 更新请求
   */
  @Transactional
  public void updateApprovalFlow(UpdateApprovalFlowRequest request) {
    // 加载审批流
    ApprovalFlow flow = loadFlowOrThrow(request.flowId());

    // 转换匹配规则
    MatchRules matchRules = convertMatchRules(request.matchRules());

    // 转换审批节点
    List<ApprovalNode> nodes = convertNodes(request.nodes());

    // 更新审批流
    flow.update(
      FlowName.of(request.flowName()),
      matchRules,
      nodes,
      UserNo.of(request.updatedBy())
    );

    // 保存审批流
    flowRepository.save(flow);

    // 发布领域事件
    flow.domainEvents().forEach(eventBus::publish);
    flow.clearDomainEvents();

    log.info("审批流更新成功: flowId={}", request.flowId());
  }

  /**
   * 废弃审批流
   *
   * @param request 废弃请求
   */
  @Transactional
  public void deprecateApprovalFlow(DeprecateApprovalFlowRequest request) {
    // 加载审批流
    ApprovalFlow flow = loadFlowOrThrow(request.flowId());

    // 废弃审批流
    flow.deprecate(UserNo.of(request.operatedBy()));

    // 保存审批流
    flowRepository.save(flow);

    // 发布领域事件
    flow.domainEvents().forEach(eventBus::publish);
    flow.clearDomainEvents();

    log.info("审批流废弃成功: flowId={}", request.flowId());
  }

  /**
   * 查询审批流
   *
   * @param request 查询请求
   * @return 审批流详情
   */
  @Transactional(readOnly = true)
  public ApprovalFlowDTO getApprovalFlow(GetApprovalFlowRequest request) {
    ApprovalFlow flow = loadFlowOrThrow(request.flowId());
    return convertToDTO(flow);
  }

  /**
   * 列表查询审批流
   *
   * @param request 列表查询请求
   * @return 审批流分页列表
   */
  @Transactional(readOnly = true)
  public PageData<ApprovalFlowDTO> listApprovalFlows(ListApprovalFlowsRequest request) {
    // TODO: 实现分页查询逻辑
    // 这里需要根据状态等条件查询审批流列表
    throw new UnsupportedOperationException("待实现分页查询逻辑");
  }

  /**
   * 匹配审批流
   *
   * @param request 匹配请求
   * @return 匹配的审批流
   */
  @Transactional(readOnly = true)
  public ApprovalFlowDTO matchApprovalFlow(MatchApprovalFlowRequest request) {
    // 转换匹配规则
    MatchRules matchRules = convertMatchRulesFromRequest(request);

    // 查找所有激活的审批流
    List<ApprovalFlow> flows = flowRepository.findByStatus(
      com.example.approval.types.enums.FlowStatus.ACTIVE
    );

    // 使用领域服务匹配审批流
    Optional<ApprovalFlow> matchedFlow = flowMatcher.match(matchRules, flows);

    if (matchedFlow.isEmpty()) {
      throw new DomainException(com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_FLOW_NOT_FOUND)
        .withLogDetail("未找到匹配的审批流");
    }

    return convertToDTO(matchedFlow.get());
  }

  /**
   * 加载审批流或抛出异常
   */
  private ApprovalFlow loadFlowOrThrow(ApprovalFlowId flowId) {
    return flowRepository.load(flowId)
      .orElseThrow(() -> new DomainException(
        com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_FLOW_NOT_FOUND
      ).withLogDetail("审批流不存在, flowId: " + flowId));
  }

  /**
   * 转换匹配规则DTO为领域对象
   */
  private MatchRules convertMatchRules(com.example.approval.api.dto.MatchRulesDTO dto) {
    if (dto == null) {
      return null;
    }
    // TODO: 根据实际业务逻辑转换
    return MatchRules.of(
      null, // productNo
      null, // customerNo
      dto.accountManagerCodes() != null && !dto.accountManagerCodes().isEmpty()
        ? dto.accountManagerCodes().get(0) : null,
      null, // operationMode
      dto.businessTypes() != null && !dto.businessTypes().isEmpty()
        ? dto.businessTypes().get(0) : null,
      null  // annuityChannel
    );
  }

  /**
   * 从匹配请求转换匹配规则
   */
  private MatchRules convertMatchRulesFromRequest(MatchApprovalFlowRequest request) {
    // TODO: 根据实际请求字段转换
    return MatchRules.of(
      null, // productNo
      null, // customerNo
      request.accountManagerCode(),
      null, // operationMode
      request.businessType(),
      null  // annuityChannel
    );
  }

  /**
   * 转换审批节点DTO列表为领域对象列表
   */
  private List<ApprovalNode> convertNodes(List<com.example.approval.api.dto.ApprovalNodeDTO> nodeDTOs) {
    if (nodeDTOs == null || nodeDTOs.isEmpty()) {
      return List.of();
    }

    return nodeDTOs.stream()
      .map(this::convertNode)
      .toList();
  }

  /**
   * 转换审批节点DTO为领域对象
   */
  private ApprovalNode convertNode(com.example.approval.api.dto.ApprovalNodeDTO dto) {
    // TODO: 根据实际业务逻辑转换
    return ApprovalNode.createSamePlanNode(
      dto.nodeId() != null ? dto.nodeId() : com.example.approval.types.NodeId.of(System.currentTimeMillis()),
      com.example.approval.domain.valueobject.NodeOrder.of(dto.order()),
      com.example.approval.types.enums.ApproverType.valueOf(dto.nodeType()),
      dto.approvalUsers() != null
        ? dto.approvalUsers().stream().map(UserNo::of).toList()
        : List.of(),
      null, // roleIds
      com.example.approval.types.enums.SignMode.OR_SIGN, // 默认或签
      UserNo.of("SYSTEM") // TODO: 从上下文获取操作人
    );
  }

  /**
   * 转换领域对象为DTO
   */
  private ApprovalFlowDTO convertToDTO(ApprovalFlow flow) {
    return new ApprovalFlowDTO(
      flow.id(),
      flow.flowName().value(),
      flow.matchRules().businessType(),
      flow.status().name(),
      flow.flowVersion().value(),
      convertToMatchRulesDTO(flow.matchRules()),
      flow.getNodes().stream().map(this::convertToNodeDTO).toList(),
      flow.createdBy().value(),
      flow.createdAt(),
      flow.updatedAt()
    );
  }

  /**
   * 转换匹配规则为DTO
   */
  private com.example.approval.api.dto.MatchRulesDTO convertToMatchRulesDTO(MatchRules rules) {
    if (rules == null) {
      return null;
    }
    return new com.example.approval.api.dto.MatchRulesDTO(
      rules.accountManager() != null ? List.of(rules.accountManager()) : null,
      rules.businessType() != null ? List.of(rules.businessType()) : null,
      null, // amountMin
      null  // amountMax
    );
  }

  /**
   * 转换审批节点为DTO
   */
  private com.example.approval.api.dto.ApprovalNodeDTO convertToNodeDTO(ApprovalNode node) {
    return new com.example.approval.api.dto.ApprovalNodeDTO(
      node.id(),
      node.nodeType().name(), // nodeName
      node.nodeType().name(),
      node.isSpecifiedRoleApproval() ? node.roleIds().get(0) : null,
      node.approverIds().stream().map(UserNo::value).toList(),
      node.nodeOrder().value(),
      true // required
    );
  }
}
