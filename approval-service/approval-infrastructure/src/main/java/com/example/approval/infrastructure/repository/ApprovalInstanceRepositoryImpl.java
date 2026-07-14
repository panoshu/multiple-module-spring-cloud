package com.example.approval.infrastructure.repository;

import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.infrastructure.converter.ApprovalInstanceConverter;
import com.example.approval.infrastructure.entity.ApprovalInstanceDO;
import com.example.approval.infrastructure.entity.ApprovalNodeExecutionDO;
import com.example.approval.infrastructure.entity.ApprovalRecordDO;
import com.example.approval.infrastructure.mapper.ApprovalInstanceMapper;
import com.example.approval.infrastructure.mapper.ApprovalNodeExecutionMapper;
import com.example.approval.infrastructure.mapper.ApprovalRecordMapper;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.approval.infrastructure.entity.table.ApprovalInstanceDOTableDef.APPROVAL_INSTANCE_DO;
import static com.example.approval.infrastructure.entity.table.ApprovalNodeExecutionDOTableDef.APPROVAL_NODE_EXECUTION_DO;
import static com.example.approval.infrastructure.entity.table.ApprovalRecordDOTableDef.APPROVAL_RECORD_DO;

/**
 * 审批实例仓储实现
 * 负责审批实例聚合根的持久化操作
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApprovalInstanceRepositoryImpl implements ApprovalInstanceRepository {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalNodeExecutionMapper executionMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalInstanceConverter converter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<ApprovalInstance> load(ApprovalInstanceId instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }

        // 查询审批实例
        ApprovalInstanceDO instanceDO = instanceMapper.selectOneById(instanceId.value().toString());
        if (instanceDO == null) {
            return Optional.empty();
        }

        // 查询节点执行记录
        List<ApprovalNodeExecutionDO> executionDOs = executionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_NODE_EXECUTION_DO.INSTANCE_ID.eq(instanceId.value().toString()))
                        .orderBy(APPROVAL_NODE_EXECUTION_DO.NODE_ORDER.asc())
        );

        // 查询每个执行记录的审批记录
        List<NodeExecution> executions = executionDOs.stream()
                .map(executionDO -> {
                    NodeExecution execution = converter.toExecutionDomain(executionDO);

                    // 查询审批记录
                    List<ApprovalRecordDO> recordDOs = recordMapper.selectListByQuery(
                            QueryWrapper.create()
                                    .where(APPROVAL_RECORD_DO.EXECUTION_ID.eq(executionDO.getId()))
                                    .orderBy(APPROVAL_RECORD_DO.OPERATED_AT.asc())
                    );

                    // 转换审批记录
                    List<ApprovalRecord> records = recordDOs.stream()
                            .map(converter::toRecordDomain)
                            .toList();

                    // 使用重建方法创建完整的执行记录
                    return NodeExecution.reconstitute(
                            execution.id(),
                            execution.nodeId(),
                            execution.nodeOrder(),
                            execution.status(),
                            records,
                            execution.startedAt(),
                            execution.completedAt(),
                            execution.createdBy(),
                            execution.updatedBy(),
                            execution.createdAt(),
                            execution.updatedAt(),
                            execution.version()
                    );
                })
                .toList();

        // 转换为领域对象
        ApprovalInstance instance = converter.toDomain(instanceDO);

        // 使用重建方法创建完整的聚合根
        return Optional.of(ApprovalInstance.reconstitute(
                instance.id(),
                instance.flowId(),
                instance.flowVersion(),
                instance.businessApplicationId(),
                instance.currentNodeOrder(),
                instance.status(),
                instance.initiatorPlan(),
                instance.currentPlan(),
                executions,
                instance.createdBy(),
                instance.updatedBy(),
                instance.createdAt(),
                instance.updatedAt(),
                instance.version()
        ));
    }

    @Override
    public void save(ApprovalInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("审批实例不能为空");
        }

        // 转换审批实例DO
        ApprovalInstanceDO instanceDO = converter.toDO(instance);

        // 判断是新增还是更新
        ApprovalInstanceDO existingInstance = instanceMapper.selectOneById(instance.id().value().toString());
        if (existingInstance == null) {
            // 新增
            instanceMapper.insert(instanceDO);
            log.debug("新增审批实例: instanceId={}", instance.id());
        } else {
            // 更新
            instanceMapper.update(instanceDO);
            log.debug("更新审批实例: instanceId={}, version={}", instance.id(), instance.version());
        }

        // 保存节点执行记录和审批记录
        saveNodeExecutions(instance.id(), instance.getNodeExecutions());

        // 发布领域事件
        publishDomainEvents(instance);
    }

    @Override
    public Optional<ApprovalInstance> findByBusinessApplicationId(ApplicationId applicationId) {
        if (applicationId == null) {
            return Optional.empty();
        }

        ApprovalInstanceDO instanceDO = instanceMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_INSTANCE_DO.BUSINESS_APPLICATION_ID.eq(applicationId.value()))
        );

        if (instanceDO == null) {
            return Optional.empty();
        }

        return load(converter.toDomain(instanceDO).id());
    }

    @Override
    public List<ApprovalInstance> findByApproverId(UserNo approverId, InstanceStatus status) {
        if (approverId == null) {
            return Collections.emptyList();
        }

        // TODO: 实现根据审批人ID和状态查询审批实例
        // 当前实现需要通过复杂的关联查询才能找到待审批人审批的实例
        // 实际应用中可能需要设计专门的待审批视图表或缓存机制
        log.warn("findByApproverId 方法尚未完整实现");
        return Collections.emptyList();
    }

    /**
     * 保存节点执行记录和审批记录
     *
     * @param instanceId 审批实例ID
     * @param executions  节点执行记录列表
     */
    private void saveNodeExecutions(ApprovalInstanceId instanceId, List<NodeExecution> executions) {
        if (executions == null || executions.isEmpty()) {
            return;
        }

        for (NodeExecution execution : executions) {
            // 转换执行记录DO
            ApprovalNodeExecutionDO executionDO = converter.toExecutionDO(execution);
            executionDO.setInstanceId(instanceId.value().toString());

            // 判断是新增还是更新
            ApprovalNodeExecutionDO existingExecution = executionMapper.selectOneById(execution.id().value().toString());
            if (existingExecution == null) {
                // 新增
                executionMapper.insert(executionDO);
            } else {
                // 更新
                executionMapper.update(executionDO);
            }

            // 保存审批记录
            saveApprovalRecords(executionDO.getId(), execution.getApprovalRecords());
        }

        log.debug("保存节点执行记录: instanceId={}, executionCount={}", instanceId, executions.size());
    }

    /**
     * 保存审批记录列表
     *
     * @param executionId 节点执行ID
     * @param records      审批记录列表
     */
    private void saveApprovalRecords(String executionId, List<ApprovalRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        for (ApprovalRecord record : records) {
            // 转换审批记录DO
            ApprovalRecordDO recordDO = converter.toRecordDO(record);
            recordDO.setExecutionId(executionId);

            // 判断是新增还是更新
            ApprovalRecordDO existingRecord = recordMapper.selectOneById(record.id().value().toString());
            if (existingRecord == null) {
                // 新增
                recordMapper.insert(recordDO);
            } else {
                // 更新
                recordMapper.update(recordDO);
            }
        }

        log.debug("保存审批记录: executionId={}, recordCount={}", executionId, records.size());
    }

    /**
     * 发布领域事件
     *
     * @param instance 审批实例聚合根
     */
    private void publishDomainEvents(ApprovalInstance instance) {
        List<DomainEvent> events = instance.getDomainEvents();
        if (events.isEmpty()) {
            return;
        }

        for (DomainEvent event : events) {
            try {
                eventPublisher.publishEvent(event);
                log.debug("发布领域事件: eventId={}, eventType={}",
                        event.eventId(), event.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("发布领域事件失败: eventId={}, eventType={}",
                        event.eventId(), event.getClass().getSimpleName(), e);
                // 继续发布其他事件，不中断流程
            }
        }

        // 清理已发布的事件
        instance.clearDomainEvents();
    }
}