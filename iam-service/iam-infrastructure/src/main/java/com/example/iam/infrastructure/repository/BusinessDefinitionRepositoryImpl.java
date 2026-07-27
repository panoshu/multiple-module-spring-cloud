package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.repository.BusinessDefinitionRepository;
import com.example.iam.infrastructure.converter.BusinessDefinitionConverter;
import com.example.iam.infrastructure.entity.BusinessActionDO;
import com.example.iam.infrastructure.entity.BusinessDefinitionDO;
import com.example.iam.infrastructure.mapper.BusinessActionMapper;
import com.example.iam.infrastructure.mapper.BusinessDefinitionMapper;
import com.example.iam.types.BusinessDefinitionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.example.iam.infrastructure.entity.table.BusinessActionDOTableDef.BUSINESS_ACTION_DO;
import static com.example.iam.infrastructure.entity.table.BusinessDefinitionDOTableDef.BUSINESS_DEFINITION_DO;

/**
 * 业务定义聚合根仓储实现。
 *
 * <p>负责 {@link BusinessDefinition} 与子表 {@link BusinessActionDO} 的持久化操作。
 * 保存时采用"全量替换"策略:先删除子表旧数据,再插入新数据。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BusinessDefinitionRepositoryImpl implements BusinessDefinitionRepository {

    private final BusinessDefinitionMapper definitionMapper;
    private final BusinessActionMapper actionMapper;
    private final BusinessDefinitionConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<BusinessDefinition> load(BusinessDefinitionId id) {
        if (id == null) {
            return Optional.empty();
        }
        BusinessDefinitionDO definitionDO = definitionMapper.selectOneById(id.value());
        if (definitionDO == null) {
            return Optional.empty();
        }
        List<BusinessActionDO> actionDOs = actionMapper.selectListByQuery(
                QueryWrapper.create().where(BUSINESS_ACTION_DO.DEFINITION_ID.eq(id.value()))
        );
        return Optional.ofNullable(converter.toDomain(definitionDO, actionDOs));
    }

    @Override
    public void save(BusinessDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("业务定义不能为空");
        }
        BusinessDefinitionDO definitionDO = converter.toDO(definition);
        boolean isInsert = definitionMapper.selectOneById(definition.id().value()) == null;
        if (isInsert) {
            definitionMapper.insert(definitionDO);
            log.debug("新增业务定义: definitionId={}, businessCode={}",
                    definition.id(), definition.businessCode());
        } else {
            definitionMapper.update(definitionDO);
            log.debug("更新业务定义: definitionId={}, businessCode={}, version={}",
                    definition.id(), definition.businessCode(), definition.version());
        }
        saveActions(definition);
        eventPublisher.publishFor(definition);
    }

    @Override
    public void delete(BusinessDefinition definition) {
        if (definition == null) {
            return;
        }
        actionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(BUSINESS_ACTION_DO.DEFINITION_ID.eq(definition.id().value()))
        );
        BusinessDefinitionDO definitionDO = definitionMapper.selectOneById(definition.id().value());
        if (definitionDO != null) {
            definitionMapper.delete(definitionDO);
        }
        log.debug("删除业务定义: definitionId={}", definition.id());
    }

    @Override
    public void deleteById(BusinessDefinitionId id) {
        if (id == null) {
            return;
        }
        actionMapper.deleteByQuery(
                QueryWrapper.create().where(BUSINESS_ACTION_DO.DEFINITION_ID.eq(id.value()))
        );
        definitionMapper.deleteById(id.value());
        log.debug("根据ID删除业务定义: definitionId={}", id);
    }

    @Override
    public List<BusinessDefinition> loadAll() {
        List<BusinessDefinitionDO> definitionDOs = definitionMapper.selectAll();
        return definitionDOs.stream()
                .map(this::loadActions)
                .toList();
    }

    @Override
    public void streamByAppId(BusinessDefinitionId id, Consumer<AggregateRoot<BusinessDefinitionId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public Optional<BusinessDefinition> findByBusinessCode(BusinessCode businessCode) {
        if (businessCode == null) {
            return Optional.empty();
        }
        BusinessDefinitionDO definitionDO = definitionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(BUSINESS_DEFINITION_DO.BUSINESS_CODE.eq(businessCode.value()))
        );
        if (definitionDO == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadActions(definitionDO));
    }

    @Override
    public boolean existsByBusinessCode(BusinessCode businessCode) {
        if (businessCode == null) {
            return false;
        }
        return definitionMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(BUSINESS_DEFINITION_DO.BUSINESS_CODE.eq(businessCode.value()))
        ) > 0;
    }

    @Override
    public List<BusinessDefinition> findAll() {
        return loadAll();
    }

    @Override
    public List<BusinessDefinition> findByActive(boolean active) {
        List<BusinessDefinitionDO> definitionDOs = definitionMapper.selectListByQuery(
                QueryWrapper.create().where(BUSINESS_DEFINITION_DO.ACTIVE.eq(active))
        );
        return definitionDOs.stream()
                .map(this::loadActions)
                .toList();
    }

    /**
     * 加载业务动作子表并组装聚合根。
     */
    private BusinessDefinition loadActions(BusinessDefinitionDO definitionDO) {
        List<BusinessActionDO> actionDOs = actionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(BUSINESS_ACTION_DO.DEFINITION_ID.eq(definitionDO.getId()))
        );
        return converter.toDomain(definitionDO, actionDOs);
    }

    /**
     * 全量替换业务动作(先删后插)。
     */
    private void saveActions(BusinessDefinition definition) {
        Long definitionId = definition.id().value();
        Set<BusinessAction> currentActions = new HashSet<>(definition.supportedActions());
        // 收集当前数据库中的动作集合用于差异比较
        Set<String> existingActionNames = actionMapper.selectListByQuery(
                QueryWrapper.create().where(BUSINESS_ACTION_DO.DEFINITION_ID.eq(definitionId))
        ).stream().map(BusinessActionDO::getAction).collect(Collectors.toSet());
        Set<String> currentActionNames = currentActions.stream()
                .map(a -> a.action().name()).collect(Collectors.toSet());
        if (existingActionNames.equals(currentActionNames)) {
            return;
        }
        actionMapper.deleteByQuery(
                QueryWrapper.create().where(BUSINESS_ACTION_DO.DEFINITION_ID.eq(definitionId))
        );
        for (BusinessAction action : currentActions) {
            BusinessActionDO actionDO = converter.toActionDO(definition, action, definitionId);
            actionMapper.insert(actionDO);
        }
    }
}
