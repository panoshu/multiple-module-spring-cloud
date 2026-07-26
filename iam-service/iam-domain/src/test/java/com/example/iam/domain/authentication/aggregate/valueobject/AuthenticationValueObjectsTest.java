package com.example.iam.domain.authentication.aggregate.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * authentication 域 5 个值对象(枚举)的单元测试。
 *
 * <p>覆盖范围:
 * <ul>
 *   <li>枚举常量数量符合设计</li>
 *   <li>所有枚举实现 {@link ValueObject} 标记接口</li>
 *   <li>便捷方法 isActive/isPending 等返回正确</li>
 *   <li>状态转换校验方法 canXxx() 对合法/非法转换返回值符合预期</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
class AuthenticationValueObjectsTest {

  // ==================== ChannelType ====================

  @Nested
  @DisplayName("ChannelType: 三套渠道类型")
  class ChannelTypeTest {

    @Test
    @DisplayName("应包含 3 个常量: INTERNET/HQ/BRANCH")
    void shouldHaveThreeConstants() {
      assertEquals(3, ChannelType.values().length);
    }

    @Test
    @DisplayName("常量顺序应为 INTERNET, HQ, BRANCH")
    void constantsShouldBeInDeclaredOrder() {
      ChannelType[] values = ChannelType.values();
      assertEquals(ChannelType.INTERNET, values[0]);
      assertEquals(ChannelType.HQ, values[1]);
      assertEquals(ChannelType.BRANCH, values[2]);
    }

    @Test
    @DisplayName("应实现 ValueObject 标记接口")
    void shouldImplementValueObject() {
      for (ChannelType type : ChannelType.values()) {
        assertTrue(type instanceof ValueObject,
            "ChannelType." + type + " 应实现 ValueObject");
      }
    }
  }

  // ==================== UserStatus ====================

  @Nested
  @DisplayName("UserStatus: 用户账号状态机")
  class UserStatusTest {

    @Test
    @DisplayName("应包含 3 个常量: ACTIVE/DISABLED/LOCKED")
    void shouldHaveThreeConstants() {
      assertEquals(3, UserStatus.values().length);
    }

    @Test
    @DisplayName("应实现 ValueObject 标记接口")
    void shouldImplementValueObject() {
      for (UserStatus status : UserStatus.values()) {
        assertTrue(status instanceof ValueObject,
            "UserStatus." + status + " 应实现 ValueObject");
      }
    }

    @Test
    @DisplayName("isActive/isDisabled/isLocked 便捷方法应返回正确")
    void convenienceMethodsShouldReturnCorrectly() {
      assertTrue(UserStatus.ACTIVE.isActive());
      assertFalse(UserStatus.ACTIVE.isDisabled());
      assertFalse(UserStatus.ACTIVE.isLocked());

      assertFalse(UserStatus.DISABLED.isActive());
      assertTrue(UserStatus.DISABLED.isDisabled());
      assertFalse(UserStatus.DISABLED.isLocked());

      assertFalse(UserStatus.LOCKED.isActive());
      assertFalse(UserStatus.LOCKED.isDisabled());
      assertTrue(UserStatus.LOCKED.isLocked());
    }

    @Test
    @DisplayName("canDisable: ACTIVE/LOCKED 可转入 DISABLED, DISABLED 不可")
    void canDisableShouldOnlyAllowFromActiveOrLocked() {
      assertTrue(UserStatus.ACTIVE.canDisable());
      assertTrue(UserStatus.LOCKED.canDisable());
      assertFalse(UserStatus.DISABLED.canDisable());
    }

    @Test
    @DisplayName("canEnable: DISABLED/LOCKED 可转入 ACTIVE, ACTIVE 不可")
    void canEnableShouldOnlyAllowFromDisabledOrLocked() {
      assertTrue(UserStatus.DISABLED.canEnable());
      assertTrue(UserStatus.LOCKED.canEnable());
      assertFalse(UserStatus.ACTIVE.canEnable());
    }

    @Test
    @DisplayName("canLock: 仅 ACTIVE 可转入 LOCKED")
    void canLockShouldOnlyAllowFromActive() {
      assertTrue(UserStatus.ACTIVE.canLock());
      assertFalse(UserStatus.DISABLED.canLock());
      assertFalse(UserStatus.LOCKED.canLock());
    }
  }

  // ==================== CredentialType ====================

  @Nested
  @DisplayName("CredentialType: 凭据类型")
  class CredentialTypeTest {

