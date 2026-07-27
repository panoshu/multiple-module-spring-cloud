package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.domain.authorization.repository.RouteRuleRepository;
import com.example.iam.infrastructure.converter.RouteRuleConverter;
import com.example.iam.infrastructure.entity.RouteRuleDO;
import com.example.iam.infrastructure.mapper.RouteRuleMapper;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.iam.infrastructure.entity.table.RouteRuleDOTableDef.ROUTE_RULE_DO;

/**
 * 路由权限规则聚合根仓储实现。
 *
 * <p>负责 {@link RouteRule} 的持久化操作。
 * demo-gateway 启动时通过本仓储加载所有启用的路由规则用于请求路径匹配鉴权。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RouteRuleRepositoryImpl implements RouteRuleRepository {

    private final RouteRuleMapper ruleMapper;
    private final RouteRuleConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<RouteRule> load(RouteRuleId id) {
        if (id == null) {
            return Optional.empty();
        }
        RouteRuleDO ruleDO = ruleMapper.selectOneById(id.value());
        return Optional.ofNullable(converter.toDomain(ruleDO));
    }

    @Override
    public void save(RouteRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("路由规则不能为空");
        }
        RouteRuleDO ruleDO = converter.toDO(rule);
        boolean isInsert = ruleMapper.selectOneById(rule.id().value()) == null;
        if (isInsert) {
            ruleMapper.insert(ruleDO);
            log.debug("新增路由规则: ruleId={}, routePattern={}", rule.id(), rule.routePattern());
        } else {
            ruleMapper.update(ruleDO);
            log.debug("更新路由规则: ruleId={}, routePattern={}, version={}",
                    rule.id(), rule.routePattern(), rule.version());
        }
        eventPublisher.publishFor(rule);
    }

    @Override
    public void delete(RouteRule rule) {
        if (rule == null) {
            return;
        }
        RouteRuleDO ruleDO = ruleMapper.selectOneById(rule.id().value());
        if (ruleDO != null) {
            ruleMapper.delete(ruleDO);
        }
        log.debug("删除路由规则: ruleId={}", rule.id());
    }

    @Override
    public void deleteById(RouteRuleId id) {
        if (id == null) {
            return;
        }
        ruleMapper.deleteById(id.value());
        log.debug("根据ID删除路由规则: ruleId={}", id);
    }

    @Override
    public List<RouteRule> loadAll() {
        List<RouteRuleDO> ruleDOs = ruleMapper.selectAll();
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public void streamByAppId(RouteRuleId id, Consumer<AggregateRoot<RouteRuleId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public Optional<RouteRule> findByRoutePattern(String routePattern) {
        if (routePattern == null || routePattern.isBlank()) {
            return Optional.empty();
        }
        RouteRuleDO ruleDO = ruleMapper.selectOneByQuery(
                QueryWrapper.create().where(ROUTE_RULE_DO.ROUTE_PATTERN.eq(routePattern))
        );
        return Optional.ofNullable(converter.toDomain(ruleDO));
    }

    @Override
    public List<RouteRule> findAllEnabled() {
        List<RouteRuleDO> ruleDOs = ruleMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ROUTE_RULE_DO.ENABLED.eq(true))
                        .orderBy(ROUTE_RULE_DO.PRIORITY.desc())
        );
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public List<RouteRule> findAll() {
        List<RouteRuleDO> ruleDOs = ruleMapper.selectListByQuery(
                QueryWrapper.create()
                        .orderBy(ROUTE_RULE_DO.PRIORITY.desc())
        );
        return ruleDOs.stream()
                .map(converter::toDomain)
                .toList();
    }
}
