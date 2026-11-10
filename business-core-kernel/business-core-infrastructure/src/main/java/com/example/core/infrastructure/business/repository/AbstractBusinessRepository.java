package com.example.core.infrastructure.business.repository;

import com.example.core.infrastructure.engine.errorcode.CoreInfraErrorCode;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventBus;
import com.example.shared.domain.repository.Repository;
import com.example.shared.exception.SystemException;
import com.example.shared.identifier.contract.Identifier;
import com.mybatisflex.core.BaseMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 业务聚合根仓储抽象基类
 * <p>
 * 封装基于 MyBatis-Flex 的通用 CRUD、乐观锁更新与领域事件发布逻辑，
 * 子类只需提供 {@link BaseMapper}、DO↔领域对象转换与 ID 提取策略即可。
 * <p>
 * <b>时间戳管理：</b>{@code createTime}/{@code updateTime} 由 Converter 从领域对象的
 * {@code createdAt()}/{@code updatedAt()} 映射，不依赖 ORM 自动填充。
 *
 * @param <T>  聚合根类型
 * @param <ID> 聚合根 ID 类型
 * @param <D>  DO 类型
 * @author core-kernel
 * @since 2026/8/8
 */
@Slf4j
public abstract class AbstractBusinessRepository<
  T extends AggregateRoot<ID>,
  ID extends Identifier<?>,
  D> implements Repository<T, ID> {

  private final EventBus eventBus;

  protected AbstractBusinessRepository(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  /**
   * 子类提供对应的 MyBatis-Flex Mapper。
   */
  protected abstract BaseMapper<D> mapper();

  /**
   * 领域对象 → DO。
   */
  protected abstract D toDO(T aggregate);

  /**
   * DO → 领域对象。
   */
  protected abstract T toDomain(D aDo);

  /**
   * 从 DO 提取主键值（用于存在性判断与按 ID 查询）。
   */
  protected abstract Object doId(D aDo);

  /**
   * 读取 DO 的乐观锁版本。
   */
  protected abstract Integer doVersion(D aDo);

  /**
   * 设置 DO 的乐观锁版本，更新时必须使用数据库当前版本。
   */
  protected abstract void doVersion(D aDo, Integer version);

  @Override
  public Optional<T> load(ID id) {
    if (id == null) {
      return Optional.empty();
    }
    D aDo = mapper().selectOneById((Serializable) id.value());
    return Optional.ofNullable(aDo).map(this::toDomain);
  }

  @Override
  public void save(T aggregate) {
    if (aggregate == null) {
      throw new IllegalArgumentException("聚合根不能为空");
    }
    D aDo = toDO(aggregate);
    D existing = mapper().selectOneById((Serializable) doId(aDo));
    if (existing == null) {
      mapper().insert(aDo);
      log.debug("新增聚合根: id={}", aggregate.id());
    } else {
      updateExisting(aggregate, aDo, existing);
      log.debug("更新聚合根: id={}, version={}", aggregate.id(), aggregate.version());
    }
    publishDomainEvents(aggregate);
  }

  private void updateExisting(T aggregate, D aDo, D existing) {
    Integer persistedVersion = doVersion(existing);
    if (persistedVersion == null) {
      throw optimisticLockFailure(aggregate);
    }

    long domainVersion = aggregate.version().value();
    if (domainVersion != persistedVersion && domainVersion != persistedVersion + 1L) {
      throw optimisticLockFailure(aggregate);
    }

    // MyBatis-Flex 使用 DO 版本作为 WHERE 条件并执行 version + 1。
    // markUpdated 已提前递增领域版本时，DO 必须回退为数据库当前版本。
    doVersion(aDo, persistedVersion);
    if (mapper().update(aDo) != 1) {
      throw optimisticLockFailure(aggregate);
    }
    if (domainVersion == persistedVersion) {
      aggregate.syncVersionAfterUpdate();
    }
  }

  private SystemException optimisticLockFailure(T aggregate) {
    return new SystemException(CoreInfraErrorCode.OPTIMISTIC_LOCK_FAILURE)
      .withLogDetail("聚合保存发生版本冲突: id=" + aggregate.id());
  }

  @Override
  public void delete(T aggregate) {
    if (aggregate == null) {
      return;
    }
    mapper().deleteById((Serializable) aggregate.id().value());
    log.debug("删除聚合根: id={}", aggregate.id());
  }

  @Override
  public void deleteById(ID id) {
    if (id == null) {
      return;
    }
    mapper().deleteById((Serializable) id.value());
    log.debug("根据 ID 删除聚合根: id={}", id);
  }

  @Override
  public List<T> loadAll() {
    return mapper().selectAll().stream()
      .map(this::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(ID id, Consumer<AggregateRoot<ID>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  /**
   * 发布聚合根内部注册的领域事件并清空事件队列（spec §13.2）。
   * <p>
   * 统一通过 {@link EventBus} 发布，由 shared-event 负责 Outbox 落库、本地分发、
   * 远程投递与恢复；应用服务不再重复 publish，也不提前清空队列。
   * 单条事件发布失败不影响其他事件，仅记录错误日志。
   */
  protected void publishDomainEvents(T aggregate) {
    List<DomainEvent> events = aggregate.domainEvents();
    if (events.isEmpty()) {
      return;
    }
    for (DomainEvent event : events) {
      try {
        eventBus.publish(event);
        log.debug("发布领域事件: eventId={}, type={}",
          event.eventId(), event.getClass().getSimpleName());
      } catch (Exception e) {
        log.error("发布领域事件失败: eventId={}, type={}",
          event.eventId(), event.getClass().getSimpleName(), e);
      }
    }
    aggregate.clearDomainEvents();
  }
}