    @Test
    @DisplayName("应包含 3 个常量: PASSWORD/UKEY/DYNAMIC_TOKEN")
    void shouldHaveThreeConstants() {
      assertEquals(3, CredentialType.values().length);
    }

    @Test
    @DisplayName("常量顺序应为 PASSWORD, UKEY, DYNAMIC_TOKEN")
    void constantsShouldBeInDeclaredOrder() {
      CredentialType[] values = CredentialType.values();
      assertEquals(CredentialType.PASSWORD, values[0]);
      assertEquals(CredentialType.UKEY, values[1]);
      assertEquals(CredentialType.DYNAMIC_TOKEN, values[2]);
    }

    @Test
    @DisplayName("应实现 ValueObject 标记接口")
    void shouldImplementValueObject() {
      for (CredentialType type : CredentialType.values()) {
        assertTrue(type instanceof ValueObject,
            "CredentialType." + type + " 应实现 ValueObject");
      }
    }
  }

  // ==================== CredentialStatus ====================

  @Nested
  @DisplayName("CredentialStatus: 凭据状态机")
  class CredentialStatusTest {

    @Test
    @DisplayName("应包含 3 个常量: ACTIVE/EXPIRED/REVOKED")
    void shouldHaveThreeConstants() {
      assertEquals(3, CredentialStatus.values().length);
    }

    @Test
    @DisplayName("应实现 ValueObject 标记接口")
    void shouldImplementValueObject() {
      for (CredentialStatus status : CredentialStatus.values()) {
        assertTrue(status instanceof ValueObject,
            "CredentialStatus." + status + " 应实现 ValueObject");
      }
    }

    @Test
    @DisplayName("isActive/isExpired/isRevoked 便捷方法应返回正确")
    void convenienceMethodsShouldReturnCorrectly() {
      assertTrue(CredentialStatus.ACTIVE.isActive());
      assertFalse(CredentialStatus.ACTIVE.isExpired());
      assertFalse(CredentialStatus.ACTIVE.isRevoked());

      assertFalse(CredentialStatus.EXPIRED.isActive());
      assertTrue(CredentialStatus.EXPIRED.isExpired());
      assertFalse(CredentialStatus.EXPIRED.isRevoked());

      assertFalse(CredentialStatus.REVOKED.isActive());
      assertFalse(CredentialStatus.REVOKED.isExpired());
      assertTrue(CredentialStatus.REVOKED.isRevoked());
    }

    @Test
    @DisplayName("canMarkExpired: 仅 ACTIVE 可转入 EXPIRED")
    void canMarkExpiredShouldOnlyAllowFromActive() {
      assertTrue(CredentialStatus.ACTIVE.canMarkExpired());
      assertFalse(CredentialStatus.EXPIRED.canMarkExpired());
      assertFalse(CredentialStatus.REVOKED.canMarkExpired());
    }

    @Test
    @DisplayName("canRevoke: ACTIVE/EXPIRED 可转入 REVOKED, REVOKED 不可")
    void canRevokeShouldAllowFromActiveOrExpired() {
      assertTrue(CredentialStatus.ACTIVE.canRevoke());
      assertTrue(CredentialStatus.EXPIRED.canRevoke());
      assertFalse(CredentialStatus.REVOKED.canRevoke());
    }
  }

  // ==================== SecondaryAuthStatus ====================

  @Nested
  @DisplayName("SecondaryAuthStatus: 二次授权会话状态机")
  class SecondaryAuthStatusTest {

    @Test
    @DisplayName("应包含 6 个常量: PENDING/AUTHORIZED/EXPIRED/REVOKED/CLOSED/REJECTED")
    void shouldHaveSixConstants() {
      assertEquals(6, SecondaryAuthStatus.values().length);
    }

    @Test
    @DisplayName("常量顺序应为 PENDING, AUTHORIZED, EXPIRED, REVOKED, CLOSED, REJECTED")
    void constantsShouldBeInDeclaredOrder() {
      SecondaryAuthStatus[] values = SecondaryAuthStatus.values();
      assertEquals(SecondaryAuthStatus.PENDING, values[0]);
      assertEquals(SecondaryAuthStatus.AUTHORIZED, values[1]);
      assertEquals(SecondaryAuthStatus.EXPIRED, values[2]);
      assertEquals(SecondaryAuthStatus.REVOKED, values[3]);
      assertEquals(SecondaryAuthStatus.CLOSED, values[4]);
      assertEquals(SecondaryAuthStatus.REJECTED, values[5]);
    }

