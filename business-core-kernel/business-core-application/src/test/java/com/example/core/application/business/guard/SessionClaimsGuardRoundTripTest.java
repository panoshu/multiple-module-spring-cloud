package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.security.SessionClaims;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话声明 JSON 往返 + Guard 校验闭环测试（Phase B 信任链验证）。
 *
 * <p>验证链路：auth SessionClaimsWriter 写入 Token-Session 的声明 JSON
 * → 网关整体透传（Base64 前）→ kernel SessionContextResolver 反序列化
 * → DefaultBusinessAccessGuard 渠道差异化校验。
 * 本测试固化其中两端：JSON 序列化/反序列化往返保真 + Guard 对反序列化声明的判定。
 *
 * @author business-core-kernel
 */
@DisplayName("会话声明 JSON 往返 + Guard 校验闭环")
class SessionClaimsGuardRoundTripTest {

  private DefaultBusinessAccessGuard guard;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    guard = new DefaultBusinessAccessGuard();
    objectMapper = new ObjectMapper();
  }

  private SessionClaims claims(String channelType, boolean isProxy, boolean hasSecondaryAuth) {
    return new SessionClaims(
      "U001", "USER", "alice", "Alice",
      channelType, "CLI001", "127.0.0.1",
      "C001", "Customer A",
      "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
      isProxy, isProxy ? "U002" : null, isProxy ? "bob" : null,
      hasSecondaryAuth, hasSecondaryAuth ? 100L : null, hasSecondaryAuth ? "U003" : null,
      Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of("P001"), 0L
    );
  }

  private BusinessMetaContext meta() {
    return new BusinessMetaContext("ANNUITY_OPEN", "P001", "C001", "Customer A",
      "PRD001", "Product A", "Plan A", "MODEL_A", "CJP");
  }

  /**
   * JSON 往返保真：writer 写入的 JSON 经网关透传后，resolver 反序列化得到的声明字段无损
   * （含 INTERNET 代办与 BRANCH 二次授权字段）.
   */
  @Test
  @DisplayName("声明 JSON 序列化往返字段无损")
  void json_round_trip_preserves_all_fields() throws Exception {
    SessionClaims original = claims("BRANCH", false, true);
    String json = objectMapper.writeValueAsString(original);
    SessionClaims restored = objectMapper.readValue(json, SessionClaims.class);

    assertThat(restored.userNo()).isEqualTo("U001");
    assertThat(restored.channelType()).isEqualTo("BRANCH");
    assertThat(restored.planNo()).isEqualTo("P001");
    assertThat(restored.isProxy()).isFalse();
    assertThat(restored.hasSecondaryAuth()).isTrue();
    assertThat(restored.secondaryAuthSessionId()).isEqualTo(100L);
    assertThat(restored.borrowedApproverId()).isEqualTo("U003");
    assertThat(restored.permissionCodes()).containsExactly("BUSINESS_ANNUITY_OPEN_HANDLE");
    assertThat(restored.delegatedPlanNos()).containsExactly("P001");
  }

  /**
   * INTERNET 渠道：代办且 planNo 在委托范围内 → 放行.
   */
  @Test
  @DisplayName("INTERNET 代办 + 委托范围内 → 放行")
  void internet_proxy_within_delegated_passes() throws Exception {
    SessionClaims session = roundTrip(claims("INTERNET", true, false));
    assertThatCode(() -> guard.checkCanHandle(session, meta()))
      .doesNotThrowAnyException();
  }

  /**
   * INTERNET 渠道：代办但 planNo 不在委托范围 → 拒绝.
   */
  @Test
  @DisplayName("INTERNET 代办 + 超出委托范围 → 拒绝")
  void internet_proxy_outside_delegated_rejected() {
    SessionClaims session = claims("INTERNET", true, false);
    BusinessMetaContext otherPlan = new BusinessMetaContext("ANNUITY_OPEN", "P999", "C001",
      "Customer A", "PRD001", "Product A", "Plan A", "MODEL_A", "CJP");
    assertThatThrownBy(() -> guard.checkCanHandle(session, otherPlan))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining("请求参数"); // 公共校验先拦截（planNo 不一致）
  }

  /**
   * BRANCH 渠道：未二次授权 → 拒绝（渠道差异化防线真正生效）.
   */
  @Test
  @DisplayName("BRANCH 未二次授权 → 拒绝")
  void branch_without_secondary_auth_rejected() throws Exception {
    SessionClaims session = roundTrip(claims("BRANCH", false, false));
    assertThatThrownBy(() -> guard.checkCanHandle(session, meta()))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining("无权限");
  }

  /**
   * BRANCH 渠道：已二次授权 → 放行.
   */
  @Test
  @DisplayName("BRANCH 已二次授权 → 放行")
  void branch_with_secondary_auth_passes() throws Exception {
    SessionClaims session = roundTrip(claims("BRANCH", false, true));
    assertThatCode(() -> guard.checkCanHandle(session, meta()))
      .doesNotThrowAnyException();
  }

  /**
   * HQ 渠道：无额外校验 → 放行.
   */
  @Test
  @DisplayName("HQ 渠道 → 无额外校验放行")
  void hq_channel_passes() throws Exception {
    SessionClaims session = roundTrip(claims("HQ", false, false));
    assertThatCode(() -> guard.checkCanHandle(session, meta()))
      .doesNotThrowAnyException();
  }

  /**
   * 渠道类型词表统一性：声明 channelType 必须是 ChannelType 枚举名（大写），
   * 小写 loginType 会落入 Guard 的 default 分支被拒绝.
   */
  @Test
  @DisplayName("channelType 必须为枚举名（大写），小写 loginType 被拒")
  void channel_type_must_be_enum_name() throws Exception {
    SessionClaims lowercase = roundTrip(claims("internet", false, false));
    assertThatThrownBy(() -> guard.checkCanHandle(lowercase, meta()))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining("请求参数");
  }

  private SessionClaims roundTrip(SessionClaims claims) throws Exception {
    String json = objectMapper.writeValueAsString(claims);
    return objectMapper.readValue(json, SessionClaims.class);
  }
}
