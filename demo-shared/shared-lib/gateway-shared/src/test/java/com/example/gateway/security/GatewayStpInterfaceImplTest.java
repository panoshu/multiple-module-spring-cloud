package com.example.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GatewayStpInterfaceImpl} 网关层 StpInterface 实现单元测试。
 *
 * <p>网关层不做细粒度权限校验，{@link GatewayStpInterfaceImpl#getPermissionList(Object, String)}
 * 始终返回空列表，业务服务通过 {@code @RequirePermission} 注解实时调用 auth-service 校验。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayStpInterfaceImpl 网关层 StpInterface 实现测试")
class GatewayStpInterfaceImplTest {

  @Mock
  private ChannelAwareSaRouter channelAwareSaRouter;

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
    @DisplayName("网关层不校验权限，始终返回空列表")
    void alwaysReturnsEmpty() {
      assertThat(stpInterface.getPermissionList("user-1", "internet")).isEmpty();
      assertThat(stpInterface.getPermissionList("user-2", "hq")).isEmpty();
      assertThat(stpInterface.getPermissionList("user-3", "branch")).isEmpty();
      assertThat(stpInterface.getPermissionList("user-4", "unknown")).isEmpty();
    }
  }
}