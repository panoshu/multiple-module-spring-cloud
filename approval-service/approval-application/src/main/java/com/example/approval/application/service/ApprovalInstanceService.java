package com.example.approval.application.service;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.*;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.RejectTarget;
import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /**
     * 启动审批
     *
     * @param request 启动请求
     * @return 审批实例ID
     */
    @Transactional
    public ApprovalInstanceId startApproval(StartApprovalRequest request) {
        // 加载审批流
        ApprovalFlow flow = loadFlowOrThrow(request.flowId());

        // 创建审批实例
        ApprovalInstanceId instanceId = ApprovalInstanceId.of(System.currentTimeMillis());
        ApprovalInstance instance = ApprovalInstance.create(
                instanceId,
                request.flowId(),
                FlowVersion.of(flow.flowVersion().value()),
                new ApplicationId(request.businessNo()),
                null, // initiatorPlan
                UserNo.of(request.initiator())
        );

        // 启动审批流程
        instance.start(UserNo.of(request.initiator()));

        // 保存审批实例
        instanceRepository.save(instance);

        // 发布领域事件
        instance.getDomainEvents().forEach(eventBus::publish);
        instance.clearDomainEvents();

        log.info("审批实例创建成功: instanceId={}, flowId={}, businessNo={}",
                instanceId, request.flowId(), request.businessNo());
        return instanceId;
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
                UserNo.of(request.approver())
        );

        // 保存审批实例
        instanceRepository.save(instance);

        // 发布领域事件
        instance.getDomainEvents().forEach(eventBus::publish);
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
                UserNo.of(request.approver())
        );

        // 保存审批实例
        instanceRepository.save(instance);

        // 发布领域事件
        instance.getDomainEvents().forEach(eventBus::publish);
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
                UserNo.of(request.currentApprover())
        );

        // 保存审批实例
        instanceRepository.save(instance);

        // 发布领域事件
        instance.getDomainEvents().forEach(eventBus::publish);
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
        instance.withdraw(UserNo.of(request.initiator()));

        // 保存审批实例
        instanceRepository.save(instance);

        // 发布领域事件
        instance.getDomainEvents().forEach(eventBus::publish);
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
        // TODO: 实现待审批列表查询逻辑
        // 这里需要根据审批人和状态查询待审批的实例列表
        throw new UnsupportedOperationException("待实现待审批列表查询逻辑");
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
        // TODO: 转换审批记录
        return List.of();
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
                null, // businessType
                instance.status().name(),
                instance.createdBy().value(),
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