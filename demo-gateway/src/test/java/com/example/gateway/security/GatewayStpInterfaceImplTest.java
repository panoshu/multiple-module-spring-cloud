package com.example.gateway.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayStpInterfaceImpl} 网关层 StpInterface 实现单元测试。
 *
 * <p>覆盖 {@link GatewayStpInterfaceImpl#getRoleList(Object, String)} 与
 * {@link GatewayStpInterfaceImpl#getPermissionList(Object, String)} 的可观察行为。
 *
 * <p>失败策略:任何异常返回空权限列表,sa-token 拒绝所有需要权限的操作,保证安全。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayStpInterfaceImpl 网关层 StpInterface 实现测试")
class GatewayStpInterfaceImplTest {

  @Mock
  private ChannelAwareSaRouter channelAwareSaRouter;

  @Mock
  private StpLogic stpLogic;

  @Mock
  private SaSession session;

  private GatewayStpInterfaceImpl stpInterface;

  @BeforeEach
  void setUp() {
    stpInterface = new GatewayStpInterfaceImpl(channelAwareSaRouter);
  }

  @Nested
  @DisplayName("getRoleList 获取角色列表")
  class GetRoleList {

    @Test
    @DisplayName("internet 返回 [operator]")
    void internetReturnsOperator() {
      assertThat(stpInterface.getRoleList("user-1", "internet"))
          .containsExactly("operator");
    }

    @Test
    @DisplayName("hq 返回 [staff]")
    void hqReturnsStaff() {
      assertThat(stpInterface.getRoleList("user-2", "hq"))
          .containsExactly("staff");
    }

    @Test
    @DisplayName("branch 返回 [teller]")
    void branchReturnsTeller() {
      assertThat(stpInterface.getRoleList("user-3", "branch"))
          .containsExactly("teller");
    }

    @Test
    @DisplayName("未知 loginType 返回空列表")
    void unknownLoginTypeReturnsEmpty() {
      assertThat(stpInterface.getRoleList("user-4", "unknown"))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("getPermissionList 获取权限列表")
  class GetPermissionList {

    @Test
    @DisplayName("未知 loginType 返回空列表")
    void unknownLoginTypeReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("unknown")).thenReturn(null);

      assertThat(stpInterface.getPermissionList("user-1", "unknown")).isEmpty();
    }

    @Test
    @DisplayName("Token-Session 不存在(getTokenValueByLoginId 返回 null)返回空列表")
    void tokenSessionNullReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("internet")).thenReturn(ChannelType.INTERNET);
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-1")).thenReturn(null);

      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
    }

    @Test
    @DisplayName("currentPlanId 为 null 返回空列表")
    void planIdNullReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("internet")).thenReturn(ChannelType.INTERNET);
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-1")).thenReturn("token-1");
      when(stpLogic.getTokenSessionByToken("token-1")).thenReturn(session);
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID)).thenReturn(null);

      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
    }

    @Test
    @DisplayName("currentPlanId 为空白字符串返回空列表")
    void planIdBlankReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("internet")).thenReturn(ChannelType.INTERNET);
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-1")).thenReturn("token-1");
      when(stpLogic.getTokenSessionByToken("token-1")).thenReturn(session);
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID)).thenReturn("   ");

      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
    }

    @Test
    @DisplayName("正常情况: currentPermissions 为 Set,返回其不可变副本")
    void normalReturnsPermissionCopy() {
      when(channelAwareSaRouter.getChannelByLoginType("internet")).thenReturn(ChannelType.INTERNET);
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-1")).thenReturn("token-1");
      when(stpLogic.getTokenSessionByToken("token-1")).thenReturn(session);
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID)).thenReturn("plan-1");
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS))
          .thenReturn(Set.of("biz:handle", "biz:query"));

      List<String> permissions = stpInterface.getPermissionList("user-1", "internet");

      assertThat(permissions).containsExactlyInAnyOrder("biz:handle", "biz:query");
    }

    @Test
    @DisplayName("currentPermissions 为 List 类型时正常适配为 Set 副本")
    void permissionsAsListAdaptedToSet() {
      when(channelAwareSaRouter.getChannelByLoginType("hq")).thenReturn(ChannelType.HQ);
      when(channelAwareSaRouter.getStpLogic(ChannelType.HQ)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-2")).thenReturn("token-2");
      when(stpLogic.getTokenSessionByToken("token-2")).thenReturn(session);
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID)).thenReturn("plan-2");
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS))
          .thenReturn(List.of("hq:admin", "hq:read"));

      List<String> permissions = stpInterface.getPermissionList("user-2", "hq");

      assertThat(permissions).containsExactlyInAnyOrder("hq:admin", "hq:read");
    }

    @Test
    @DisplayName("currentPermissions 为非 Set/List 类型返回空列表")
    void permissionsNonCollectionReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("internet")).thenReturn(ChannelType.INTERNET);
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-1")).thenReturn("token-1");
      when(stpLogic.getTokenSessionByToken("token-1")).thenReturn(session);
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID)).thenReturn("plan-1");
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS))
          .thenReturn("not-a-collection");

      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
    }

    @Test
    @DisplayName("currentPermissions 为 null 返回空列表")
    void permissionsNullReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("internet")).thenReturn(ChannelType.INTERNET);
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getTokenValueByLoginId("user-1")).thenReturn("token-1");
      when(stpLogic.getTokenSessionByToken("token-1")).thenReturn(session);
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID)).thenReturn("plan-1");
      when(session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PERMISSIONS)).thenReturn(null);

      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
    }

    @Test
    @DisplayName("异常情况: 捕获并返回空列表,保证安全")
    void exceptionReturnsEmpty() {
      when(channelAwareSaRouter.getChannelByLoginType("internet"))
          .thenThrow(new RuntimeException("redis down"));

      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
    }
  }
}
