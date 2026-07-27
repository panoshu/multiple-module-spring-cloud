package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.repository.PlanDelegationRepository;
import com.example.iam.infrastructure.converter.PlanDelegationConverter;
import com.example.iam.infrastructure.entity.PlanDelegationDO;
import com.example.iam.infrastructure.entity.PlanDelegationOperatorDO;
import com.example.iam.infrastructure.entity.PlanDelegationPermissionDO;
import com.example.iam.infrastructure.mapper.PlanDelegationMapper;
import com.example.iam.infrastructure.mapper.PlanDelegationOperatorMapper;
import com.example.iam.infrastructure.mapper.PlanDelegationPermissionMapper;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.example.iam.infrastructure.entity.table.PlanDelegationDOTableDef.PLAN_DELEGATION_DO;
import static com.example.iam.infrastructure.entity.table.PlanDelegationOperatorDOTableDef.PLAN_DELEGATION_OPERATOR_DO;
import static com.example.iam.infrastructure.entity.table.PlanDelegationPermissionDOTableDef.PLAN_DELEGATION_PERMISSION_DO;

/**
 * 计划代办关系聚合根仓储实现。
 *
 * <p>负责 {@link PlanDelegation} 与子表(operator/permission)的持久化操作。
 * 保存时采用"全量替换"策略:先删除子表旧数据,再插入新数据,
 * 确保聚合根内 designatedOperators 与 delegatedPermissions 与数据库一致。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanDelegationRepositoryImpl implements PlanDelegationRepository {

    private final PlanDelegationMapper delegationMapper;
    private final PlanDelegationOperatorMapper operatorMapper;
    private final PlanDelegationPermissionMapper permissionMapper;
    private final PlanDelegationConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<PlanDelegation> load(PlanDelegationId id) {
        if (id == null) {
            return Optional.empty();
        }
        PlanDelegationDO delegationDO = delegationMapper.selectOneById(id.value());
        if (delegationDO == null) {
            return Optional.empty();
        }
        List<PlanDelegationOperatorDO> operatorDOs = operatorMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_OPERATOR_DO.DELEGATION_ID.eq(id.value()))
        );
        List<PlanDelegationPermissionDO> permissionDOs = permissionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_PERMISSION_DO.DELEGATION_ID.eq(id.value()))
        );
        return Optional.ofNullable(converter.toDomain(delegationDO, operatorDOs, permissionDOs));
    }

    @Override
    public void save(PlanDelegation delegation) {
        if (delegation == null) {
            throw new IllegalArgumentException("代办关系不能为空");
        }
        PlanDelegationDO delegationDO = converter.toDO(delegation);
        boolean isInsert = delegationMapper.selectOneById(delegation.id().value()) == null;
        if (isInsert) {
            delegationMapper.insert(delegationDO);
            log.debug("新增代办关系: delegationId={}, delegationCode={}",
                    delegation.id(), delegation.delegationCode());
        } else {
            delegationMapper.update(delegationDO);
            log.debug("更新代办关系: delegationId={}, version={}",
                    delegation.id(), delegation.version());
        }
        saveOperators(delegation);
        savePermissions(delegation);
        eventPublisher.publishFor(delegation);
    }

    @Override
    public void delete(PlanDelegation delegation) {
        if (delegation == null) {
            return;
        }
        deleteChildren(delegation.id().value());
        PlanDelegationDO delegationDO = delegationMapper.selectOneById(delegation.id().value());
        if (delegationDO != null) {
            delegationMapper.delete(delegationDO);
        }
        log.debug("删除代办关系: delegationId={}", delegation.id());
    }

    @Override
    public void deleteById(PlanDelegationId id) {
        if (id == null) {
            return;
        }
        deleteChildren(id.value());
        delegationMapper.deleteById(id.value());
        log.debug("根据ID删除代办关系: delegationId={}", id);
    }

    @Override
    public List<PlanDelegation> loadAll() {
        List<PlanDelegationDO> delegationDOs = delegationMapper.selectAll();
        return delegationDOs.stream()
                .map(this::loadChildren)
                .toList();
    }

    @Override
    public void streamByAppId(PlanDelegationId id, Consumer<AggregateRoot<PlanDelegationId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public Optional<PlanDelegation> findByDelegationCode(String delegationCode) {
        if (delegationCode == null || delegationCode.isBlank()) {
            return Optional.empty();
        }
        PlanDelegationDO delegationDO = delegationMapper.selectOneByQuery(
                QueryWrapper.create().where(PLAN_DELEGATION_DO.DELEGATION_CODE.eq(delegationCode))
        );
        if (delegationDO == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadChildren(delegationDO));
    }

    @Override
    public boolean existsByDelegationCode(String delegationCode) {
        if (delegationCode == null || delegationCode.isBlank()) {
            return false;
        }
        return delegationMapper.selectCountByQuery(
                QueryWrapper.create().where(PLAN_DELEGATION_DO.DELEGATION_CODE.eq(delegationCode))
        ) > 0;
    }

    @Override
    public List<PlanDelegation> findEffectiveByDelegator(String delegatorPlanNo) {
        if (delegatorPlanNo == null || delegatorPlanNo.isBlank()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<PlanDelegationDO> delegationDOs = delegationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_DO.DELEGATOR_PLAN_NO.eq(delegatorPlanNo))
                        .and(PLAN_DELEGATION_DO.STATUS.eq(DelegationStatus.ACTIVE.name()))
                        .and(PLAN_DELEGATION_DO.EFFECTIVE_AT.le(now))
                        .and(
                                PLAN_DELEGATION_DO.EXPIRE_AT.isNull()
                                        .or(PLAN_DELEGATION_DO.EXPIRE_AT.ge(now))
                        )
        );
        return delegationDOs.stream()
                .map(this::loadChildren)
                .toList();
    }

    @Override
    public List<PlanDelegation> findEffectiveByDelegatee(String delegateePlanNo) {
        if (delegateePlanNo == null || delegateePlanNo.isBlank()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<PlanDelegationDO> delegationDOs = delegationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_DO.DELEGATEE_PLAN_NO.eq(delegateePlanNo))
                        .and(PLAN_DELEGATION_DO.STATUS.eq(DelegationStatus.ACTIVE.name()))
                        .and(PLAN_DELEGATION_DO.EFFECTIVE_AT.le(now))
                        .and(
                                PLAN_DELEGATION_DO.EXPIRE_AT.isNull()
                                        .or(PLAN_DELEGATION_DO.EXPIRE_AT.ge(now))
                        )
        );
        return delegationDOs.stream()
                .map(this::loadChildren)
                .toList();
    }

    @Override
    public List<PlanDelegation> findByStatus(DelegationStatus status) {
        if (status == null) {
            return List.of();
        }
        List<PlanDelegationDO> delegationDOs = delegationMapper.selectListByQuery(
                QueryWrapper.create().where(PLAN_DELEGATION_DO.STATUS.eq(status.name()))
        );
        return delegationDOs.stream()
                .map(this::loadChildren)
                .toList();
    }

    @Override
    public List<PlanDelegation> findExpiredActive() {
        LocalDateTime now = LocalDateTime.now();
        List<PlanDelegationDO> delegationDOs = delegationMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_DO.STATUS.eq(DelegationStatus.ACTIVE.name()))
                        .and(PLAN_DELEGATION_DO.EXPIRE_AT.isNotNull())
                        .and(PLAN_DELEGATION_DO.EXPIRE_AT.lt(now))
        );
        return delegationDOs.stream()
                .map(this::loadChildren)
                .toList();
    }

    /**
     * 加载代办关系的子表数据并组装聚合根。
     */
    private PlanDelegation loadChildren(PlanDelegationDO delegationDO) {
        List<PlanDelegationOperatorDO> operatorDOs = operatorMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_OPERATOR_DO.DELEGATION_ID.eq(delegationDO.getId()))
        );
        List<PlanDelegationPermissionDO> permissionDOs = permissionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_PERMISSION_DO.DELEGATION_ID.eq(delegationDO.getId()))
        );
        return converter.toDomain(delegationDO, operatorDOs, permissionDOs);
    }

    /**
     * 全量替换指定操作员(先删后插)。
     */
    private void saveOperators(PlanDelegation delegation) {
        Long delegationId = delegation.id().value();
        Set<Long> existingOperators = operatorMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_OPERATOR_DO.DELEGATION_ID.eq(delegationId))
        ).stream().map(PlanDelegationOperatorDO::getOperatorId).collect(Collectors.toSet());

        Set<Long> currentOperators = new HashSet<>(delegation.designatedOperators());
        if (existingOperators.equals(currentOperators)) {
            return;
        }
        operatorMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_OPERATOR_DO.DELEGATION_ID.eq(delegationId))
        );
        for (Long operatorId : currentOperators) {
            PlanDelegationOperatorDO operatorDO = converter.toOperatorDO(delegation, delegationId, operatorId);
            operatorMapper.insert(operatorDO);
        }
    }

    /**
     * 全量替换授权权限(先删后插)。
     */
    private void savePermissions(PlanDelegation delegation) {
        Long delegationId = delegation.id().value();
        Set<DelegationPermission> currentPermissions = new HashSet<>(delegation.delegatedPermissions());
        permissionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_PERMISSION_DO.DELEGATION_ID.eq(delegationId))
        );
        for (DelegationPermission permission : currentPermissions) {
            PlanDelegationPermissionDO permissionDO = converter.toPermissionDO(delegation, permission, delegationId);
            permissionMapper.insert(permissionDO);
        }
    }

    /**
     * 删除子表数据(operator + permission)。
     */
    private void deleteChildren(Long delegationId) {
        operatorMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_OPERATOR_DO.DELEGATION_ID.eq(delegationId))
        );
        permissionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(PLAN_DELEGATION_PERMISSION_DO.DELEGATION_ID.eq(delegationId))
        );
    }
}
