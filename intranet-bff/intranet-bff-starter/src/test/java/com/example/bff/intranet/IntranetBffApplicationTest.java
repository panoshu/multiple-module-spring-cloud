package com.example.bff.intranet;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.auth.api.CustomerChannelEntitlementApi;
import com.example.auth.api.PermissionCacheApi;
import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.PermissionMetadataApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内网/专线 BFF 启动模块集成测试
 *
 * <p>外部 @HttpExchange API 客户端通过 {@link MockitoBean} 注入空实现，避免真实 HTTP 调用。
 *
 * @author bff
 */
@SpringBootTest
class IntranetBffApplicationTest {

  @Autowired
  private ApplicationContext applicationContext;

  // 外部 @HttpExchange API 的 mock 实现，避免真实 HTTP 调用
  @MockitoBean
  private ApprovalFlowApi approvalFlowApi;
  @MockitoBean
  private ApprovalInstanceApi approvalInstanceApi;
  @MockitoBean
  private PermissionCheckApi permissionCheckApi;
  @MockitoBean
  private PermissionMetadataApi permissionMetadataApi;
  @MockitoBean
  private PermissionCacheApi permissionCacheApi;
  @MockitoBean
  private CustomerChannelEntitlementApi customerChannelEntitlementApi;

  @Test
  @DisplayName("应用上下文加载成功")
  void contextLoads() {
    assertNotNull(applicationContext);
  }

  @Test
  @DisplayName("BffBusinessController 已注册")
  void bffBusinessControllerRegistered() {
    assertTrue(applicationContext.containsBean("bffBusinessController"));
  }

  @Test
  @DisplayName("BffApprovalFlowController 已注册")
  void bffApprovalFlowControllerRegistered() {
    assertTrue(applicationContext.containsBean("bffApprovalFlowController"));
  }

  @Test
  @DisplayName("BffApprovalInstanceController 已注册")
  void bffApprovalInstanceControllerRegistered() {
    assertTrue(applicationContext.containsBean("bffApprovalInstanceController"));
  }

  @Test
  @DisplayName("BffPermissionController 已注册")
  void bffPermissionControllerRegistered() {
    assertTrue(applicationContext.containsBean("bffPermissionController"));
  }

  @Test
  @DisplayName("BffChannelController 已注册")
  void bffChannelControllerRegistered() {
    assertTrue(applicationContext.containsBean("bffChannelController"));
  }

  @Test
  @DisplayName("BffSystemController 已注册")
  void bffSystemControllerRegistered() {
    assertTrue(applicationContext.containsBean("bffSystemController"));
  }
}
