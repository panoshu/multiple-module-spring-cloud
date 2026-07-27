package com.example.iam.application.service;

import com.example.iam.api.dto.PermissionSnapshotDTO;
import com.example.iam.api.query.ResolvePermissionsQuery;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.types.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionResolverAppService} 单元测试。
 *
 * <p>本服务为简单委托类,仅做参数转换 + 调用领域服务 + DTO 转换,
 * 测试聚焦于 happy path 与权限码集合映射。
 *
 * @author iam-service
 */
@DisplayName("权限解析应用服务测试")
@ExtendWith(MockitoExtension.class)
class PermissionResolverAppServiceTest {

  private static final Long USER_ID = 1001L;
  private static final String PLAN_ID = "PLAN001";

  @Mock private PermissionResolver permissionResolver;

  @InjectMocks
  private PermissionResolverAppService permissionResolverAppService;

  @Test
  @DisplayName("解析权限返回权限码字符串集合")
  void should_resolve_permissions_to_string_set() {
    PermissionSnapshot snapshot = new PermissionSnapshot(
        UserId.of(USER_ID), PLAN_ID,
        Set.of(
            PermissionCode.of("BIZ_A.HANDLE"),
            PermissionCode.of("BIZ_B.QUERY")),
        LocalDateTime.now());
    when(permissionResolver.resolve(UserId.of(USER_ID), PLAN_ID))
        .thenReturn(snapshot);

    PermissionSnapshotDTO dto = permissionResolverAppService.resolve(
        new ResolvePermissionsQuery(USER_ID, PLAN_ID));

    assertThat(dto.userId()).isEqualTo(USER_ID);
    assertThat(dto.planId()).isEqualTo(PLAN_ID);
    assertThat(dto.permissions()).containsExactlyInAnyOrder("BIZ_A.HANDLE", "BIZ_B.QUERY");
  }

  @Test
  @DisplayName("空权限快照返回空集合")
  void should_return_empty_set_when_no_permissions() {
    PermissionSnapshot snapshot = new PermissionSnapshot(
        UserId.of(USER_ID), PLAN_ID, Set.of(), LocalDateTime.now());
    when(permissionResolver.resolve(UserId.of(USER_ID), PLAN_ID))
        .thenReturn(snapshot);

    PermissionSnapshotDTO dto = permissionResolverAppService.resolve(
        new ResolvePermissionsQuery(USER_ID, PLAN_ID));

    assertThat(dto.permissions()).isEmpty();
  }
}
