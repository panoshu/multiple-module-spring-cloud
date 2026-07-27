package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.types.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IamStpInterfaceImpl sa-token 权限/角色查询实现测试。
 *
 * <p>覆盖核心可观察行为:
 * <ul>
 *   <li>getRoleList 按 loginType 返回固定角色(internet/hq/branch/未知)</li>
 *   <li>getPermissionList 各分支:未登录、未选计划、网点快照、其他渠道缓存命中/未命中</li>
 *   <li>PermissionResolver 异常时返回空权限(安全降级)</li>
 *   <li>缓存值既支持 Set 也支持 List(Redis 序列化兼容)</li>
 * </ul>
 *
 * <p>通过替换 {@link StpInternetUtil#stpLogic}/{@link StpHqUtil#stpLogic}/
 * {@link StpBranchUtil#stpLogic} 公共静态字段实现隔离,测试后恢复。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IamStpInterfaceImpl sa-token 权限查询")
class IamStpInterfaceImplTest {

  private static final String PLAN_NO = "PLAN001";
  private static final Long USER_ID = 1001L;
  private static final String TOKEN = "token-xxx";

  @Mock
  private PermissionResolver permissionResolver;

  private IamStpInterfaceImpl stpInterface;

  private StpLogic originalInternet;
  private StpLogic originalHq;
  private StpLogic originalBranch;
  private StpLogic mockInternet;
  private StpLogic mockHq;
  private StpLogic mockBranch;

  @BeforeEach
  void setUp() {
    originalInternet = StpInternetUtil.stpLogic;
    originalHq = StpHqUtil.stpLogic;
    originalBranch = StpBranchUtil.stpLogic;
    mockInternet = org.mockito.Mockito.mock(StpLogic.class);
    mockHq = org.mockito.Mockito.mock(StpLogic.class);
    mockBranch = org.mockito.Mockito.mock(StpLogic.class);
    StpInternetUtil.stpLogic = mockInternet;
    StpHqUtil.stpLogic = mockHq;
    StpBranchUtil.stpLogic = mockBranch;

    stpInterface = new IamStpInterfaceImpl(permissionResolver);
  }

  @AfterEach
  void tearDown() {
    StpInternetUtil.stpLogic = originalInternet;
    StpHqUtil.stpLogic = originalHq;
    StpBranchUtil.stpLogic = originalBranch;
  }

  /**
   * 配置 internet 渠道 mock:loginId 对应 token,token 对应 session。
   */
  private void stubInternetSession(Object loginId, SaSession session) {
    when(mockInternet.getTokenValueByLoginId(loginId)).thenReturn(TOKEN);
    when(mockInternet.getTokenSessionByToken(TOKEN)).thenReturn(session);
  }

  private PermissionSnapshot snapshotWith(String... codes) {
    Set<PermissionCode> permissions = java.util.Arrays.stream(codes)
        .map(PermissionCode::of)
        .collect(java.util.stream.Collectors.toSet());
    return new PermissionSnapshot(UserId.of(USER_ID), PLAN_NO, permissions,
        LocalDateTime.now());
  }

  @Nested
  @DisplayName("getRoleList")
  class GetRoleList {

    @Test
    @DisplayName("internet 渠道返回 operator")
    void internetChannel_returnsOperator() {
      assertThat(stpInterface.getRoleList(USER_ID, "internet"))
          .containsExactly("operator");
    }

    @Test
    @DisplayName("hq 渠道返回 staff")
    void hqChannel_returnsStaff() {
      assertThat(stpInterface.getRoleList(USER_ID, "hq"))
          .containsExactly("staff");
    }

    @Test
    @DisplayName("branch 渠道返回 teller")
    void branchChannel_returnsTeller() {
      assertThat(stpInterface.getRoleList(USER_ID, "branch"))
          .containsExactly("teller");
    }

    @Test
    @DisplayName("未知渠道返回空列表")
    void unknownChannel_returnsEmpty() {
      assertThat(stpInterface.getRoleList(USER_ID, "unknown"))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("getPermissionList")
  class GetPermissionList {

    @Test
    @DisplayName("未知渠道返回空列表")
    void unknownChannel_returnsEmpty() {
      assertThat(stpInterface.getPermissionList(USER_ID, "unknown"))
          .isEmpty();
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("internet 渠道未登录(token 为 null)返回空列表")
    void internetChannel_notLoggedIn_returnsEmpty() {
      when(mockInternet.getTokenValueByLoginId(USER_ID)).thenReturn(null);

      assertThat(stpInterface.getPermissionList(USER_ID, "internet"))
          .isEmpty();
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("internet 渠道已登录但未选择计划返回空列表")
    void internetChannel_noPlanSelected_returnsEmpty() {
      SaSession session = new SaSession("session-1");
      stubInternetSession(USER_ID, session);

      assertThat(stpInterface.getPermissionList(USER_ID, "internet"))
          .isEmpty();
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("internet 渠道计划为空白字符串时返回空列表")
    void internetChannel_blankPlanId_returnsEmpty() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, "  ");
      stubInternetSession(USER_ID, session);

      assertThat(stpInterface.getPermissionList(USER_ID, "internet"))
          .isEmpty();
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("internet 渠道缓存未命中时调用 PermissionResolver 并写入缓存")
    void internetChannel_cacheMiss_callsResolverAndCaches() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      stubInternetSession(USER_ID, session);
      when(permissionResolver.resolve(UserId.of(USER_ID), PLAN_NO))
          .thenReturn(snapshotWith("business1.handle", "business2.query"));

      List<String> firstCall = stpInterface.getPermissionList(USER_ID, "internet");

      assertThat(firstCall)
          .containsExactlyInAnyOrder("business1.handle", "business2.query");
      // 缓存已写入 session
      Object cached = session.get(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS);
      assertThat(cached).isInstanceOf(java.util.Set.class);
      assertThat(((java.util.Set<?>) cached).toArray())
          .containsExactlyInAnyOrder("business1.handle", "business2.query");
    }

    @Test
    @DisplayName("internet 渠道缓存命中时不再调用 PermissionResolver")
    void internetChannel_cacheHit_doesNotCallResolver() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS,
          new java.util.HashSet<>(Set.of("cached.perm")));
      stubInternetSession(USER_ID, session);

      List<String> result = stpInterface.getPermissionList(USER_ID, "internet");

      assertThat(result).containsExactly("cached.perm");
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("internet 渠道缓存为 List 形态时正确读取(Redis 序列化兼容)")
    void internetChannel_cacheAsList_readsCorrectly() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS,
          java.util.Arrays.asList("a.b", "c.d"));
      stubInternetSession(USER_ID, session);

      List<String> result = stpInterface.getPermissionList(USER_ID, "internet");

      assertThat(result).containsExactlyInAnyOrder("a.b", "c.d");
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("branch 渠道直接返回二次授权冻结的快照(不调用 PermissionResolver)")
    void branchChannel_returnsFrozenSnapshotWithoutResolver() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS,
          new java.util.HashSet<>(Set.of("branch.perm")));
      when(mockBranch.getTokenValueByLoginId(USER_ID)).thenReturn(TOKEN);
      when(mockBranch.getTokenSessionByToken(TOKEN)).thenReturn(session);

      List<String> result = stpInterface.getPermissionList(USER_ID, "branch");

      assertThat(result).containsExactly("branch.perm");
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("branch 渠道缓存为空时返回空列表(不调用 PermissionResolver)")
    void branchChannel_emptyCache_returnsEmptyWithoutResolver() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      when(mockBranch.getTokenValueByLoginId(USER_ID)).thenReturn(TOKEN);
      when(mockBranch.getTokenSessionByToken(TOKEN)).thenReturn(session);

      List<String> result = stpInterface.getPermissionList(USER_ID, "branch");

      assertThat(result).isEmpty();
      verify(permissionResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("loginId 为 String 形态时正确解析为 UserId")
    void loginIdAsString_parsedCorrectly() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      stubInternetSession("1001", session);
      when(permissionResolver.resolve(UserId.of(USER_ID), PLAN_NO))
          .thenReturn(snapshotWith("str.perm"));

      List<String> result = stpInterface.getPermissionList("1001", "internet");

      assertThat(result).containsExactly("str.perm");
      verify(permissionResolver).resolve(eq(UserId.of(USER_ID)), eq(PLAN_NO));
    }

    @Test
    @DisplayName("PermissionResolver 抛异常时返回空列表(安全降级)")
    void resolverThrows_returnsEmpty() {
      SaSession session = new SaSession("session-1");
      session.set(IamStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      stubInternetSession(USER_ID, session);
      when(permissionResolver.resolve(any(), any()))
          .thenThrow(new RuntimeException("resolver down"));

      List<String> result = stpInterface.getPermissionList(USER_ID, "internet");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getTokenSessionByLoginId 抛异常时返回空列表(用户可能未登录)")
    void getTokenSessionThrows_returnsEmpty() {
      when(mockInternet.getTokenValueByLoginId(USER_ID))
          .thenThrow(new RuntimeException("session store down"));

      List<String> result = stpInterface.getPermissionList(USER_ID, "internet");

      assertThat(result).isEmpty();
      verify(permissionResolver, never()).resolve(any(), any());
    }
  }
}
