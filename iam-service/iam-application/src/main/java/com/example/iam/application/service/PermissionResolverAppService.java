package com.example.iam.application.service;

import com.example.iam.api.dto.PermissionSnapshotDTO;
import com.example.iam.api.query.ResolvePermissionsQuery;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.types.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限解析应用服务。
 *
 * <p>作为权限计算的调试与预览入口,对外暴露 {@link PermissionResolver} 能力。
 * 接收 {@link ResolvePermissionsQuery}(用户 ID + 计划编号),
 * 调用领域服务 {@link PermissionResolver#resolve} 计算权限快照,转换为 DTO 返回。
 *
 * <p>本服务不缓存计算结果,每次调用都重新计算;
 * 实际生产环境中的缓存策略由调用方(如 sa-token {@code IamStpInterfaceImpl} 或
 * 二次授权快照冻结)决定。
 *
 * <p>本服务仅编排业务流程,权限计算的业务规则
 * (规则加载、过滤、合并、覆盖、继承)由 {@link PermissionResolver} 实现负责。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionResolverAppService {

  private final PermissionResolver permissionResolver;

  /**
   * 解析指定用户在指定计划上下文下的权限快照。
   *
   * <p>流程:
   * <ol>
   *   <li>将命令中的 userId 转换为领域原语 {@link UserId}</li>
   *   <li>调用 {@link PermissionResolver#resolve} 计算权限快照</li>
   *   <li>将 {@link PermissionSnapshot} 转换为 {@link PermissionSnapshotDTO}</li>
   * </ol>
   *
   * @param query 权限解析查询
   * @return 权限快照 DTO
   */
  @Transactional(readOnly = true)
  public PermissionSnapshotDTO resolve(ResolvePermissionsQuery query) {
    UserId userId = UserId.of(query.userId());
    PermissionSnapshot snapshot = permissionResolver.resolve(userId, query.planId());

    log.info("权限解析成功: userId={}, planId={}, permissionCount={}",
        query.userId(), query.planId(),
        snapshot.permissions() != null ? snapshot.permissions().size() : 0);

    return toDTO(snapshot);
  }

  /**
   * 领域对象转 DTO。
   */
  private PermissionSnapshotDTO toDTO(PermissionSnapshot snapshot) {
    Set<String> permissionStrings = snapshot.permissions().stream()
        .map(PermissionCode::value)
        .collect(Collectors.toSet());
    return new PermissionSnapshotDTO(
        snapshot.userId().value(),
        snapshot.planId(),
        permissionStrings,
        snapshot.calculatedAt()
    );
  }
}
