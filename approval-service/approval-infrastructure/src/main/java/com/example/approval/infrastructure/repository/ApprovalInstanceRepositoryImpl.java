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
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
  private final EventBus eventBus;

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

    return Optional.of(convertToInstanceWithDetails(instanceDO));
  }

  /**
   * 将审批实例DO转换为包含节点执行记录和审批记录的完整聚合根
   *
   * @param instanceDO 审批实例DO
   * @return 审批实例聚合根
   */
  private ApprovalInstance convertToInstanceWithDetails(ApprovalInstanceDO instanceDO) {
    String instanceIdStr = instanceDO.getId();

    // 查询节点执行记录
    List<ApprovalNodeExecutionDO> executionDOs = executionMapper.selectListByQuery(
      QueryWrapper.create()
        .where(APPROVAL_NODE_EXECUTION_DO.INSTANCE_ID.eq(instanceIdStr))
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
    return ApprovalInstance.reconstitute(
      instance.id(),
      instance.flowId(),
      instance.flowVersion(),
      instance.businessApplicationId(),
      instance.businessType(),
      instance.currentNodeOrder(),
      instance.status(),
      instance.initiatorPlan(),
      instance.currentPlan(),
      executions,
      instance.createdBy(),
      instance.updatedBy(),
      instance.createdAt(),
      instance.updatedAt(),
      instance.version(),
      // 必须透传 requestNo：二次重建遗漏会让审批结果事件丢失外部协作幂等键，
      // kernel 只能走 businessNo 回退路径并重跑步骤管线。
      instance.requestNo()
    );
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
      // 更新：领域方法可能已 markUpdated 提前递增版本，DO 的 WHERE 版本必须回退为数据库当前值，
      // 否则 UPDATE 匹配 0 行且静默丢失状态变更（审批永远停在 APPROVING）。
      int persistedVersion = existingInstance.getVersion() != null ? existingInstance.getVersion() : 0;
      long domainVersion = instance.version() != null ? instance.version().value() : 0L;
      if (domainVersion != persistedVersion && domainVersion != persistedVersion + 1L) {
        throw new SystemException(CommonError.CONCURRENCY_ERROR)
          .withLogDetail("审批实例版本冲突: instanceId=" + instance.id()
            + ", domainVersion=" + domainVersion + ", persistedVersion=" + persistedVersion);
      }
      instanceDO.setVersion(persistedVersion);
      if (instanceMapper.update(instanceDO) != 1) {
        throw new SystemException(CommonError.CONCURRENCY_ERROR)
          .withLogDetail("审批实例更新失败（版本冲突）: instanceId=" + instance.id());
      }
      if (domainVersion == persistedVersion) {
        instance.syncVersionAfterUpdate();
      }
      log.debug("更新审批实例: instanceId={}, version={}", instance.id(), instance.version());
    }

    // 保存节点执行记录和审批记录
    saveNodeExecutions(instance.id(), instance.getNodeExecutions());

    // 发布领域事件
    publishDomainEvents(instance);
  }

  @Override
  public void delete(ApprovalInstance instance) {
    if (instance == null) {
      return;
    }
    String instanceIdStr = instance.id().value().toString();
    // 先删除审批记录（通过 execution 关联）
    recordMapper.deleteByQuery(
      QueryWrapper.create()
        .where(APPROVAL_RECORD_DO.EXECUTION_ID.in(
          QueryWrapper.create()
            .select(APPROVAL_NODE_EXECUTION_DO.ID)
            .from(APPROVAL_NODE_EXECUTION_DO)
            .where(APPROVAL_NODE_EXECUTION_DO.INSTANCE_ID.eq(instanceIdStr))
        ))
    );
    // 再删除节点执行记录
    executionMapper.deleteByQuery(
      QueryWrapper.create().where(APPROVAL_NODE_EXECUTION_DO.INSTANCE_ID.eq(instanceIdStr))
    );
    // 最后删除主表
    instanceMapper.deleteById(instanceIdStr);
    log.debug("删除审批实例: instanceId={}", instance.id());
  }

  @Override
  public void deleteById(ApprovalInstanceId id) {
    if (id == null) {
      return;
    }
    String instanceIdStr = id.value().toString();
    recordMapper.deleteByQuery(
      QueryWrapper.create()
        .where(APPROVAL_RECORD_DO.EXECUTION_ID.in(
          QueryWrapper.create()
            .select(APPROVAL_NODE_EXECUTION_DO.ID)
            .from(APPROVAL_NODE_EXECUTION_DO)
            .where(APPROVAL_NODE_EXECUTION_DO.INSTANCE_ID.eq(instanceIdStr))
        ))
    );
    executionMapper.deleteByQuery(
      QueryWrapper.create().where(APPROVAL_NODE_EXECUTION_DO.INSTANCE_ID.eq(instanceIdStr))
    );
    instanceMapper.deleteById(instanceIdStr);
    log.debug("根据ID删除审批实例: instanceId={}", id);
  }

  @Override
  public List<ApprovalInstance> loadAll() {
    List<ApprovalInstanceDO> instanceDOs = instanceMapper.selectAll();
    return instanceDOs.stream()
      .map(this::convertToInstanceWithDetails)
      .toList();
  }

  @Override
  public Optional<ApprovalInstance> findByBusinessApplicationId(String applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }

    ApprovalInstanceDO instanceDO = instanceMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(APPROVAL_INSTANCE_DO.BUSINESS_APPLICATION_ID.eq(applicationId))
    );

    if (instanceDO == null) {
      return Optional.empty();
    }

    return load(converter.toDomain(instanceDO).id());
  }

  @Override
  public Optional<ApprovalInstance> findByRequestNo(String requestNo) {
    if (requestNo == null || requestNo.isBlank()) {
      return Optional.empty();
    }
    ApprovalInstanceDO instanceDO = instanceMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(APPROVAL_INSTANCE_DO.REQUEST_NO.eq(requestNo))
    );
    if (instanceDO == null) {
      return Optional.empty();
    }
    return load(converter.toDomain(instanceDO).id());
  }

  @Override
  public List<ApprovalInstance> findByStatuses(Collection<InstanceStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) {
      return List.of();
    }
    List<String> names = statuses.stream().map(Enum::name).toList();
    List<ApprovalInstanceDO> rows = instanceMapper.selectListByQuery(
      QueryWrapper.create()
        .where(APPROVAL_INSTANCE_DO.STATUS.in(names))
        .orderBy(APPROVAL_INSTANCE_DO.CREATE_TIME.desc())
    );
    return rows.stream().map(this::convertToInstanceWithDetails).toList();
  }

  /**
   * 保存节点执行记录和审批记录
   *
   * @param instanceId 审批实例ID
   * @param executions 节点执行记录列表
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
        // 更新：与实例相同，DO 版本回退到数据库当前值后再做乐观锁更新
        int persistedVersion = existingExecution.getVersion() != null ? existingExecution.getVersion() : 0;
        long domainVersion = execution.version() != null ? execution.version().value() : 0L;
        if (domainVersion != persistedVersion && domainVersion != persistedVersion + 1L) {
          throw new SystemException(CommonError.CONCURRENCY_ERROR)
            .withLogDetail("节点执行记录版本冲突: executionId=" + execution.id());
        }
        executionDO.setVersion(persistedVersion);
        if (executionMapper.update(executionDO) != 1) {
          throw new SystemException(CommonError.CONCURRENCY_ERROR)
            .withLogDetail("节点执行记录更新失败: executionId=" + execution.id());
        }
        if (domainVersion == persistedVersion) {
          execution.syncVersionAfterUpdate();
        }
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
   * @param records     审批记录列表
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
        // 更新：DO 版本回退到数据库当前值，避免 markUpdated 已递增导致 0 行更新
        int persistedVersion = existingRecord.getVersion() != null ? existingRecord.getVersion() : 0;
        long domainVersion = record.version() != null ? record.version().value() : 0L;
        if (domainVersion != persistedVersion && domainVersion != persistedVersion + 1L) {
          throw new SystemException(CommonError.CONCURRENCY_ERROR)
            .withLogDetail("审批记录版本冲突: recordId=" + record.id());
        }
        recordDO.setVersion(persistedVersion);
        if (recordMapper.update(recordDO) != 1) {
          throw new SystemException(CommonError.CONCURRENCY_ERROR)
            .withLogDetail("审批记录更新失败: recordId=" + record.id());
        }
        if (domainVersion == persistedVersion) {
          record.syncVersionAfterUpdate();
        }
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
    List<DomainEvent> events = instance.domainEvents();
    if (events.isEmpty()) {
      return;
    }

    for (DomainEvent event : events) {
      try {
        // 走 EventBus（Outbox + 本地 Spring 分发 + Redis 远端投递）：
        // 审批结果事件必须跨服务到达 kernel，仅用 ApplicationEventPublisher 会永远留在本进程。
        eventBus.publish(event);
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
