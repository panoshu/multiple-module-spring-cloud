package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 权限快照 - PermissionResolver 计算结果,冻结某用户在某计划上下文下的权限集合。
 *
 * <p>设计文档 3.7 节:网点渠道在二次授权瞬间调用 {@code resolve} 冻结快照;
 * 其他渠道在 {@code IamStpInterfaceImpl.getPermissionList} 缓存未命中时调用。
 *
 * <p>字段说明:
 * <ul>
 *   <li>{@code userId} - 用户标识(本服务内 ID 类型)</li>
 *   <li>{@code planId} - 计划编号(字符串,来自外部系统,iam-service 不定义 PlanId 类型)</li>
 *   <li>{@code permissions} - 权限码集合(不可变,如 {"business1.handle", "business2.query"})</li>
 *   <li>{@code calculatedAt} - 计算时间戳</li>
 * </ul>
 *
 * <p>不变量:所有字段非 null;permissions 通过 {@link Set#copyOf} 包装为不可变集合。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionSnapshot(
    UserId userId,
    String planId,
    Set<PermissionCode> permissions,
    LocalDateTime calculatedAt
) implements ValueObject {

  public PermissionSnapshot {
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(planId, "planId cannot be null");
    Objects.requireNonNull(permissions, "permissions cannot be null");
    Objects.requireNonNull(calculatedAt, "calculatedAt cannot be null");
    permissions = Set.copyOf(permissions);
  }
}
