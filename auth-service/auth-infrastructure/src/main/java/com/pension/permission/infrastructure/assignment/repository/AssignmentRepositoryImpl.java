package com.pension.permission.infrastructure.assignment.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.enumeration.AssignmentStatus;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.infrastructure.assignment.converter.AssignmentConverter;
import com.pension.permission.infrastructure.assignment.entity.AssignmentDO;
import com.pension.permission.infrastructure.assignment.mapper.AssignmentMapper;
import com.pension.permission.types.AssignmentId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.assignment.entity.table.AssignmentDOTableDef.ASSIGNMENT_DO;

/**
 * 账号身份分配仓储实现.
 *
 * <p>负责 {@link AgentIdentityAssignment} 聚合根的持久化操作。领域事件不在 Repository 发布，
 * 由 {@code ApplicationService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AssignmentRepositoryImpl implements AssignmentRepository {

  private final AssignmentMapper assignmentMapper;
  private final AssignmentConverter converter;

  @Override
  public Optional<AgentIdentityAssignment> load(AssignmentId id) {
    if (id == null) {
      return Optional.empty();
    }
    AssignmentDO doObj = assignmentMapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(AgentIdentityAssignment assignment) {
    if (assignment == null) {
      throw new IllegalArgumentException("Assignment 不能为空");
    }

    AssignmentDO doObj = converter.toDO(assignment);
    AssignmentDO existing = assignmentMapper.selectOneById(doObj.getId());

    if (existing == null) {
      assignmentMapper.insert(doObj);
      log.debug("新增 Assignment: assignmentId={}", assignment.id());
    } else {
      doObj.setVersion(existing.getVersion());
      assignmentMapper.update(doObj);
      log.debug("更新 Assignment: assignmentId={}, version={}", assignment.id(), assignment.version());
    }
  }

  @Override
  public void delete(AgentIdentityAssignment aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    assignmentMapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 Assignment: assignmentId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(AssignmentId id) {
    if (id == null) {
      return;
    }
    assignmentMapper.deleteById(id.value());
    log.debug("根据 ID 删除 Assignment: assignmentId={}", id);
  }

  @Override
  public List<AgentIdentityAssignment> loadAll() {
    List<AssignmentDO> doList = assignmentMapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(AssignmentId id, Consumer<AggregateRoot<AssignmentId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public List<AgentIdentityAssignment> findActiveByAccount(UserNo accountId) {
    if (accountId == null) {
      return List.of();
    }

    List<AssignmentDO> doList = assignmentMapper.selectListByQuery(
      QueryWrapper.create()
        .where(ASSIGNMENT_DO.USER_NO.eq(accountId.value()))
        .and(ASSIGNMENT_DO.STATUS.eq(AssignmentStatus.ACTIVE.name()))
        .and(ASSIGNMENT_DO.DELETED.eq(false))
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public List<AgentIdentityAssignment> findAllActive() {
    List<AssignmentDO> doList = assignmentMapper.selectListByQuery(
      QueryWrapper.create()
        .where(ASSIGNMENT_DO.STATUS.eq(AssignmentStatus.ACTIVE.name()))
        .and(ASSIGNMENT_DO.DELETED.eq(false))
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }
}