    @Test
    @DisplayName("应实现 ValueObject 标记接口")
    void shouldImplementValueObject() {
      for (SecondaryAuthStatus status : SecondaryAuthStatus.values()) {
        assertTrue(status instanceof ValueObject,
            "SecondaryAuthStatus." + status + " 应实现 ValueObject");
      }
    }

    @Test
    @DisplayName("isPending/isAuthorized/isClosed 等便捷方法应返回正确")
    void convenienceMethodsShouldReturnCorrectly() {
      assertTrue(SecondaryAuthStatus.PENDING.isPending());
      assertTrue(SecondaryAuthStatus.AUTHORIZED.isAuthorized());
      assertTrue(SecondaryAuthStatus.EXPIRED.isExpired());
      assertTrue(SecondaryAuthStatus.REVOKED.isRevoked());
      assertTrue(SecondaryAuthStatus.CLOSED.isClosed());
      assertTrue(SecondaryAuthStatus.REJECTED.isRejected());
    }

    @Test
    @DisplayName("便捷方法应对其他状态返回 false")
    void convenienceMethodsShouldReturnFalseForOtherStates() {
      SecondaryAuthStatus[] all = SecondaryAuthStatus.values();
      for (SecondaryAuthStatus status : all) {
        assertEquals(status == SecondaryAuthStatus.PENDING, status.isPending());
        assertEquals(status == SecondaryAuthStatus.AUTHORIZED, status.isAuthorized());
        assertEquals(status == SecondaryAuthStatus.EXPIRED, status.isExpired());
        assertEquals(status == SecondaryAuthStatus.REVOKED, status.isRevoked());
        assertEquals(status == SecondaryAuthStatus.CLOSED, status.isClosed());
        assertEquals(status == SecondaryAuthStatus.REJECTED, status.isRejected());
      }
    }

    @Test
    @DisplayName("canAuthorize: 仅 PENDING 可进行经办人决策(转 AUTHORIZED/REJECTED)")
    void canAuthorizeShouldOnlyAllowFromPending() {
      assertTrue(SecondaryAuthStatus.PENDING.canAuthorize());
      assertFalse(SecondaryAuthStatus.AUTHORIZED.canAuthorize());
      assertFalse(SecondaryAuthStatus.EXPIRED.canAuthorize());
      assertFalse(SecondaryAuthStatus.REVOKED.canAuthorize());
      assertFalse(SecondaryAuthStatus.CLOSED.canAuthorize());
      assertFalse(SecondaryAuthStatus.REJECTED.canAuthorize());
    }

    @Test
    @DisplayName("canRevoke: 仅 AUTHORIZED 可转入 REVOKED")
    void canRevokeShouldOnlyAllowFromAuthorized() {
      assertFalse(SecondaryAuthStatus.PENDING.canRevoke());
      assertTrue(SecondaryAuthStatus.AUTHORIZED.canRevoke());
      assertFalse(SecondaryAuthStatus.EXPIRED.canRevoke());
      assertFalse(SecondaryAuthStatus.REVOKED.canRevoke());
      assertFalse(SecondaryAuthStatus.CLOSED.canRevoke());
      assertFalse(SecondaryAuthStatus.REJECTED.canRevoke());
    }

    @Test
    @DisplayName("canExpire: 仅 AUTHORIZED 可转入 EXPIRED")
    void canExpireShouldOnlyAllowFromAuthorized() {
      assertFalse(SecondaryAuthStatus.PENDING.canExpire());
      assertTrue(SecondaryAuthStatus.AUTHORIZED.canExpire());
      assertFalse(SecondaryAuthStatus.EXPIRED.canExpire());
      assertFalse(SecondaryAuthStatus.REVOKED.canExpire());
      assertFalse(SecondaryAuthStatus.CLOSED.canExpire());
      assertFalse(SecondaryAuthStatus.REJECTED.canExpire());
    }

    @Test
    @DisplayName("canClose: 仅 AUTHORIZED 可转入 CLOSED")
    void canCloseShouldOnlyAllowFromAuthorized() {
      assertFalse(SecondaryAuthStatus.PENDING.canClose());
      assertTrue(SecondaryAuthStatus.AUTHORIZED.canClose());
      assertFalse(SecondaryAuthStatus.EXPIRED.canClose());
      assertFalse(SecondaryAuthStatus.REVOKED.canClose());
      assertFalse(SecondaryAuthStatus.CLOSED.canClose());
      assertFalse(SecondaryAuthStatus.REJECTED.canClose());
    }
  }
}
