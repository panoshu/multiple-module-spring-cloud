package com.pension.permission.infrastructure.authorization.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.infrastructure.authorization.converter.GrantConverter;
import com.pension.permission.infrastructure.authorization.entity.GrantDO;
import com.pension.permission.infrastructure.authorization.mapper.GrantMapper;
import com.pension.permission.types.GrantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.authorization.entity.table.GrantDOTableDef.GRANT_DO;

/**
 * 授权策略主记录仓储实现.
 *
 * <p>负责 {@link Grant} 聚合根的持久化操作。</p>
 *
 * <h3>查询策略</h3>
 * <ul>
 *   <li>{@link #findActiveCapabilityGrants(LocalDateTime)}：查询 {@code grantType=BASE} 且
 *       {@code subject LIKE "%Capability%"} 的记录，CapabilitySubject 是能力层的标记</li>
 *   <li>{@link #findCandidateSubjectGrants(UserNo, LocalDateTime)}：查询可能覆盖该身份的 Grant，
 *       包含三类主体：
 *       <ol>
 *         <li>UserListSubject：按用户列表匹配（DO 字段 subject LIKE "%UserList%" 粗筛 + 应用层精筛）</li>
 *         <li>PlanAllMembersSubject：按计划全体成员匹配</li>
 *         <li>PlanRoleSubject：按计划+角色匹配</li>
 *       </ol>
 *       当前实现采用"宽进严出"策略：DO 层先按状态/时间过滤，subject 的精确匹配由调用方在内存中完成</li>
 * </ul>
 *
 * <p>领域事件不在 Repository 发布，由 {@code ApplicationService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GrantRepositoryImpl implements GrantRepository {

  /**
   * 能力层 subject JSON 中包含的类型标识，用于 LIKE 粗筛.
   */
  private static final String SUBJECT_TYPE_CAPABILITY = "\"type\":\"Capability\"";
  private static final String SUBJECT_TYPE_USER_LIST = "\"type\":\"UserList\"";

  private final GrantMapper grantMapper;
  private final GrantConverter converter;

  @Override
  public Optional<Grant> load(GrantId id) {
    if (id == null) {
      return Optional.empty();
    }
    GrantDO doObj = grantMapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(Grant grant) {
    if (grant == null) {
      throw new IllegalArgumentException("Grant 不能为空");
    }

    GrantDO doObj = converter.toDO(grant);
    GrantDO existing = grantMapper.selectOneById(doObj.getId());

    if (existing == null) {
      grantMapper.insert(doObj);
      log.debug("新增 Grant: grantId={}, status={}", grant.id(), grant.status());
    } else {
      doObj.setVersion(existing.getVersion());
      grantMapper.update(doObj);
      log.debug("更新 Grant: grantId={}, status={}, version={}",
        grant.id(), grant.status(), grant.version());
    }
  }

  @Override
  public void delete(Grant aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    grantMapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 Grant: grantId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(GrantId id) {
    if (id == null) {
      return;
    }
    grantMapper.deleteById(id.value());
    log.debug("根据 ID 删除 Grant: grantId={}", id);
  }

  @Override
  public List<Grant> loadAll() {
    List<GrantDO> doList = grantMapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(GrantId id, Consumer<AggregateRoot<GrantId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public List<Grant> findActiveCapabilityGrants(LocalDateTime at) {
    // 能力层 Grant 特征：
    // 1. grantType = BASE
    // 2. subject JSON 中包含 "type":"Capability"
    // 3. status = EFFECTIVE
    // 4. 时间窗口有效（validity_start <= at AND (validity_end IS NULL OR validity_end > at)）
    QueryWrapper query = QueryWrapper.create()
      .where(GRANT_DO.GRANT_TYPE.eq(GrantType.BASE.name()))
      .and(GRANT_DO.STATUS.eq(GrantStatus.EFFECTIVE.name()))
      .and(GRANT_DO.SUBJECT.like(SUBJECT_TYPE_CAPABILITY))
      .and(GRANT_DO.DELETED.eq(false));

    addTimeWindowFilter(query, at);

    List<GrantDO> doList = grantMapper.selectListByQuery(query);
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public List<Grant> findCandidateSubjectGrants(UserNo identity, LocalDateTime at) {
    // 候选 Grant 集合：非 Capability 类型，且可能覆盖该身份的 Grant。
    // 由于 subject 是 JSON，无法用 SQL 精确匹配 UserList/PlanAllMembers/PlanRole，
    // 采用"宽进"策略：先查出所有 subject LIKE "%UserList%" OR subject LIKE "%PlanAllMembers%"
    // OR subject LIKE "%PlanRole%" 的 EFFECTIVE Grant，subject 的精确匹配交给调用方在内存中完成
    QueryWrapper query = QueryWrapper.create()
      .where(GRANT_DO.STATUS.eq(GrantStatus.EFFECTIVE.name()))
      .and(GRANT_DO.DELETED.eq(false))
      .and(GRANT_DO.SUBJECT.like(SUBJECT_TYPE_USER_LIST)
        .or(GRANT_DO.SUBJECT.like("\"type\":\"PlanAllMembers\""))
        .or(GRANT_DO.SUBJECT.like("\"type\":\"PlanRole\"")));

    addTimeWindowFilter(query, at);

    List<GrantDO> doList = grantMapper.selectListByQuery(query);
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  /**
   * 添加时间窗口过滤条件.
   *
   * <p>Grant 有效期判断：validityStart &lt;= at AND (validityEnd IS NULL OR validityEnd &gt; at)</p>
   */
  private void addTimeWindowFilter(QueryWrapper query, LocalDateTime at) {
    if (at == null) {
      return;
    }
    // (validity_start IS NULL OR validity_start <= at)
    query.and(GRANT_DO.VALIDITY_START.isNull().or(GRANT_DO.VALIDITY_START.le(at)));
    // (validity_end IS NULL OR validity_end > at)
    query.and(GRANT_DO.VALIDITY_END.isNull().or(GRANT_DO.VALIDITY_END.gt(at)));
  }
}
