package com.pension.permission.infrastructure.role.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.domain.role.repository.RoleTemplateRepository;
import com.pension.permission.infrastructure.role.converter.RoleTemplateConverter;
import com.pension.permission.infrastructure.role.entity.RoleTemplateDO;
import com.pension.permission.infrastructure.role.mapper.RoleTemplateMapper;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.role.entity.table.RoleTemplateDOTableDef.ROLE_TEMPLATE_DO;

/**
 * 角色权限模板仓储实现.
 *
 * <p>负责 {@link RoleTemplate} 聚合根的持久化操作。领域事件不在 Repository 发布，
 * 由 {@code ApplicationService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RoleTemplateRepositoryImpl implements RoleTemplateRepository {

  private final RoleTemplateMapper roleTemplateMapper;
  private final RoleTemplateConverter converter;

  @Override
  public Optional<RoleTemplate> load(RoleTemplateId id) {
    if (id == null) {
      return Optional.empty();
    }
    RoleTemplateDO doObj = roleTemplateMapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(RoleTemplate roleTemplate) {
    if (roleTemplate == null) {
      throw new IllegalArgumentException("RoleTemplate 不能为空");
    }

    RoleTemplateDO doObj = converter.toDO(roleTemplate);
    RoleTemplateDO existing = roleTemplateMapper.selectOneById(doObj.getId());

    if (existing == null) {
      roleTemplateMapper.insert(doObj);
      log.debug("新增 RoleTemplate: templateId={}, roleCode={}", roleTemplate.id(), roleTemplate.roleCode());
    } else {
      doObj.setVersion(existing.getVersion());
      roleTemplateMapper.update(doObj);
      log.debug("更新 RoleTemplate: templateId={}, roleCode={}, version={}",
        roleTemplate.id(), roleTemplate.roleCode(), roleTemplate.version());
    }
  }

  @Override
  public void delete(RoleTemplate aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    roleTemplateMapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 RoleTemplate: templateId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(RoleTemplateId id) {
    if (id == null) {
      return;
    }
    roleTemplateMapper.deleteById(id.value());
    log.debug("根据 ID 删除 RoleTemplate: templateId={}", id);
  }

  @Override
  public List<RoleTemplate> loadAll() {
    List<RoleTemplateDO> doList = roleTemplateMapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(RoleTemplateId id, Consumer<AggregateRoot<RoleTemplateId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public List<RoleTemplate> findByRoleCode(RoleCode roleCode, RoleTemplateStatus status) {
    if (roleCode == null) {
      return List.of();
    }

    QueryWrapper query = QueryWrapper.create()
      .where(ROLE_TEMPLATE_DO.ROLE_CODE.eq(roleCode.value()))
      .and(ROLE_TEMPLATE_DO.DELETED.eq(false));

    if (status != null) {
      query.and(ROLE_TEMPLATE_DO.STATUS.eq(status.name()));
    }

    List<RoleTemplateDO> doList = roleTemplateMapper.selectListByQuery(query);
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public List<RoleCode> findAllRoleCodes() {
    List<RoleTemplateDO> doList = roleTemplateMapper.selectListByQuery(
      QueryWrapper.create()
        .where(ROLE_TEMPLATE_DO.DELETED.eq(false))
    );

    return doList.stream()
      .map(RoleTemplateDO::getRoleCode)
      .distinct()
      .map(RoleCode::new)
      .toList();
  }
}
