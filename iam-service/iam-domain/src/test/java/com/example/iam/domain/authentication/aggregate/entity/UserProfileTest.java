package com.example.iam.domain.authentication.aggregate.entity;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserProfile 实体行为")
class UserProfileTest {

  private static final UserNo OPERATOR = UserNo.of("U-TEST");
  private static final UserId USER_ID = UserId.of(1L);

  @Test
  @DisplayName("create 工厂方法初始化渠道专属档案字段")
  void create_initializesChannelSpecificFields() {
    UserProfile profile = UserProfile.create(
        USER_ID,
        ChannelType.INTERNET,
        "user@example.com",
        "13800138000",
        "ACME Corp",
        "HR Manager",
        null,
        "EMP-001",
        Map.of(),
        OPERATOR
    );

    assertThat(profile.id()).isEqualTo(USER_ID);
    assertThat(profile.channelType()).isEqualTo(ChannelType.INTERNET);
    assertThat(profile.email()).isEqualTo("user@example.com");
    assertThat(profile.phone()).isEqualTo("13800138000");
    assertThat(profile.organization()).isEqualTo("ACME Corp");
    assertThat(profile.position()).isEqualTo("HR Manager");
    assertThat(profile.branchId()).isNull();
    assertThat(profile.employeeNo()).isEqualTo("EMP-001");
    assertThat(profile.extraAttributes()).isEmpty();
    assertThat(profile.createdBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("重建构造器从数据库状态恢复完整档案")
  void reconstitute_restoresFullState() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 7, 26, 10, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 11, 0);
    Version version = Version.of(5);

    UserProfile profile = UserProfile.reconstitute(
        USER_ID, ChannelType.BRANCH,
        "teller@bank.com", "13900139000",
        "Bank HQ", "Teller", "BR-001", "EMP-002",
        Map.of("clearance", "level2"),
        OPERATOR, OPERATOR, createdAt, updatedAt, version
    );

    assertThat(profile.id()).isEqualTo(USER_ID);
    assertThat(profile.channelType()).isEqualTo(ChannelType.BRANCH);
    assertThat(profile.branchId()).isEqualTo("BR-001");
    assertThat(profile.extraAttributes()).containsEntry("clearance", "level2");
    assertThat(profile.version()).isEqualTo(version);
    assertThat(profile.createdAt()).isEqualTo(createdAt);
    assertThat(profile.updatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("update 修改档案字段并递增版本号")
  void update_modifiesFieldsAndIncrementsVersion() {
    UserProfile profile = createInternetProfile();
    Version initialVersion = profile.version();

    profile.update(
        "new@example.com",
        "13900139000",
        "ACME Corp",
        "Senior HR",
        null,
        "EMP-001",
        Map.of("clearance", "level3"),
        OPERATOR
    );

    assertThat(profile.email()).isEqualTo("new@example.com");
    assertThat(profile.phone()).isEqualTo("13900139000");
    assertThat(profile.position()).isEqualTo("Senior HR");
    assertThat(profile.extraAttributes()).containsEntry("clearance", "level3");
    assertThat(profile.version()).isEqualTo(initialVersion.next());
    assertThat(profile.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("create 拒绝空渠道类型")
  void create_rejectsNullChannelType() {
    assertThatThrownBy(() -> UserProfile.create(
        USER_ID, null, "user@example.com", "13800138000",
        null, null, null, null, Map.of(), OPERATOR
    )).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("create 拒绝网点渠道无 branchId")
  void create_rejectsBranchChannelWithoutBranchId() {
    assertThatThrownBy(() -> UserProfile.create(
        USER_ID, ChannelType.BRANCH, null, null,
        null, null, null, null, Map.of(), OPERATOR
    )).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("branchId");
  }

  @Test
  @DisplayName("extraAttributes 返回不可变副本")
  void extraAttributes_returnsImmutableCopy() {
    UserProfile profile = UserProfile.create(
        USER_ID, ChannelType.INTERNET, "u@e.com", "138",
        null, null, null, null,
        Map.of("k", "v"), OPERATOR
    );

    assertThatThrownBy(() -> profile.extraAttributes().put("new", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private UserProfile createInternetProfile() {
    return UserProfile.create(
        USER_ID, ChannelType.INTERNET,
        "user@example.com", "13800138000",
        "ACME Corp", "HR Manager",
        null, "EMP-001", Map.of(), OPERATOR
    );
  }
}
