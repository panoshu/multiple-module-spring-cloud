package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.entity.UserProfile;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("User 聚合根行为")
class UserTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final UserId USER_ID = UserId.of(1001L);

  @Test
  @DisplayName("create 工厂方法初始化 ACTIVE 状态和空档案")
  void create_initializesActiveStatusAndEmptyProfile() {
    User user = User.create(
        USER_ID, ChannelType.INTERNET,
        "hr001", "张三", OPERATOR
    );

    assertThat(user.id()).isEqualTo(USER_ID);
    assertThat(user.channelType()).isEqualTo(ChannelType.INTERNET);
    assertThat(user.loginName()).isEqualTo("hr001");
    assertThat(user.displayName()).isEqualTo("张三");
    assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.lastLoginTime()).isNull();
    assertThat(user.lastLoginIp()).isNull();
    assertThat(user.profile()).isNull();
    assertThat(user.createdBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("create 工厂方法可附带档案")
  void create_withProfile_attachesProfile() {
    UserProfile profile = UserProfile.create(
        USER_ID, ChannelType.INTERNET,
        "hr@example.com", "13800138000",
        null, null, null, "EMP-001", Map.of(), OPERATOR
    );

    User user = User.create(
        USER_ID, ChannelType.INTERNET,
        "hr001", "张三", profile, OPERATOR
    );

    assertThat(user.profile()).isNotNull();
    assertThat(user.profile().email()).isEqualTo("hr@example.com");
  }

  @Test
  @DisplayName("disable 将 ACTIVE 状态置为 DISABLED 并记录原因")
  void disable_changesStatusToDisabled() {
    User user = createUser();
    Version initialVersion = user.version();

    user.disable(OPERATOR, "违规操作");

    assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
    assertThat(user.version()).isEqualTo(initialVersion.next());
    assertThat(user.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("disable 在已 DISABLED 状态时抛 DomainException")
  void disable_throwsWhenAlreadyDisabled() {
    User user = createUser();
    user.disable(OPERATOR, "首次禁用");

    assertThatThrownBy(() -> user.disable(OPERATOR, "再次禁用"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("disable 拒绝空原因")
  void disable_rejectsBlankReason() {
    User user = createUser();

    assertThatThrownBy(() -> user.disable(OPERATOR, ""))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("enable 将 DISABLED 状态置为 ACTIVE")
  void enable_changesDisabledToActive() {
    User user = createUser();
    user.disable(OPERATOR, "暂停使用");

    user.enable(OPERATOR);

    assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("enable 在 ACTIVE 状态时抛 DomainException")
  void enable_throwsWhenAlreadyActive() {
    User user = createUser();

    assertThatThrownBy(() -> user.enable(OPERATOR))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("lock 将 ACTIVE 状态置为 LOCKED 并记录原因")
  void lock_changesActiveToLocked() {
    User user = createUser();

    user.lock(OPERATOR, "登录失败次数超限");

    assertThat(user.status()).isEqualTo(UserStatus.LOCKED);
  }

  @Test
  @DisplayName("lock 在 LOCKED 状态时抛 DomainException")
  void lock_throwsWhenAlreadyLocked() {
    User user = createUser();
    user.lock(OPERATOR, "首次锁定");

    assertThatThrownBy(() -> user.lock(OPERATOR, "再次锁定"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("lock 在 DISABLED 状态时抛 DomainException")
  void lock_throwsWhenDisabled() {
    User user = createUser();
    user.disable(OPERATOR, "已禁用");

    assertThatThrownBy(() -> user.lock(OPERATOR, "尝试锁定"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("markLoginSuccess 更新最后登录时间和 IP")
  void markLoginSuccess_updatesLastLoginInfo() {
    User user = createUser();
    LocalDateTime loginTime = LocalDateTime.of(2026, 7, 26, 14, 30);

    user.markLoginSuccess("192.168.1.1", loginTime, OPERATOR);

    assertThat(user.lastLoginTime()).isEqualTo(loginTime);
    assertThat(user.lastLoginIp()).isEqualTo("192.168.1.1");
  }

  @Test
  @DisplayName("markLoginSuccess 在 DISABLED 状态时抛 DomainException")
  void markLoginSuccess_throwsWhenDisabled() {
    User user = createUser();
    user.disable(OPERATOR, "禁用");

    assertThatThrownBy(() -> user.markLoginSuccess("1.1.1.1", LocalDateTime.now(), OPERATOR))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("attachProfile 为无档案用户附加渠道档案")
  void attachProfile_attachesProfileToUser() {
    User user = createUser();
    UserProfile profile = UserProfile.create(
        USER_ID, ChannelType.INTERNET,
        "hr@example.com", "13800138000",
        null, null, null, "EMP-001", Map.of(), OPERATOR
    );

    user.attachProfile(profile, OPERATOR);

    assertThat(user.profile()).isNotNull();
    assertThat(user.profile().email()).isEqualTo("hr@example.com");
  }

  @Test
  @DisplayName("attachProfile 在档案已存在时抛 DomainException")
  void attachProfile_throwsWhenProfileExists() {
    User user = createUserWithProfile();
    UserProfile newProfile = UserProfile.create(
        USER_ID, ChannelType.INTERNET,
        "new@example.com", "13900139000",
        null, null, null, "EMP-001", Map.of(), OPERATOR
    );

    assertThatThrownBy(() -> user.attachProfile(newProfile, OPERATOR))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("updateProfile 委托给已存在的档案实体")
  void updateProfile_delegatesToExistingProfile() {
    User user = createUserWithProfile();

    user.updateProfile(
        "new@example.com", "13900139000",
        null, "Senior HR",
        null, "EMP-001", Map.of("clearance", "level2"),
        OPERATOR
    );

    assertThat(user.profile().email()).isEqualTo("new@example.com");
    assertThat(user.profile().position()).isEqualTo("Senior HR");
    assertThat(user.profile().extraAttributes()).containsEntry("clearance", "level2");
  }

  @Test
  @DisplayName("updateProfile 在档案不存在时抛 DomainException")
  void updateProfile_throwsWhenProfileMissing() {
    User user = createUser();

    assertThatThrownBy(() -> user.updateProfile(
        "new@example.com", "13900139000",
        null, null, null, null, Map.of(), OPERATOR
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("reconstitute 从数据库状态恢复完整聚合")
  void reconstitute_restoresFullAggregate() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 14, 30);
    LocalDateTime lastLogin = LocalDateTime.of(2026, 7, 26, 10, 0);
    Version version = Version.of(7);
    UserProfile profile = UserProfile.reconstitute(
        USER_ID, ChannelType.INTERNET,
        "hr@example.com", "13800138000",
        null, null, null, "EMP-001", Map.of(),
        OPERATOR, OPERATOR, createdAt, updatedAt, version
    );

    User user = User.reconstitute(
        USER_ID, ChannelType.INTERNET,
        "hr001", "张三",
        UserStatus.ACTIVE, lastLogin, "10.0.0.1",
        profile, OPERATOR, OPERATOR, createdAt, updatedAt, version
    );

    assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.lastLoginTime()).isEqualTo(lastLogin);
    assertThat(user.lastLoginIp()).isEqualTo("10.0.0.1");
    assertThat(user.profile()).isNotNull();
    assertThat(user.version()).isEqualTo(version);
  }

  @Test
  @DisplayName("create 拒绝空登录名")
  void create_rejectsBlankLoginName() {
    assertThatThrownBy(() -> User.create(
        USER_ID, ChannelType.INTERNET,
        "", "张三", OPERATOR
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("create 拒绝空渠道类型")
  void create_rejectsNullChannelType() {
    assertThatThrownBy(() -> User.create(
        USER_ID, null,
        "hr001", "张三", OPERATOR
    )).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("disable 与 enable 可循环转换(终态外的双向流转)")
  void disableAndEnable_canCycleBackToActive() {
    User user = createUser();
    user.disable(OPERATOR, "暂停");
    user.enable(OPERATOR);
    user.disable(OPERATOR, "再次暂停");

    assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
  }

  private User createUser() {
    return User.create(USER_ID, ChannelType.INTERNET, "hr001", "张三", OPERATOR);
  }

  private User createUserWithProfile() {
    UserProfile profile = UserProfile.create(
        USER_ID, ChannelType.INTERNET,
        "hr@example.com", "13800138000",
        null, null, null, "EMP-001", Map.of(), OPERATOR
    );
    return User.create(USER_ID, ChannelType.INTERNET, "hr001", "张三", profile, OPERATOR);
  }
}
