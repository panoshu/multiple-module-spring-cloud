package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.SystemException;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SaTokenChannelSessionAdapter 渠道会话适配器测试。
 *
 * <p>覆盖核心可观察行为:
 * <ul>
 *   <li>当前登录上下文查询(渠道/用户ID/用户编号)委托给 ChannelContextProvider</li>
 *   <li>setCurrentPlan/clearCurrentPlan/getCurrentPlanId/getCurrentPermissions 读写 Token-Session</li>
 *   <li>setSecondaryAuthSession/clearSecondaryAuthSession 仅对 BRANCH 渠道生效</li>
 *   <li>login/logout/kickout 委托给对应渠道的 StpXxxUtil</li>
 *   <li>未登录场景抛 BusinessException,框架异常封装为 SystemException</li>
 * </ul>
 *
 * <p>使用真实 {@link SaSession} 验证可观察的会话状态变更。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SaTokenChannelSessionAdapter 渠道会话适配器")
class SaTokenChannelSessionAdapterTest {

  private static final Long USER_ID = 1001L;
  private static final String PLAN_NO = "PLAN001";
  private static final String TOKEN = "token-xxx";

  @Mock
  private ChannelContextProvider channelContextProvider;

  private SaTokenChannelSessionAdapter adapter;

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

    adapter = new SaTokenChannelSessionAdapter(channelContextProvider);
  }

  @AfterEach
  void tearDown() {
    StpInternetUtil.stpLogic = originalInternet;
    StpHqUtil.stpLogic = originalHq;
    StpBranchUtil.stpLogic = originalBranch;
  }

  /**
   * 配置当前渠道为指定类型,并使对应 StpLogic 已登录、返回给定 session。
   */
  private void stubLoggedIn(ChannelType channel, SaSession session) {
    when(channelContextProvider.currentChannelType()).thenReturn(channel);
    StpLogic mock = switch (channel) {
      case INTERNET -> mockInternet;
      case HQ -> mockHq;
      case BRANCH -> mockBranch;
    };
    when(mock.isLogin()).thenReturn(true);
    when(mock.getTokenSession()).thenReturn(session);
  }

  @Nested
  @DisplayName("当前登录上下文查询")
  class CurrentContext {

    @Test
    @DisplayName("currentChannelType 委托给 ChannelContextProvider")
    void currentChannelType_delegatesToProvider() {
      when(channelContextProvider.currentChannelType()).thenReturn(ChannelType.HQ);

      assertThat(adapter.currentChannelType()).isEqualTo(ChannelType.HQ);
    }

    @Test
    @DisplayName("currentUserId 委托给 ChannelContextProvider")
    void currentUserId_delegatesToProvider() {
      when(channelContextProvider.currentUserId()).thenReturn(USER_ID);

      assertThat(adapter.currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("currentUserNo 返回 currentUserId 的字符串形态")
    void currentUserNo_returnsStringOfUserId() {
      when(channelContextProvider.currentUserId()).thenReturn(USER_ID);

      assertThat(adapter.currentUserNo()).isEqualTo("1001");
    }
  }

  @Nested
  @DisplayName("setCurrentPlan")
  class SetCurrentPlan {

    @Test
    @DisplayName("planId 为 null 时抛 BusinessException")
    void nullPlanId_throwsBusinessException() {
      assertThatThrownBy(() -> adapter.setCurrentPlan(null, Set.of("a.b")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("planId 为空白字符串时抛 BusinessException")
    void blankPlanId_throwsBusinessException() {
      assertThatThrownBy(() -> adapter.setCurrentPlan("   ", Set.of("a.b")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("未登录时抛 BusinessException(NOT_LOGGED_IN)")
    void notLoggedIn_throwsBusinessException() {
      when(channelContextProvider.currentChannelType()).thenReturn(ChannelType.INTERNET);
      when(mockInternet.isLogin()).thenReturn(false);

      assertThatThrownBy(() -> adapter.setCurrentPlan(PLAN_NO, Set.of("a.b")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("有效入参时写入 planId 与 permissions(非空集合)")
    void validArgs_writesBothKeys() {
      SaSession session = new SaSession("session-1");
      stubLoggedIn(ChannelType.INTERNET, session);

      adapter.setCurrentPlan(PLAN_NO, Set.of("a.b", "c.d"));

      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID))
          .isEqualTo(PLAN_NO);
      Object perms = session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS);
      assertThat(perms).isInstanceOf(java.util.HashSet.class);
      assertThat(((java.util.Set<?>) perms).toArray()).containsExactlyInAnyOrder("a.b", "c.d");
    }

    @Test
    @DisplayName("permissions 为 null 时写入空集合")
    void nullPermissions_writesEmptySet() {
      SaSession session = new SaSession("session-1");
      stubLoggedIn(ChannelType.INTERNET, session);

      adapter.setCurrentPlan(PLAN_NO, null);

      Object perms = session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS);
      assertThat(perms).isInstanceOf(java.util.HashSet.class);
      assertThat((java.util.Set<?>) perms).isEmpty();
    }

    @Test
    @DisplayName("session.set 抛异常时封装为 SystemException")
    void sessionSetThrows_wrapsAsSystemException() {
      SaSession session = org.mockito.Mockito.mock(SaSession.class);
      when(session.set(any(), any()))
          .thenThrow(new RuntimeException("redis down"));
      stubLoggedIn(ChannelType.INTERNET, session);

      assertThatThrownBy(() -> adapter.setCurrentPlan(PLAN_NO, Set.of("a.b")))
          .isInstanceOf(SystemException.class);
    }
  }

  @Nested
  @DisplayName("clearCurrentPlan")
  class ClearCurrentPlan {

    @Test
    @DisplayName("已登录时删除两个缓存键")
    void loggedIn_deletesBothKeys() {
      SaSession session = new SaSession("session-1");
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS, Set.of("a.b"));
      stubLoggedIn(ChannelType.BRANCH, session);

      adapter.clearCurrentPlan();

      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID))
          .isNull();
      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS))
          .isNull();
    }

    @Test
    @DisplayName("session.delete 抛异常时封装为 SystemException")
    void sessionDeleteThrows_wrapsAsSystemException() {
      SaSession session = org.mockito.Mockito.mock(SaSession.class);
      when(session.delete(any()))
          .thenThrow(new RuntimeException("redis down"));
      stubLoggedIn(ChannelType.INTERNET, session);

      assertThatThrownBy(() -> adapter.clearCurrentPlan())
          .isInstanceOf(SystemException.class);
    }
  }

  @Nested
  @DisplayName("getCurrentPlanId")
  class GetCurrentPlanId {

    @Test
    @DisplayName("session 中存在 planId 时返回其字符串形态")
    void planIdPresent_returnsStringValue() {
      SaSession session = new SaSession("session-1");
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID, 12345L);
      stubLoggedIn(ChannelType.HQ, session);

      assertThat(adapter.getCurrentPlanId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("session 中无 planId 时返回 null")
    void planIdAbsent_returnsNull() {
      SaSession session = new SaSession("session-1");
      stubLoggedIn(ChannelType.HQ, session);

      assertThat(adapter.getCurrentPlanId()).isNull();
    }

    @Test
    @DisplayName("session 抛异常时返回 null")
    void sessionThrows_returnsNull() {
      when(channelContextProvider.currentChannelType()).thenReturn(ChannelType.INTERNET);
      when(mockInternet.isLogin()).thenReturn(true);
      when(mockInternet.getTokenSession())
          .thenThrow(new RuntimeException("session store down"));

      assertThat(adapter.getCurrentPlanId()).isNull();
    }
  }

  @Nested
  @DisplayName("getCurrentPermissions")
  class GetCurrentPermissions {

    @Test
    @DisplayName("session 值为 Set 时返回字符串集合")
    void valueIsSet_returnsStringSet() {
      SaSession session = new SaSession("session-1");
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS,
          new java.util.HashSet<>(Set.of("a.b", "c.d")));
      stubLoggedIn(ChannelType.INTERNET, session);

      assertThat(adapter.getCurrentPermissions())
          .containsExactlyInAnyOrder("a.b", "c.d");
    }

    @Test
    @DisplayName("session 值为 List 时返回字符串集合(Redis 序列化兼容)")
    void valueIsList_returnsStringSet() {
      SaSession session = new SaSession("session-1");
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS,
          List.of("a.b", "c.d"));
      stubLoggedIn(ChannelType.INTERNET, session);

      assertThat(adapter.getCurrentPermissions())
          .containsExactlyInAnyOrder("a.b", "c.d");
    }

    @Test
    @DisplayName("session 值为 null 时返回空集合")
    void valueIsNull_returnsEmptySet() {
      SaSession session = new SaSession("session-1");
      stubLoggedIn(ChannelType.INTERNET, session);

      assertThat(adapter.getCurrentPermissions()).isEmpty();
    }

    @Test
    @DisplayName("session 抛异常时返回空集合")
    void sessionThrows_returnsEmptySet() {
      when(channelContextProvider.currentChannelType()).thenReturn(ChannelType.INTERNET);
      when(mockInternet.isLogin()).thenReturn(true);
      when(mockInternet.getTokenSession())
          .thenThrow(new RuntimeException("session store down"));

      assertThat(adapter.getCurrentPermissions()).isEmpty();
    }
  }

  @Nested
  @DisplayName("setSecondaryAuthSession")
  class SetSecondaryAuthSession {

    @Test
    @DisplayName("非 BRANCH 渠道抛 BusinessException(SECONDARY_AUTH_STRATEGY_NOT_SUPPORTED)")
    void nonBranchChannel_throwsBusinessException() {
      stubLoggedIn(ChannelType.INTERNET, new SaSession("session-1"));

      assertThatThrownBy(() -> adapter.setSecondaryAuthSession(
          9001L, 2001L, PLAN_NO, Set.of("a.b")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("BRANCH 渠道写入 4 个会话键")
    void branchChannel_writesAllFourKeys() {
      SaSession session = new SaSession("session-1");
      stubLoggedIn(ChannelType.BRANCH, session);

      adapter.setSecondaryAuthSession(9001L, 2001L, PLAN_NO, Set.of("a.b"));

      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_SECONDARY_AUTH_ID))
          .isEqualTo(9001L);
      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_BORROWED_APPROVER_ID))
          .isEqualTo(2001L);
      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID))
          .isEqualTo(PLAN_NO);
      Object perms = session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS);
      assertThat(((java.util.Set<?>) perms).toArray()).containsExactly("a.b");
    }

    @Test
    @DisplayName("BRANCH 渠道 permissions 为 null 时写入空集合")
    void branchChannel_nullPermissions_writesEmptySet() {
      SaSession session = new SaSession("session-1");
      stubLoggedIn(ChannelType.BRANCH, session);

      adapter.setSecondaryAuthSession(9001L, 2001L, PLAN_NO, null);

      Object perms = session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS);
      assertThat(perms).isInstanceOf(java.util.HashSet.class);
      assertThat((java.util.Set<?>) perms).isEmpty();
    }
  }

  @Nested
  @DisplayName("clearSecondaryAuthSession")
  class ClearSecondaryAuthSession {

    @Test
    @DisplayName("非 BRANCH 渠道静默返回(无异常)")
    void nonBranchChannel_silentReturn() {
      stubLoggedIn(ChannelType.INTERNET, new SaSession("session-1"));

      assertThatCode(() -> adapter.clearSecondaryAuthSession())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BRANCH 渠道删除 4 个会话键")
    void branchChannel_deletesAllFourKeys() {
      SaSession session = new SaSession("session-1");
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_SECONDARY_AUTH_ID, 9001L);
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_BORROWED_APPROVER_ID, 2001L);
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID, PLAN_NO);
      session.set(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS, Set.of("a.b"));
      stubLoggedIn(ChannelType.BRANCH, session);

      adapter.clearSecondaryAuthSession();

      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_SECONDARY_AUTH_ID))
          .isNull();
      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_BORROWED_APPROVER_ID))
          .isNull();
      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PLAN_ID))
          .isNull();
      assertThat(session.get(SaTokenChannelSessionAdapter.SESSION_KEY_CURRENT_PERMISSIONS))
          .isNull();
    }
  }

  @Nested
  @DisplayName("kickout")
  class Kickout {

    @Test
    @DisplayName("userId 为 null 时为 no-op")
    void nullUserId_isNoOp() {
      assertThatCode(() -> adapter.kickout(null, ChannelType.INTERNET))
          .doesNotThrowAnyException();
      verify(mockInternet, never()).kickout(any());
    }

    @Test
    @DisplayName("channelType 为 null 时为 no-op")
    void nullChannelType_isNoOp() {
      assertThatCode(() -> adapter.kickout(USER_ID, null))
          .doesNotThrowAnyException();
      verify(mockInternet, never()).kickout(any());
      verify(mockHq, never()).kickout(any());
      verify(mockBranch, never()).kickout(any());
    }

    @Test
    @DisplayName("INTERNET 渠道委托给 StpInternetUtil.kickout")
    void internetChannel_delegatesToInternetUtil() {
      adapter.kickout(USER_ID, ChannelType.INTERNET);

      verify(mockInternet).kickout(USER_ID);
      verify(mockHq, never()).kickout(any());
      verify(mockBranch, never()).kickout(any());
    }

    @Test
    @DisplayName("HQ 渠道委托给 StpHqUtil.kickout")
    void hqChannel_delegatesToHqUtil() {
      adapter.kickout(USER_ID, ChannelType.HQ);

      verify(mockHq).kickout(USER_ID);
    }

    @Test
    @DisplayName("BRANCH 渠道委托给 StpBranchUtil.kickout")
    void branchChannel_delegatesToBranchUtil() {
      adapter.kickout(USER_ID, ChannelType.BRANCH);

      verify(mockBranch).kickout(USER_ID);
    }

    @Test
    @DisplayName("StpLogic.kickout 抛异常时封装为 SystemException")
    void kickoutThrows_wrapsAsSystemException() {
      doThrow(new RuntimeException("redis down")).when(mockInternet).kickout(USER_ID);

      assertThatThrownBy(() -> adapter.kickout(USER_ID, ChannelType.INTERNET))
          .isInstanceOf(SystemException.class);
    }
  }

  @Nested
  @DisplayName("login")
  class Login {

    @Test
    @DisplayName("userId 为 null 时抛 BusinessException")
    void nullUserId_throwsBusinessException() {
      assertThatThrownBy(() -> adapter.login(null, ChannelType.INTERNET))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("channelType 为 null 时抛 BusinessException")
    void nullChannelType_throwsBusinessException() {
      assertThatThrownBy(() -> adapter.login(USER_ID, null))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("INTERNET 渠道委托给 StpInternetUtil.login")
    void internetChannel_delegatesToInternetUtil() {
      adapter.login(USER_ID, ChannelType.INTERNET);

      verify(mockInternet).login(USER_ID);
    }

    @Test
    @DisplayName("BRANCH 渠道委托给 StpBranchUtil.login")
    void branchChannel_delegatesToBranchUtil() {
      adapter.login(USER_ID, ChannelType.BRANCH);

      verify(mockBranch).login(USER_ID);
    }

    @Test
    @DisplayName("StpLogic.login 抛异常时封装为 SystemException")
    void loginThrows_wrapsAsSystemException() {
      doThrow(new RuntimeException("session store down")).when(mockHq).login(USER_ID);

      assertThatThrownBy(() -> adapter.login(USER_ID, ChannelType.HQ))
          .isInstanceOf(SystemException.class);
    }
  }

  @Nested
  @DisplayName("logout")
  class Logout {

    @Test
    @DisplayName("channelType 为 null 时为 no-op")
    void nullChannelType_isNoOp() {
      assertThatCode(() -> adapter.logout(null))
          .doesNotThrowAnyException();
      verify(mockInternet, never()).logout();
      verify(mockHq, never()).logout();
      verify(mockBranch, never()).logout();
    }

    @Test
    @DisplayName("INTERNET 渠道委托给 StpInternetUtil.logout")
    void internetChannel_delegatesToInternetUtil() {
      adapter.logout(ChannelType.INTERNET);

      verify(mockInternet).logout();
    }

    @Test
    @DisplayName("HQ 渠道委托给 StpHqUtil.logout")
    void hqChannel_delegatesToHqUtil() {
      adapter.logout(ChannelType.HQ);

      verify(mockHq).logout();
    }

    @Test
    @DisplayName("BRANCH 渠道委托给 StpBranchUtil.logout")
    void branchChannel_delegatesToBranchUtil() {
      adapter.logout(ChannelType.BRANCH);

      verify(mockBranch).logout();
    }

    @Test
    @DisplayName("StpLogic.logout 抛异常时封装为 SystemException")
    void logoutThrows_wrapsAsSystemException() {
      doThrow(new RuntimeException("redis down")).when(mockInternet).logout();

      assertThatThrownBy(() -> adapter.logout(ChannelType.INTERNET))
          .isInstanceOf(SystemException.class);
    }
  }
}
