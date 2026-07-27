package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * SaTokenPermissionCacheAdapter 权限缓存适配器测试。
 *
 * <p>覆盖核心可观察行为:
 * <ul>
 *   <li>evictByUser:跳过 null;三渠道均尝试清除缓存键;某渠道未登录时跳过且不抛错</li>
 *   <li>evictByPlan:跳过 null/blank;目前实现为 no-op(不抛错)</li>
 *   <li>evictAll:目前实现为 no-op(不抛错)</li>
 * </ul>
 *
 * <p>使用真实 {@link SaSession} 实例作为 Token-Session,验证缓存键被实际删除
 * (避免单纯验证 mock 调用,关注可观察的最终状态)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SaTokenPermissionCacheAdapter 权限缓存失效")
class SaTokenPermissionCacheAdapterTest {

  private static final Long USER_ID = 1001L;
  private static final String TOKEN = "token-xxx";
  private static final String PLAN_NO = "PLAN001";

  private SaTokenPermissionCacheAdapter adapter;

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

    adapter = new SaTokenPermissionCacheAdapter();
  }

  @AfterEach
  void tearDown() {
    StpInternetUtil.stpLogic = originalInternet;
    StpHqUtil.stpLogic = originalHq;
    StpBranchUtil.stpLogic = originalBranch;
  }

  /**
   * 配置指定渠道 mock:loginId 对应 token,token 对应 session。
   */
  private void stubChannelSession(StpLogic mock, Object loginId, SaSession session) {
    when(mock.getTokenValueByLoginId(loginId)).thenReturn(TOKEN);
    when(mock.getTokenSessionByToken(TOKEN)).thenReturn(session);
  }

  /**
   * 创建一个已填充缓存键的 session。
   */
  private SaSession sessionWithCache() {
    SaSession session = new SaSession("session-1");
    session.set("currentPlanId", PLAN_NO);
    session.set("currentPermissions", java.util.Set.of("a.b"));
    return session;
  }

  @Nested
  @DisplayName("evictByUser")
  class EvictByUser {

    @Test
    @DisplayName("userId 为 null 时为 no-op(不抛错)")
    void nullUserId_isNoOp() {
      assertThatCode(() -> adapter.evictByUser(null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("三渠道均存在会话时清除两个缓存键")
    void allChannelsHaveSession_clearsAllCacheKeys() {
      SaSession internetSession = sessionWithCache();
      SaSession hqSession = sessionWithCache();
      SaSession branchSession = sessionWithCache();
      stubChannelSession(mockInternet, USER_ID, internetSession);
      stubChannelSession(mockHq, USER_ID, hqSession);
      stubChannelSession(mockBranch, USER_ID, branchSession);

      adapter.evictByUser(USER_ID);

      assertThat(internetSession.get("currentPlanId")).isNull();
      assertThat(internetSession.get("currentPermissions")).isNull();
      assertThat(hqSession.get("currentPlanId")).isNull();
      assertThat(hqSession.get("currentPermissions")).isNull();
      assertThat(branchSession.get("currentPlanId")).isNull();
      assertThat(branchSession.get("currentPermissions")).isNull();
    }

    @Test
    @DisplayName("某渠道未登录(token 为 null)时跳过该渠道,其他渠道仍清除")
    void channelNotLoggedIn_skippedOthersStillCleared() {
      SaSession branchSession = sessionWithCache();
      when(mockInternet.getTokenValueByLoginId(USER_ID)).thenReturn(null);
      when(mockHq.getTokenValueByLoginId(USER_ID))
          .thenThrow(new RuntimeException("hq session store down"));
      stubChannelSession(mockBranch, USER_ID, branchSession);

      adapter.evictByUser(USER_ID);

      // branch 渠道仍被清除
      assertThat(branchSession.get("currentPlanId")).isNull();
      assertThat(branchSession.get("currentPermissions")).isNull();
    }

    @Test
    @DisplayName("三渠道均未登录时不抛错")
    void noChannelsLoggedIn_noException() {
      when(mockInternet.getTokenValueByLoginId(USER_ID)).thenReturn(null);
      when(mockHq.getTokenValueByLoginId(USER_ID)).thenReturn(null);
      when(mockBranch.getTokenValueByLoginId(USER_ID)).thenReturn(null);

      assertThatCode(() -> adapter.evictByUser(USER_ID))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("evictByPlan")
  class EvictByPlan {

    @Test
    @DisplayName("planNo 为 null 时为 no-op")
    void nullPlanNo_isNoOp() {
      assertThatCode(() -> adapter.evictByPlan(null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("planNo 为空白字符串时为 no-op")
    void blankPlanNo_isNoOp() {
      assertThatCode(() -> adapter.evictByPlan("   "))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("有效 planNo 不抛错(当前实现依赖反向索引,暂为 no-op)")
    void validPlanNo_doesNotThrow() {
      assertThatCode(() -> adapter.evictByPlan(PLAN_NO))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("evictAll")
  class EvictAll {

    @Test
    @DisplayName("evictAll 不抛错(当前实现依赖 Redis FLUSHDB,暂为 no-op)")
    void evictAll_doesNotThrow() {
      assertThatCode(() -> adapter.evictAll())
          .doesNotThrowAnyException();
    }
  }
}
