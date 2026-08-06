package com.pension.permission.infrastructure.role.repository;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.repository.RoleVisibilityRepository;
import com.pension.permission.domain.role.valueobject.RoleVisibilityScope;
import com.pension.permission.infrastructure.role.converter.RoleVisibilityConverter;
import com.pension.permission.infrastructure.role.entity.RoleVisibilityDO;
import com.pension.permission.infrastructure.role.mapper.RoleVisibilityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.pension.permission.infrastructure.role.entity.table.RoleVisibilityDOTableDef.ROLE_VISIBILITY_DO;

/**
 * 角色可见性范围仓储实现.
 *
 * <p>负责 {@link RoleVisibilityScope} 值对象的持久化操作。该值为 record，不携带
 * id/createdBy/createdAt/version 等基类字段，故通用字段由本实现设置：</p>
 * <ul>
 *   <li>新建：createdBy/createTime/updatedBy/updateTime 使用系统用户与当前时间</li>
 *   <li>更新：updatedBy/updateTime 使用系统用户与当前时间，version 从已有记录复制</li>
 * </ul>
 *
 * <p>save 按 (dimension, scopeValue) 做 upsert，不发布领域事件
 * （值对象无领域事件）。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RoleVisibilityRepositoryImpl implements RoleVisibilityRepository {

  /**
   * 系统用户标识，用于值对象持久化时填充 createdBy/updatedBy 字段。
   */
  private static final String SYSTEM_USER = "system";

  private final RoleVisibilityMapper roleVisibilityMapper;
  private final RoleVisibilityConverter converter;

  @Override
  public Optional<RoleVisibilityScope> findByPlan(PlanNo planNo) {
    if (planNo == null) {
      return Optional.empty();
    }
    return findByScope(RoleTemplateScopeDimension.PLAN, planNo.value());
  }

  @Override
  public Optional<RoleVisibilityScope> findByCustomer(CustomerNo customerNo) {
    if (customerNo == null) {
      return Optional.empty();
    }
    return findByScope(RoleTemplateScopeDimension.CUSTOMER, customerNo.value());
  }

  @Override
  public void save(RoleVisibilityScope scope) {
    if (scope == null) {
      throw new IllegalArgumentException("RoleVisibilityScope 不能为空");
    }
    if (scope.dimension() == null) {
      throw new IllegalArgumentException("RoleVisibilityScope.dimension 不能为空");
    }

    RoleVisibilityDO doObj = converter.toDO(scope);
    LocalDateTime now = LocalDateTime.now();

    RoleVisibilityDO existing = roleVisibilityMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(ROLE_VISIBILITY_DO.DIMENSION.eq(doObj.getDimension()))
        .and(ROLE_VISIBILITY_DO.SCOPE_VALUE.eq(doObj.getScopeValue()))
        .and(ROLE_VISIBILITY_DO.DELETED.eq(false))
    );

    if (existing == null) {
      doObj.setCreatedBy(SYSTEM_USER);
      doObj.setCreateTime(now);
      doObj.setUpdatedBy(SYSTEM_USER);
      doObj.setUpdateTime(now);
      doObj.setDeleted(false);
      doObj.setVersion(0);
      roleVisibilityMapper.insert(doObj);
      log.debug("新增 RoleVisibilityScope: dimension={}, scopeValue={}, mode={}",
        doObj.getDimension(), doObj.getScopeValue(), doObj.getMode());
    } else {
      doObj.setId(existing.getId());
      doObj.setCreatedBy(existing.getCreatedBy());
      doObj.setCreateTime(existing.getCreateTime());
      doObj.setUpdatedBy(SYSTEM_USER);
      doObj.setUpdateTime(now);
      doObj.setDeleted(false);
      doObj.setVersion(existing.getVersion());
      roleVisibilityMapper.update(doObj);
      log.debug("更新 RoleVisibilityScope: id={}, dimension={}, scopeValue={}, mode={}",
        existing.getId(), doObj.getDimension(), doObj.getScopeValue(), doObj.getMode());
    }
  }

  // ========== 私有方法 ==========

  private Optional<RoleVisibilityScope> findByScope(RoleTemplateScopeDimension dimension, String scopeValue) {
    RoleVisibilityDO doObj = roleVisibilityMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(ROLE_VISIBILITY_DO.DIMENSION.eq(dimension.name()))
        .and(ROLE_VISIBILITY_DO.SCOPE_VALUE.eq(scopeValue))
        .and(ROLE_VISIBILITY_DO.DELETED.eq(false))
    );
    return Optional.ofNullable(converter.toDomain(doObj));
  }
}
