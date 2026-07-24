package com.example.shared.domain.aggregate.entity;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.Identifier;
import com.example.shared.primitives.identity.UserNo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类
 *
 * @author <a href="mailto: me@panoshu.top">panoshu</a>
 * @version 1.0
 * @since 2025/6/1 14:15
 **/

public abstract class Entity<ID extends Identifier<?>> implements Serializable {
  private final ID id;
  private final UserNo createdBy;
  private final LocalDateTime createdAt; // 创建时间
  private UserNo updatedBy;
  private LocalDateTime updatedAt; // 更新时间
  private Version version;      // 乐观锁版本号

  // 场景1: 业务创建新对象 (New)
  protected Entity(ID id, UserNo createdBy) {
    LocalDateTime now = LocalDateTime.now();
    this(id, createdBy, createdBy, now, now, Version.initial());
  }

  // 场景2: 从数据库重建对象 (Reconstitute) - [新增]
  protected Entity(ID id, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    if (id == null) {
      throw new IllegalArgumentException("Entity ID cannot be null.");
    }
    this.id = id;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
    // 注意：不在此处调用 validateInvariants()。
    // Java 继承机制下，父类构造函数执行时子类字段尚未初始化，
    // 此时调用可被重写的 validateInvariants() 会导致子类校验必然失败（字段全为 null）。
    // 子类必须在自己的构造函数末尾显式调用 this.validateInvariants()。
  }

  // 通常在更新操作时调用，更新updatedAt并增加版本
  protected void markUpdated(UserNo operator) {
    if (operator == null) {
      throw new IllegalArgumentException("Operator (UserNo) cannot be null for update operation.");
    }
    this.updatedBy = operator;
    this.updatedAt = LocalDateTime.now();
    this.version = this.version.next();
    this.validateInvariants();
  }

  // 实体不变性校验方法 (抽象方法，强制子类实现)
  protected abstract void validateInvariants();

  public ID id() {
    return this.id;
  }

  public UserNo createdBy() {
    return this.createdBy;
  }

  public UserNo updatedBy() {
    return this.updatedBy;
  }

  public LocalDateTime createdAt() {
    return this.createdAt;
  }

  public LocalDateTime updatedAt() {
    return this.updatedAt;
  }

  public Version version() {
    return this.version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    // 必须校验具体类型：不同类型的实体即使 ID 相同也不应相等，
    // 否则 OrderEntity(id=1) 与 ProductEntity(id=1) 会被误判为相等
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Entity<?> that = (Entity<?>) o;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    // id 为 null 时（构造函数已拦截，此处为防御性编程）回退到对象身份哈希，
    // 避免所有 null-id 实体共享同一 hashCode 导致哈希表退化
    return id != null ? id.hashCode() : System.identityHashCode(this);
  }
}
