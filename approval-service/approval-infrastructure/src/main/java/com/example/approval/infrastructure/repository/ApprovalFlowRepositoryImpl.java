package com.example.approval.infrastructure.repository;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.infrastructure.converter.ApprovalFlowConverter;
import com.example.approval.infrastructure.entity.ApprovalFlowDO;
import com.example.approval.infrastructure.entity.ApprovalNodeDO;
import com.example.approval.infrastructure.mapper.ApprovalFlowMapper;
import com.example.approval.infrastructure.mapper.ApprovalNodeMapper;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.enums.FlowStatus;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.approval.infrastructure.entity.table.ApprovalFlowDOTableDef.APPROVAL_FLOW_DO;
import static com.example.approval.infrastructure.entity.table.ApprovalNodeDOTableDef.APPROVAL_NODE_DO;

/**
 * 审批流仓储实现
 * 负责审批流聚合根的持久化操作
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApprovalFlowRepositoryImpl implements ApprovalFlowRepository {

    private final ApprovalFlowMapper flowMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalFlowConverter converter;

    @Override
    public Optional<ApprovalFlow> load(ApprovalFlowId flowId) {
        if (flowId == null) {
            return Optional.empty();
        }

        // 查询审批流
        ApprovalFlowDO flowDO = flowMapper.selectOneById(flowId.value().toString());
        if (flowDO == null) {
            return Optional.empty();
        }

        // 查询审批节点列表
        List<ApprovalNodeDO> nodeDOs = nodeMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_NODE_DO.FLOW_ID.eq(flowId.value().toString()))
                        .orderBy(APPROVAL_NODE_DO.NODE_ORDER.asc())
        );

        // 转换为领域对象
        ApprovalFlow flow = converter.toDomain(flowDO);

        // 转换节点列表
        List<ApprovalNode> nodes = nodeDOs.stream()
                .map(converter::toNodeDomain)
                .toList();

        // 使用重建方法创建完整的聚合根
        return Optional.of(ApprovalFlow.reconstitute(
                flow.id(),
                flow.flowName(),
                flow.matchRules(),
                nodes,
                flow.flowVersion(),
                flow.status(),
                flow.createdBy(),
                flow.updatedBy(),
                flow.createdAt(),
                flow.updatedAt(),
                flow.version()
        ));
    }

    @Override
    public Optional<ApprovalFlow> load(ApprovalFlowId flowId, FlowVersion version) {
        if (flowId == null || version == null) {
            return Optional.empty();
        }

        // 查询指定版本的审批流
        ApprovalFlowDO flowDO = flowMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_FLOW_DO.ID.eq(flowId.value().toString()))
                        .and(APPROVAL_FLOW_DO.FLOW_VERSION.eq(version.value()))
        );

        if (flowDO == null) {
            return Optional.empty();
        }

        // 查询审批节点列表
        List<ApprovalNodeDO> nodeDOs = nodeMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_NODE_DO.FLOW_ID.eq(flowId.value().toString()))
                        .orderBy(APPROVAL_NODE_DO.NODE_ORDER.asc())
        );

        // 转换为领域对象
        ApprovalFlow flow = converter.toDomain(flowDO);

        // 转换节点列表
        List<ApprovalNode> nodes = nodeDOs.stream()
                .map(converter::toNodeDomain)
                .toList();

        // 使用重建方法创建完整的聚合根
        return Optional.of(ApprovalFlow.reconstitute(
                flow.id(),
                flow.flowName(),
                flow.matchRules(),
                nodes,
                flow.flowVersion(),
                flow.status(),
                flow.createdBy(),
                flow.updatedBy(),
                flow.createdAt(),
                flow.updatedAt(),
                flow.version()
        ));
    }

    @Override
    public void save(ApprovalFlow flow) {
        if (flow == null) {
            throw new IllegalArgumentException("审批流不能为空");
        }

        // 转换审批流DO
        ApprovalFlowDO flowDO = converter.toDO(flow);

        // 判断是新增还是更新
        ApprovalFlowDO existingFlow = flowMapper.selectOneById(flow.id().value().toString());
        if (existingFlow == null) {
            // 新增
            flowMapper.insert(flowDO);
            log.debug("新增审批流: flowId={}", flow.id());
        } else {
            // 更新
            flowMapper.update(flowDO);
            log.debug("更新审批流: flowId={}, version={}", flow.id(), flow.version());
        }

        // 保存审批节点
        saveNodes(flow.id(), flow.getNodes());
    }

    @Override
    public void delete(ApprovalFlow flow) {
        if (flow == null) {
            return;
        }
        String flowIdStr = flow.id().value().toString();
        // 先删除子表（节点）
        nodeMapper.deleteByQuery(
                QueryWrapper.create().where(APPROVAL_NODE_DO.FLOW_ID.eq(flowIdStr))
        );
        // 再删除主表
        flowMapper.deleteById(flowIdStr);
        log.debug("删除审批流: flowId={}", flow.id());
    }

    @Override
    public void deleteById(ApprovalFlowId id) {
        if (id == null) {
            return;
        }
        String flowIdStr = id.value().toString();
        nodeMapper.deleteByQuery(
                QueryWrapper.create().where(APPROVAL_NODE_DO.FLOW_ID.eq(flowIdStr))
        );
        flowMapper.deleteById(flowIdStr);
        log.debug("根据ID删除审批流: flowId={}", id);
    }

    @Override
    public List<ApprovalFlow> loadAll() {
        List<ApprovalFlowDO> flowDOs = flowMapper.selectAll();
        return flowDOs.stream()
                .map(this::convertToFlowWithNodes)
                .toList();
    }

    @Override
    public void streamByAppId(ApprovalFlowId id, Consumer<AggregateRoot<ApprovalFlowId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public List<ApprovalFlow> findByStatus(FlowStatus status) {
        if (status == null) {
            return Collections.emptyList();
        }

        List<ApprovalFlowDO> flowDOs = flowMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_FLOW_DO.STATUS.eq(status.name()))
                        .orderBy(APPROVAL_FLOW_DO.CREATE_TIME.desc())
        );

        return flowDOs.stream()
                .map(this::convertToFlowWithNodes)
                .toList();
    }

    @Override
    public List<ApprovalFlow> findActiveByMatchRules(MatchRules rules) {
        if (rules == null) {
            return Collections.emptyList();
        }

        // 查询激活状态的审批流
        List<ApprovalFlowDO> flowDOs = flowMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_FLOW_DO.STATUS.eq(FlowStatus.ACTIVE.name()))
                        .orderBy(APPROVAL_FLOW_DO.CREATE_TIME.desc())
        );

        // TODO: 实现更精确的匹配规则查询逻辑
        // 当前实现返回所有激活的审批流，实际应用中需要根据 MatchRules 进行精确匹配
        return flowDOs.stream()
                .map(this::convertToFlowWithNodes)
                .toList();
    }

    /**
     * 保存审批节点列表
     *
     * @param flowId 审批流ID
     * @param nodes  审批节点列表
     */
    private void saveNodes(ApprovalFlowId flowId, List<ApprovalNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // 删除旧的节点
        nodeMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_NODE_DO.FLOW_ID.eq(flowId.value().toString()))
        );

        // 插入新的节点
        for (ApprovalNode node : nodes) {
            ApprovalNodeDO nodeDO = converter.toNodeDO(node);
            nodeDO.setFlowId(flowId.value().toString());
            nodeMapper.insert(nodeDO);
        }

        log.debug("保存审批节点: flowId={}, nodeCount={}", flowId, nodes.size());
    }

    /**
     * 将审批流DO转换为包含节点的聚合根
     *
     * @param flowDO 审批流DO
     * @return 审批流聚合根
     */
    private ApprovalFlow convertToFlowWithNodes(ApprovalFlowDO flowDO) {
        // 查询审批节点列表
        List<ApprovalNodeDO> nodeDOs = nodeMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APPROVAL_NODE_DO.FLOW_ID.eq(flowDO.getId()))
                        .orderBy(APPROVAL_NODE_DO.NODE_ORDER.asc())
        );

        // 转换为领域对象
        ApprovalFlow flow = converter.toDomain(flowDO);

        // 转换节点列表
        List<ApprovalNode> nodes = nodeDOs.stream()
                .map(converter::toNodeDomain)
                .toList();

        // 使用重建方法创建完整的聚合根
        return ApprovalFlow.reconstitute(
                flow.id(),
                flow.flowName(),
                flow.matchRules(),
                nodes,
                flow.flowVersion(),
                flow.status(),
                flow.createdBy(),
                flow.updatedBy(),
                flow.createdAt(),
                flow.updatedAt(),
                flow.version()
        );
    }
}