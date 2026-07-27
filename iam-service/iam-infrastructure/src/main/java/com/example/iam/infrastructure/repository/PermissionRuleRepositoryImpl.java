package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.repository.PermissionRuleRepository;
import com.example.iam.infrastructure.converter.PermissionRuleConverter;
import com.example.iam.infrastructure.entity.PermissionRuleDO;
import com.example.iam.infrastructure.mapper.PermissionRuleMapper;
import com.example.iam.types.PermissionRuleId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.iam.infrastructure.entity.table.PermissionRuleDOTableDef.PERMISSION_RULE_DO;

/**
 * 权限规则聚合根仓储实现。
 *
 * <p>负责 {@link PermissionRule} 的持久化操作。
 * {@code allowedActions} 以 JSON 数组字符串存储,通过 Converter 完成序列化。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PermissionRuleRepositoryImpl implements PermissionRuleRepository {

    private final PermissionRuleMapper ruleMapper;
    private final PermissionRuleConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<PermissionRule> load(PermissionRuleId id) {
        if (id == null) {
            return Optional.empty();
        }
        PermissionRuleDO ruleDO = ruleMapper.selectOneById(id.value());
        return Optional.ofNullable(converter.toDomain(ruleDO));
    }

    @Override
    public void save(PermissionRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("权限规则不能为空");
        }
        PermissionRuleDO ruleDO = converter.toDO(rule);
        boolean isInsert = ruleMapper.selectOneById(rule.id().value()) == null;
        if (isInsert) {
            ruleMapper.insert(ruleDO);
            log.debug("新增权限规则: ruleId={}, ruleCode={}", rule.id(), rule.ruleCode());
        } else {
            ruleMapper.update(ruleDO);
            log.debug("更新权限规则: ruleId={}, ruleCode={}, version={}",
                    rule.id(), rule.ruleCode(), rule.version());
        }
        eventPublisher.publishFor(rule);
    }

    @Override
    public void delete(PermissionRule rule) {
        if (rule == null) {
            return;
        }
        PermissionRuleDO ruleDO = ruleMapper.selectOneById(rule.id().value());
        if (ruleDO != null) {
            ruleMapper.delete(ruleDO);
        }
        log.debug("删除权限规则: ruleId={}", rule.id());
    }

    @Override
    public void deleteById(PermissionRuleId id) {
        if (id == null) {
            return;
        }
        ruleMapper.deleteById(id.value());
        log.debug("根据ID删除权限规则: ruleId={}", id);
    }

    @Override
    public List<PermissionRule> loadAll() {
        List<PermissionRuleDO> ruleDOs = ruleMapper.selectAll();
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public void streamByAppId(PermissionRuleId id, Consumer<AggregateRoot<PermissionRuleId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public Optional<PermissionRule> findByRuleCode(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return Optional.empty();
        }
        PermissionRuleDO ruleDO = ruleMapper.selectOneByQuery(
                QueryWrapper.create().where(PERMISSION_RULE_DO.RULE_CODE.eq(ruleCode))
        );
        return Optional.ofNullable(converter.toDomain(ruleDO));
    }

    @Override
    public boolean existsByRuleCode(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return false;
        }
        return ruleMapper.selectCountByQuery(
                QueryWrapper.create().where(PERMISSION_RULE_DO.RULE_CODE.eq(ruleCode))
        ) > 0;
    }

    @Override
    public List<PermissionRule> findBySubject(SubjectType subjectType, String subjectId) {
        if (subjectType == null || subjectId == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<PermissionRuleDO> ruleDOs = ruleMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PERMISSION_RULE_DO.SUBJECT_TYPE.eq(subjectType.name()))
                        .and(PERMISSION_RULE_DO.SUBJECT_ID.eq(subjectId))
                        .and(PERMISSION_RULE_DO.STATUS.eq(RuleStatus.ACTIVE.name()))
                        .and(PERMISSION_RULE_DO.EFFECTIVE_AT.le(now))
                        .and(
                                PERMISSION_RULE_DO.EXPIRE_AT.isNull()
                                        .or(PERMISSION_RULE_DO.EXPIRE_AT.ge(now))
                        )
        );
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public List<PermissionRule> findEffectiveRulesForContext(String customerNo,
                                                               String operationMode,
                                                               String productNo,
                                                               String planNo,
                                                               String accountManagerCode) {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper query = QueryWrapper.create()
                .where(PERMISSION_RULE_DO.STATUS.eq(RuleStatus.ACTIVE.name()))
                .and(PERMISSION_RULE_DO.EFFECTIVE_AT.le(now))
                .and(
                        PERMISSION_RULE_DO.EXPIRE_AT.isNull()
                                .or(PERMISSION_RULE_DO.EXPIRE_AT.ge(now))
                );

        // 主体维度 IN (CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER),
        // 同维度下主体标识匹配查询参数
        query = query.and(
                PERMISSION_RULE_DO.SUBJECT_TYPE.eq(SubjectType.CUSTOMER.name())
                        .and(PERMISSION_RULE_DO.SUBJECT_ID.eq(customerNo))
                        .or(PERMISSION_RULE_DO.SUBJECT_TYPE.eq(SubjectType.OPERATION_MODE.name())
                                .and(PERMISSION_RULE_DO.SUBJECT_ID.eq(operationMode)))
                        .or(PERMISSION_RULE_DO.SUBJECT_TYPE.eq(SubjectType.PRODUCT.name())
                                .and(PERMISSION_RULE_DO.SUBJECT_ID.eq(productNo)))
                        .or(PERMISSION_RULE_DO.SUBJECT_TYPE.eq(SubjectType.PLAN.name())
                                .and(PERMISSION_RULE_DO.SUBJECT_ID.eq(planNo)))
                        .or(PERMISSION_RULE_DO.SUBJECT_TYPE.eq(SubjectType.ACCOUNT_MANAGER.name())
                                .and(PERMISSION_RULE_DO.SUBJECT_ID.eq(accountManagerCode)))
        );

        List<PermissionRuleDO> ruleDOs = ruleMapper.selectListByQuery(
                query.orderBy(PERMISSION_RULE_DO.PRIORITY.desc())
        );
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public List<PermissionRule> findByStatus(RuleStatus status) {
        if (status == null) {
            return List.of();
        }
        List<PermissionRuleDO> ruleDOs = ruleMapper.selectListByQuery(
                QueryWrapper.create().where(PERMISSION_RULE_DO.STATUS.eq(status.name()))
        );
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }
}
