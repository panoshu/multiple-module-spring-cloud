package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.event.GrantApproved;
import com.pension.permission.domain.authorization.event.GrantRejected;
import com.pension.permission.domain.authorization.event.GrantRevoked;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.valueobject.subject.CapabilitySubject;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanAllMembersSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanRoleSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import com.pension.permission.types.GrantId;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link GrantEventRefreshListener} 单元测试.
 *
 * <p>验证 Grant 事件触发权限缓存刷新的契约：</p>
 * <ul>
 *   <li>GrantApproved / GrantRevoked → 通过 grantId 加载 Grant，从 subject 提取受影响账号，调用 evictAll</li>
 *   <li>GrantRejected → 不触发缓存刷新</li>
 *   <li>UserListSubject → 精确失效指定账号集合</li>
 *   <li>CapabilitySubject / PlanAllMembersSubject / PlanRoleSubject → 暂依赖 TTL 兜底，仅 warn log</li>
 *   <li>Grant 不存在 → 不抛异常，仅 warn log</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrantEventRefreshListener")
class GrantEventRefreshListenerTest {

  @Mock private PermissionCacheStore permissionCacheStore;
  @Mock private GrantRepository grantRepository;

  @InjectMocks
  private GrantEventRefreshListener listener;

  private static final GrantId GRANT_ID = new GrantId("grant-1");
  private static final UserNo APPROVER = UserNo.of("approver-1");

  @Test
  @DisplayName("GrantApproved + UserListSubject - 调用 evictAll 失效对应账号缓存")
  void onGrantApproved_userListSubject_shouldEvictAll() {
    UserNo user1 = UserNo.of("user-1");
    UserNo user2 = UserNo.of("user-2");
    Set<UserNo> accountIds = Set.of(user1, user2);
    GrantSubject subject = new UserListSubject(accountIds);
    Grant grant = mock(Grant.class);
    when(grant.subject()).thenReturn(subject);
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.of(grant));

    listener.onGrantApproved(GrantApproved.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore).evictAll(accountIds);
  }

  @Test
  @DisplayName("GrantRevoked + UserListSubject - 调用 evictAll 失效对应账号缓存")
  void onGrantRevoked_userListSubject_shouldEvictAll() {
    UserNo user1 = UserNo.of("user-1");
    Set<UserNo> accountIds = Set.of(user1);
    GrantSubject subject = new UserListSubject(accountIds);
    Grant grant = mock(Grant.class);
    when(grant.subject()).thenReturn(subject);
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.of(grant));

    listener.onGrantRevoked(GrantRevoked.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore).evictAll(accountIds);
  }

  @Test
  @DisplayName("GrantRejected - 不触发任何缓存失效")
  void onGrantRejected_shouldNotEvict() {
    listener.onGrantRejected(GrantRejected.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore, never()).evictAll(any());
    verify(permissionCacheStore, never()).evict(any());
    verify(grantRepository, never()).load(any(GrantId.class));
  }

  @Test
  @DisplayName("GrantApproved + CapabilitySubject - 不调用 evictAll，依赖 TTL 兜底")
  void onGrantApproved_capabilitySubject_shouldRelyOnTtl() {
    GrantSubject subject = new CapabilitySubject();
    Grant grant = mock(Grant.class);
    when(grant.subject()).thenReturn(subject);
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.of(grant));

    listener.onGrantApproved(GrantApproved.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore, never()).evictAll(any());
    verify(permissionCacheStore, never()).evict(any());
  }

  @Test
  @DisplayName("GrantApproved + PlanAllMembersSubject - 不调用 evictAll，依赖 TTL 兜底")
  void onGrantApproved_planAllMembersSubject_shouldRelyOnTtl() {
    GrantSubject subject = new PlanAllMembersSubject(new PlanNo("plan-1"));
    Grant grant = mock(Grant.class);
    when(grant.subject()).thenReturn(subject);
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.of(grant));

    listener.onGrantApproved(GrantApproved.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore, never()).evictAll(any());
    verify(permissionCacheStore, never()).evict(any());
  }

  @Test
  @DisplayName("GrantApproved + PlanRoleSubject - 不调用 evictAll，依赖 TTL 兜底")
  void onGrantApproved_planRoleSubject_shouldRelyOnTtl() {
    GrantSubject subject = new PlanRoleSubject(new PlanNo("plan-1"), new RoleCode("manager"));
    Grant grant = mock(Grant.class);
    when(grant.subject()).thenReturn(subject);
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.of(grant));

    listener.onGrantApproved(GrantApproved.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore, never()).evictAll(any());
    verify(permissionCacheStore, never()).evict(any());
  }

  @Test
  @DisplayName("GrantApproved + Grant 不存在 - 不抛异常，不调用 evict")
  void onGrantApproved_grantNotFound_shouldNotThrow() {
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.empty());

    listener.onGrantApproved(GrantApproved.of(GRANT_ID, APPROVER));

    verify(permissionCacheStore, never()).evictAll(any());
    verify(permissionCacheStore, never()).evict(any());
  }

  @Test
  @DisplayName("UserListSubject 空账号集合 - 调用 evictAll 但传入空集合")
  void onGrantApproved_emptyUserList_shouldEvictAllWithEmptySet() {
    Set<UserNo> emptyAccountIds = Set.of();
    GrantSubject subject = new UserListSubject(emptyAccountIds);
    Grant grant = mock(Grant.class);
    when(grant.subject()).thenReturn(subject);
    when(grantRepository.load(GRANT_ID)).thenReturn(Optional.of(grant));

    listener.onGrantApproved(GrantApproved.of(GRANT_ID, APPROVER));

    // 空集合仍然调用 evictAll，由 PermissionCacheStore 实现决定是否跳过
    verify(permissionCacheStore).evictAll(emptyAccountIds);
  }
}
