package com.example.approval.application.service;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.*;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.RejectTarget;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.DomainException;
import com.example.core.types.ApplicationId;
import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 审批实例应用服务
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalInstanceService {

  private final ApprovalInstanceRepository instanceRepository;
  private final ApprovalFlowRepository flowRepository;
  private final EventBus eventBus;
  private final com.example.shared.identifier.contract.IdService idService;
  private final com.example.approval.domain.aggregate.root.ApprovalIdGenerator idGenerator;
  private final RoleUserGateway roleUserGateway;

  /**
   * 启动审批
   *
   * @param request 启动请求
   * @return 审批实例ID
   */
  @Transactional
  public ApprovalInstanceId startApproval(StartApprovalRequest request) {
    // 外部协作幂等（spec §12.3）：同一 requestNo 重复启动直接返回原实例，不重复创建
    if (request.requestNo() != null && !request.requestNo().isBlank()) {
      Optional<ApprovalInstance> existing = instanceRepository.findByRequestNo(request.requestNo());
      if (existing.isPresent()) {
        return existing.get().id();
      }
    }

    // 加载审批流
    ApprovalFlow flow = loadFlowOrThrow(request.flowId());

    // 创建审批实例
    ApprovalInstanceId instanceId = idService.nextId(ApprovalInstanceId.class);
    ApprovalInstance instance = ApprovalInstance.create(
      instanceId,
      request.flowId(),
      FlowVersion.of(flow.flowVersion().value()),
      new ApplicationId(request.businessNo()),
      request.businessType(),
      null, // initiatorPlan
      request.initiator(),
      request.requestNo()
    );

    // 启动审批流程
    instance.start(request.initiator());

    // 保存审批实例
    instanceRepository.save(instance);

    // 发布领域事件
    instance.domainEvents().forEach(eventBus::publish);
    instance.clearDomainEvents();

    log.info("审批实例创建成功: instanceId={}, flowId={}, businessNo={}",
      instanceId, request.flowId(), request.businessNo());
    return instanceId;
  }

  /** 按外部协作幂等键查询实例 ID（响应丢失对账，spec §12.3）。 */
  public Optional<ApprovalInstanceId> findByRequestNo(String requestNo) {
    if (requestNo == null || requestNo.isBlank()) {
      return Optional.empty();
    }
    return instanceRepository.findByRequestNo(requestNo).map(ApprovalInstance::id);
  }

  /**
   * 审批通过
   *
   * @param request 通过请求
   */
  @Transactional
  public void approve(ApproveRequest request) {
    // 加载审批实例
    ApprovalInstance instance = loadInstanceOrThrow(request.instanceId());

    // 加载审批流并获取当前节点
    ApprovalFlow flow = loadFlowOrThrow(instance.flowId());
    ApprovalNode currentNode = flow.getNode(instance.currentNodeOrder())
      .orElseThrow(() -> new DomainException(
        com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_FLOW_NODE_INVALID
      ).withLogDetail("当前节点不存在"));

    // 执行审批通过
    instance.approve(
      currentNode,
      UserNo.of(request.approver()),
      ApprovalOpinion.of(request.comment()),
      request.approver(),
      idGenerator
    );

    // 保存审批实例
    instanceRepository.save(instance);

    // 发布领域事件
    instance.domainEvents().forEach(eventBus::publish);
    instance.clearDomainEvents();

    log.info("审批通过: instanceId={}, approver={}", request.instanceId(), request.approver());
  }

  /**
   * 审批驳回
   *
   * @param request 驳回请求
   */
  @Transactional
  public void reject(RejectRequest request) {
    // 加载审批实例
    ApprovalInstance instance = loadInstanceOrThrow(request.instanceId());

    // 加载审批流并获取当前节点
    ApprovalFlow flow = loadFlowOrThrow(instance.flowId());
    ApprovalNode currentNode = flow.getNode(instance.currentNodeOrder())
      .orElseThrow(() -> new DomainException(
        com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_FLOW_NODE_INVALID
      ).withLogDetail("当前节点不存在"));

    // 执行审批驳回（默认驳回终止流程）
    instance.reject(
      currentNode,
      UserNo.of(request.approver()),
      ApprovalOpinion.of(request.reason()),
      RejectTarget.terminate(),
      request.approver(),
      idGenerator
    );

    // 保存审批实例
    instanceRepository.save(instance);

    // 发布领域事件
    instance.domainEvents().forEach(eventBus::publish);
    instance.clearDomainEvents();

    log.info("审批驳回: instanceId={}, approver={}, reason={}",
      request.instanceId(), request.approver(), request.reason());
  }

  /**
   * 审批转交
   *
   * @param request 转交请求
   */
  @Transactional
  public void transfer(TransferRequest request) {
    // 加载审批实例
    ApprovalInstance instance = loadInstanceOrThrow(request.instanceId());

    // 加载审批流并获取当前节点
    ApprovalFlow flow = loadFlowOrThrow(instance.flowId());
    ApprovalNode currentNode = flow.getNode(instance.currentNodeOrder())
      .orElseThrow(() -> new DomainException(
        com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_FLOW_NODE_INVALID
      ).withLogDetail("当前节点不存在"));

    // 执行审批转交
    instance.transfer(
      currentNode,
      UserNo.of(request.currentApprover()),
      ApprovalOpinion.of(request.reason()),
      UserNo.of(request.targetApprover()),
      request.currentApprover(),
      idGenerator
    );

    // 保存审批实例
    instanceRepository.save(instance);

    // 发布领域事件
    instance.domainEvents().forEach(eventBus::publish);
    instance.clearDomainEvents();

    log.info("审批转交: instanceId={}, from={}, to={}",
      request.instanceId(), request.currentApprover(), request.targetApprover());
  }

  /**
   * 发起人撤回
   *
   * @param request 撤回请求
   */
  @Transactional
  public void withdraw(WithdrawRequest request) {
    // 加载审批实例
    ApprovalInstance instance = loadInstanceOrThrow(request.instanceId());

    // 执行撤回
    instance.withdraw(request.initiator());

    // 保存审批实例
    instanceRepository.save(instance);

    // 发布领域事件
    instance.domainEvents().forEach(eventBus::publish);
    instance.clearDomainEvents();

    log.info("审批撤回: instanceId={}, initiator={}", request.instanceId(), request.initiator());
  }

  /**
   * 查询审批实例
   *
   * @param request 查询请求
   * @return 审批实例详情
   */
  @Transactional(readOnly = true)
  public ApprovalInstanceDTO getApprovalInstance(GetApprovalInstanceRequest request) {
    ApprovalInstance instance = loadInstanceOrThrow(request.instanceId());
    return convertToDTO(instance);
  }

  /**
   * 待审批列表
   *
   * @param request 列表请求
   * @return 待审批分页列表
   */
  @Transactional(readOnly = true)
  public PageData<PendingApprovalDTO> listMyPendingApprovals(ListMyPendingApprovalsRequest request) {
    UserNo approver = UserNo.of(request.approver());
    List<PendingCandidate> candidates = collectCandidates();
    Map<PlanNo, Map<String, List<UserNo>>> roleUsers = expandRoleUsers(candidates);

    List<PendingApprovalDTO> matched = candidates.stream()
      .filter(candidate -> matches(candidate, approver, roleUsers))
      .map(this::toPendingApprovalDTO)
      .toList();

    int total = matched.size();
    int start = request.pagination().startPos();
    int end = Math.min(start + request.pagination().pageSize(), total);
    List<PendingApprovalDTO> items = start >= total ? List.of() : matched.subList(start, end);
    boolean hasMore = end < total;
    return new PageData<>(total, start, items.size(), hasMore, items);
  }

  /** 收集活跃实例当前节点；角色节点必须带 currentPlan 才能按 PLAN 精确 scope 展开。 */
  private List<PendingCandidate> collectCandidates() {
    List<PendingCandidate> candidates = new ArrayList<>();
    for (ApprovalInstance instance : instanceRepository.findByStatuses(
      List.of(InstanceStatus.PENDING, InstanceStatus.APPROVING))) {
      Optional<ApprovalFlow> flow = flowRepository.load(instance.flowId());
      if (flow.isEmpty()) {
        continue;
      }
      Optional<ApprovalNode> node = flow.get().getNode(instance.currentNodeOrder());
      if (node.isEmpty()) {
        continue;
      }
      if (node.get().isSpecifiedUserApproval()) {
        candidates.add(new PendingCandidate(instance, flow.get(), node.get(), null));
      } else if (node.get().isSpecifiedRoleApproval()
        && instance.currentPlan() != null && !instance.currentPlan().isBlank()) {
        candidates.add(new PendingCandidate(instance, flow.get(), node.get(),
          PlanNo.of(instance.currentPlan())));
      }
    }
    return candidates;
  }

  /** 按计划聚合角色编码，每个 planNo 只调用一次网关，规避逐实例展开的 N+1。 */
  private Map<PlanNo, Map<String, List<UserNo>>> expandRoleUsers(List<PendingCandidate> candidates) {
    Map<PlanNo, Set<String>> roleIdsByPlan = new LinkedHashMap<>();
    for (PendingCandidate candidate : candidates) {
      if (candidate.planNo() != null && !candidate.node().roleIds().isEmpty()) {
        roleIdsByPlan.computeIfAbsent(candidate.planNo(), key -> new LinkedHashSet<>())
          .addAll(candidate.node().roleIds());
      }
    }
    Map<PlanNo, Map<String, List<UserNo>>> expanded = new LinkedHashMap<>();
    roleIdsByPlan.forEach((plan, roleIds) ->
      expanded.put(plan, roleUserGateway.getUsersByRoles(List.copyOf(roleIds), plan)));
    return expanded;
  }

  private boolean matches(PendingCandidate candidate, UserNo approver,
                          Map<PlanNo, Map<String, List<UserNo>>> roleUsers) {
    if (candidate.planNo() == null) {
      return candidate.node().approverIds().contains(approver);
    }
    Map<String, List<UserNo>> byRole = roleUsers.getOrDefault(candidate.planNo(), Map.of());
    return candidate.node().roleIds().stream()
      .anyMatch(role -> byRole.getOrDefault(role, List.of()).contains(approver));
  }

  private PendingApprovalDTO toPendingApprovalDTO(PendingCandidate candidate) {
    ApprovalInstance instance = candidate.instance();
    return new PendingApprovalDTO(
      instance.id(),
      instance.businessApplicationId().value(),
      instance.businessType(),
      candidate.flow().flowName().value(),
      candidate.node().nodeType().name(),
      instance.createdBy(),
      instance.createdAt());
  }

  /** 待审批候选：planNo 为空表示指定用户节点，非空表示需按 PLAN 展开的角色节点。 */
  private record PendingCandidate(ApprovalInstance instance, ApprovalFlow flow,
                                  ApprovalNode node, PlanNo planNo) {
  }

  /**
   * 审批历史
   *
   * @param request 历史请求
   * @return 审批记录列表
   */
  @Transactional(readOnly = true)
  public List<ApprovalRecordDTO> getApprovalHistory(GetApprovalHistoryRequest request) {
    ApprovalInstance instance = loadInstanceOrThrow(request.instanceId());
    // 节点名来自静态审批流定义；定义可能被改版或删除，取不到时降级而非中断查询
    ApprovalFlow flow = flowRepository.load(instance.flowId()).orElse(null);

    List<ApprovalRecordDTO> records = new ArrayList<>();
    for (NodeExecution execution : instance.getNodeExecutions()) {
      String nodeName = resolveNodeName(flow, execution.nodeOrder());
      for (ApprovalRecord record : execution.getApprovalRecords()) {
        records.add(new ApprovalRecordDTO(
          record.id(),
          request.instanceId(),
          nodeName,
          record.action().name(),
          record.approverId().value(),
          record.opinion() != null ? record.opinion().value() : null,
          record.operatedAt()));
      }
    }
    return records;
  }

  /**
   * 节点名沿用 {@code ApprovalFlowService.convertToNodeDTO} 的语义（领域模型无 name 字段，
   * 以 nodeType 占位）；审批流或节点定义缺失时降级为 {@code NODE-<order>}。
   */
  private String resolveNodeName(ApprovalFlow flow, NodeOrder nodeOrder) {
    if (flow != null) {
      Optional<ApprovalNode> node = flow.getNode(nodeOrder);
      if (node.isPresent()) {
        return node.get().nodeType().name();
      }
    }
    return "NODE-" + (nodeOrder != null ? nodeOrder.value() : "?");
  }

  /**
   * 加载审批实例或抛出异常
   */
  private ApprovalInstance loadInstanceOrThrow(ApprovalInstanceId instanceId) {
    return instanceRepository.load(instanceId)
      .orElseThrow(() -> new DomainException(
        com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_INSTANCE_NOT_FOUND
      ).withLogDetail("审批实例不存在, instanceId: " + instanceId));
  }

  /**
   * 加载审批流或抛出异常
   */
  private ApprovalFlow loadFlowOrThrow(com.example.approval.types.ApprovalFlowId flowId) {
    return flowRepository.load(flowId)
      .orElseThrow(() -> new DomainException(
        com.example.approval.domain.errorcode.ApprovalDomainErrorCode.APPROVAL_FLOW_NOT_FOUND
      ).withLogDetail("审批流不存在, flowId: " + flowId));
  }

  /**
   * 转换领域对象为DTO
   */
  private ApprovalInstanceDTO convertToDTO(ApprovalInstance instance) {
    return new ApprovalInstanceDTO(
      instance.id(),
      instance.flowId(),
      instance.businessApplicationId().value(),
      instance.businessType(),
      instance.status().name(),
      instance.createdBy(),
      instance.currentNodeOrder().toString(),
      instance.getNodeExecutions().stream()
        .map(this::convertToNodeExecutionDTO)
        .toList(),
      instance.createdAt(),
      instance.isCompleted() ? instance.updatedAt() : null
    );
  }

  /**
   * 转换节点执行记录为DTO
   */
  private com.example.approval.api.dto.NodeExecutionDTO convertToNodeExecutionDTO(
    com.example.approval.domain.aggregate.entity.NodeExecution execution) {
    return new com.example.approval.api.dto.NodeExecutionDTO(
      execution.nodeId(),
      execution.nodeOrder().toString(), // nodeName
      execution.status().name(),
      null, // approver - 需要从审批记录中获取
      null, // comment - 需要从审批记录中获取
      execution.updatedAt()
    );
  }

  /**
   * 转换审批记录为DTO
   */
  private ApprovalRecordDTO convertToApprovalRecordDTO(
    com.example.approval.domain.aggregate.entity.ApprovalRecord record) {
    return new ApprovalRecordDTO(
      record.id(),
      null, // instanceId - 需要从上下文获取
      null, // nodeName - 需要从上下文获取
      record.action().name(),
      record.approverId().value(),
      record.opinion().value(),
      record.operatedAt()
    );
  }
}
