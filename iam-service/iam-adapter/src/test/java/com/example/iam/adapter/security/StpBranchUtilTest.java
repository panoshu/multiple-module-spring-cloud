package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * StpBranchUtil 网点渠道 StpLogic 工具类测试。
 *
 * <p>覆盖核心可观察行为:
 * <ul>
 *   <li>TYPE/TOKEN_NAME 及二次授权会话键常量符合网点渠道约定</li>
 *   <li>默认 stpLogic 实例以 TYPE 作为 loginType 初始化</li>
 *   <li>getTokenSessionByLoginId 在 token 为 null 时返回 null</li>
 *   <li>hasSecondaryAuth/getSecondaryAuthSessionId/getBorrowedApproverId 在不同登录与
 *       会话状态下的返回值(包括 Number 与 String 两种序列化形态)</li>
 * </ul>
 *
 * <p>通过替换 {@link StpBranchUtil#stpLogic} 公共静态字段为 mock 实现完成隔离测试,
 * 每个测试结束后恢复原字段,避免污染其他测试类。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("StpBranchUtil 网点渠道工具类")
class StpBranchUtilTest {

  private StpLogic originalStpLogic;
  private StpLogic mockStpLogic;

  @BeforeEach
  void setUp() {
    originalStpLogic = StpBranchUtil.stpLogic;
    mockStpLogic = Mockito.mock(StpLogic.class);
    StpBranchUtil.stpLogic = mockStpLogic;
  }

  @AfterEach
  void tearDown() {
    StpBranchUtil.stpLogic = originalStpLogic;
    Mockito.reset(mockStpLogic);
  }

  @Nested
  @DisplayName("常量定义")
  class Constants {

    @Test
    @DisplayName("TYPE 常量为 branch")
    void typeConstant_isBranch() {
      assertThat(StpBranchUtil.TYPE).isEqualTo("branch");
    }

    @Test
    @DisplayName("TOKEN_NAME 常量为 satoken-branch")
    void tokenNameConstant_isSatokenBranch() {
      assertThat(StpBranchUtil.TOKEN_NAME).isEqualTo("satoken-branch");
    }

    @Test
    @DisplayName("二次授权会话键常量定义正确")
    void sessionKeyConstants_areCorrect() {
      assertThat(StpBranchUtil.SESSION_KEY_SECONDARY_AUTH_ID)
          .isEqualTo("secondaryAuthSessionId");
      assertThat(StpBranchUtil.SESSION_KEY_BORROWED_APPROVER_ID)
          .isEqualTo("borrowedApproverId");
    }
  }

  @Nested
  @DisplayName("默认 stpLogic 实例(在未被替换时)")
  class DefaultStpLogic {

    @Test
    @DisplayName("默认 stpLogic 非 null 且 loginType 为 branch")
    void defaultStpLogic_loginTypeMatchesType() {
      // 临时恢复原始 stpLogic 进行断言
      StpBranchUtil.stpLogic = originalStpLogic;
      try {
        assertThat(originalStpLogic).isNotNull();
        assertThat(originalStpLogic.getLoginType()).isEqualTo(StpBranchUtil.TYPE);
      } finally {
        StpBranchUtil.stpLogic = mockStpLogic;
      }
    }
  }

  @Nested
  @DisplayName("getTokenSessionByLoginId")
  class GetTokenSessionByLoginId {

    @Test
    @DisplayName("token 为 null 时返回 null")
    void tokenNull_returnsNull() {
      when(mockStpLogic.getTokenValueByLoginId(1001L)).thenReturn(null);

      SaSession result = StpBranchUtil.getTokenSessionByLoginId(1001L);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("token 非空时返回 tokenSessionByToken 结果")
    void tokenPresent_returnsSession() {
      SaSession expected = new SaSession("test-session");
      when(mockStpLogic.getTokenValueByLoginId(1001L)).thenReturn("token-abc");
      when(mockStpLogic.getTokenSessionByToken("token-abc")).thenReturn(expected);

      SaSession result = StpBranchUtil.getTokenSessionByLoginId(1001L);

      assertThat(result).isSameAs(expected);
    }
  }

  @Nested
  @DisplayName("hasSecondaryAuth")
  class HasSecondaryAuth {

    @Test
    @DisplayName("未登录时返回 false")
    void notLoggedIn_returnsFalse() {
      when(mockStpLogic.isLogin()).thenReturn(false);

      assertThat(StpBranchUtil.hasSecondaryAuth()).isFalse();
    }

    @Test
    @DisplayName("已登录但未设置二次授权会话时返回 false")
    void loggedInWithoutSecondaryAuth_returnsFalse() {
      SaSession session = new SaSession("session-1");
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.hasSecondaryAuth()).isFalse();
    }

    @Test
    @DisplayName("已登录且设置二次授权会话时返回 true")
    void loggedInWithSecondaryAuth_returnsTrue() {
      SaSession session = new SaSession("session-1");
      session.set(StpBranchUtil.SESSION_KEY_SECONDARY_AUTH_ID, 9001L);
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.hasSecondaryAuth()).isTrue();
    }
  }

  @Nested
  @DisplayName("getSecondaryAuthSessionId")
  class GetSecondaryAuthSessionId {

    @Test
    @DisplayName("未登录时返回 null")
    void notLoggedIn_returnsNull() {
      when(mockStpLogic.isLogin()).thenReturn(false);

      assertThat(StpBranchUtil.getSecondaryAuthSessionId()).isNull();
    }

    @Test
    @DisplayName("已登录但未设置二次授权会话时返回 null")
    void loggedInWithoutValue_returnsNull() {
      SaSession session = new SaSession("session-1");
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.getSecondaryAuthSessionId()).isNull();
    }

    @Test
    @DisplayName("会话值为 Number 时返回其 longValue")
    void valueIsNumber_returnsLongValue() {
      SaSession session = new SaSession("session-1");
      session.set(StpBranchUtil.SESSION_KEY_SECONDARY_AUTH_ID, 7777L);
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.getSecondaryAuthSessionId()).isEqualTo(7777L);
    }

    @Test
    @DisplayName("会话值为 String 时解析为 long")
    void valueIsString_parsesToLong() {
      SaSession session = new SaSession("session-1");
      session.set(StpBranchUtil.SESSION_KEY_SECONDARY_AUTH_ID, "8888");
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.getSecondaryAuthSessionId()).isEqualTo(8888L);
    }
  }

  @Nested
  @DisplayName("getBorrowedApproverId")
  class GetBorrowedApproverId {

    @Test
    @DisplayName("未登录时返回 null")
    void notLoggedIn_returnsNull() {
      when(mockStpLogic.isLogin()).thenReturn(false);

      assertThat(StpBranchUtil.getBorrowedApproverId()).isNull();
    }

    @Test
    @DisplayName("已登录但未设置借用经办人时返回 null")
    void loggedInWithoutValue_returnsNull() {
      SaSession session = new SaSession("session-1");
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.getBorrowedApproverId()).isNull();
    }

    @Test
    @DisplayName("会话值为 Number 时返回其 longValue")
    void valueIsNumber_returnsLongValue() {
      SaSession session = new SaSession("session-1");
      session.set(StpBranchUtil.SESSION_KEY_BORROWED_APPROVER_ID, 2001);
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.getBorrowedApproverId()).isEqualTo(2001L);
    }

    @Test
    @DisplayName("会话值为 String 时解析为 long")
    void valueIsString_parsesToLong() {
      SaSession session = new SaSession("session-1");
      session.set(StpBranchUtil.SESSION_KEY_BORROWED_APPROVER_ID, "3001");
      when(mockStpLogic.isLogin()).thenReturn(true);
      when(mockStpLogic.getTokenSession()).thenReturn(session);

      assertThat(StpBranchUtil.getBorrowedApproverId()).isEqualTo(3001L);
    }
  }
}
