package com.pension.permission.domain.user.aggregate;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.identity.DocumentNumber;
import com.example.shared.identity.IdentityDocument;
import com.example.shared.identity.IdentityType;
import com.pension.permission.domain.user.enumeration.UserStatus;
import com.pension.permission.domain.user.enumeration.UserType;
import com.pension.permission.domain.user.event.UserActivated;
import com.pension.permission.domain.user.event.UserDisabled;
import com.pension.permission.domain.user.event.UserFrozen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserAggregate 聚合根测试")
class UserAggregateTest {

  private UserAggregate activeUser() {
    return UserAggregate.create(
      UserNo.of("user-1"),
      UserType.AGENT,
      new IdentityDocument(IdentityType.ID_CARD, new DocumentNumber("110101199001011234")),
      new Mobile("+8613800138000"),
      UserNo.of("creator-1"));
  }

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态用户")
    void shouldCreateActiveUser() {
      var user = activeUser();

      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("identityDocument 为 null 应抛 DomainException")
    void shouldThrowWhenIdentityDocumentNull() {
      assertThatThrownBy(() -> UserAggregate.create(
        UserNo.of("user-1"),
        UserType.AGENT,
        null,
        new Mobile("+8613800138000"),
        UserNo.of("creator-1")))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("非身份证类型应抛 DomainException")
    void shouldThrowWhenNotIdCard() {
      assertThatThrownBy(() -> UserAggregate.create(
        UserNo.of("user-1"),
        UserType.AGENT,
        new IdentityDocument(IdentityType.PASSPORT, new DocumentNumber("1234567890")),
        new Mobile("+8613800138000"),
        UserNo.of("creator-1")))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("冻结 freeze")
  class FreezeTest {

    @Test
    @DisplayName("冻结活跃用户应转为 FROZEN 并注册事件")
    void shouldFreezeActiveUser() {
      var user = activeUser();

      user.freeze(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.FROZEN);
      assertThat(user.domainEvents()).anyMatch(e -> e instanceof UserFrozen);
    }

    @Test
    @DisplayName("重复冻结已冻结用户应抛 DomainException")
    void shouldThrowWhenFreezeFrozenUser() {
      var user = activeUser();
      user.freeze(UserNo.of("admin-1"));

      assertThatThrownBy(() -> user.freeze(UserNo.of("admin-1")))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("激活 activate")
  class ActivateTest {

    @Test
    @DisplayName("激活已冻结用户应转为 ACTIVE 并注册事件")
    void shouldActivateFrozenUser() {
      var user = activeUser();
      user.freeze(UserNo.of("admin-1"));

      user.activate(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(user.domainEvents()).anyMatch(e -> e instanceof UserActivated);
    }

    @Test
    @DisplayName("激活已激活用户应幂等")
    void shouldBeIdempotentWhenActivateActiveUser() {
      var user = activeUser();

      user.activate(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("停用 disable")
  class DisableTest {

    @Test
    @DisplayName("停用活跃用户应转为 DISABLED 并注册事件")
    void shouldDisableActiveUser() {
      var user = activeUser();

      user.disable(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
      assertThat(user.domainEvents()).anyMatch(e -> e instanceof UserDisabled);
    }

    @Test
    @DisplayName("停用已停用用户应幂等")
    void shouldBeIdempotentWhenDisableDisabledUser() {
      var user = activeUser();
      user.disable(UserNo.of("admin-1"));

      user.disable(UserNo.of("admin-2"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }
  }
}
