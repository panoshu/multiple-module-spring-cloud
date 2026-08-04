package com.example.shared.domain.aggregate.root;

import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.contract.Identifier;
import com.example.shared.identifier.id.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 泛型聚合根接口
 *
 * @author <a href="mailto: me@panoshu.top">panoshu</a>
 * @version 1.0
 * @since 2025/6/1 14:17
 **/

public abstract class AggregateRoot<ID extends Identifier<?>> extends Entity<ID> {

  private final transient List<DomainEvent> domainEvents = new ArrayList<>();

  // 场景1: 业务创建
  protected AggregateRoot(ID id, UserNo userNo) {
    super(id, userNo);
  }

  // 场景2: 重建 - [新增]
  protected AggregateRoot(ID id, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 注册一个领域事件。
   * 聚合根在业务操作中生成事件，然后通过此方法注册。
   */
  protected void registerDomainEvent(DomainEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("Domain event cannot be null.");
    }
    this.domainEvents.add(event);
  }

  /**
   * 获取所有已注册的领域事件。
   * 通常在聚合根保存后被清理。
   */
  public List<DomainEvent> domainEvents() {
    return Collections.unmodifiableList(domainEvents);
  }

  /**
   * 清理所有已注册的领域事件。
   * 通常在领域事件发布后调用。
   */
  public void clearDomainEvents() {
    this.domainEvents.clear();
  }
}
