package com.example.iam.application.service;

import com.example.iam.api.command.ClearCurrentPlanCommand;
import com.example.iam.api.command.SelectPlanCommand;
import com.example.iam.api.dto.PlanPermissionDTO;
import com.example.iam.api.dto.SelectablePlanDTO;
import com.example.iam.api.query.GetCurrentPlanQuery;
import com.example.iam.api.query.ListSelectablePlansQuery;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.domain.authorization.aggregate.valueobject.OperationMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.aggregate.valueobject.PlanMetadata;
import com.example.iam.domain.authorization.gateway.PlanMetadataGateway;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.types.UserId;
import com.example.shared.web.core.dto.PageData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlanSelectionAppService} 单元测试。
 *
 * <p>覆盖可选计划列表分页、选择计划并同步权限到会话、查询与清除当前计划等核心流程。
 *
 * @author iam-service
 */
@DisplayName("计划选择应用服务测试")
@ExtendWith(MockitoExtension.class)
class PlanSelectionAppServiceTest {

  private static final Long USER_ID = 6001L;
  private static final String USER_NO = "U6001";
  private static final String PLAN_ID = "PLAN001";
  private static final String CUSTOMER_NO = "CUST001";

  @Mock private ChannelSessionPort channelSessionPort;
  @Mock private PermissionResolver permissionResolver;
  @Mock private PlanMetadataGateway planMetadataGateway;

  @InjectMocks
  private PlanSelectionAppService planSelectionAppService;

  @Nested
  @DisplayName("listSelectablePlans 列出可选计划")
  class ListSelectablePlansTest {

    @Test
    @DisplayName("无关键字时返回当前用户客户下全部可选计划")
    void should_return_all_selectable_plans_when_no_keyword() {
      PlanMetadata plan1 = new PlanMetadata(
          "PLAN001", CUSTOMER_NO, "PROD001",
          OperationMode.SINGLE_TRUSTEE, "AM001");
      PlanMetadata plan2 = new PlanMetadata(
          "PLAN002", CUSTOMER_NO, "PROD002",
          OperationMode.SINGLE_ACCOUNT_MANAGER, "AM002");
      when(channelSessionPort.currentUserNo()).thenReturn(USER_NO);
      when(planMetadataGateway.findSelectablePlansByCustomer(USER_NO))
          .thenReturn(List.of(plan1, plan2));
      ListSelectablePlansQuery query = new ListSelectablePlansQuery(null, null);

      PageData<SelectablePlanDTO> page = planSelectionAppService.listSelectablePlans(query);

      assertThat(page.totalCount()).isEqualTo(2);
      assertThat(page.items()).hasSize(2);
      assertThat(page.items().get(0).planId()).isEqualTo("PLAN001");
      assertThat(page.items().get(1).planId()).isEqualTo("PLAN002");
      assertThat(page.items().get(0).operationMode())
          .isEqualTo(OperationMode.SINGLE_TRUSTEE.name());
    }

    @Test
    @DisplayName("按关键字过滤返回匹配计划编号的子集")
    void should_filter_plans_by_keyword() {
      PlanMetadata plan1 = new PlanMetadata(
          "PLAN001", CUSTOMER_NO, "PROD001",
          OperationMode.SINGLE_TRUSTEE, "AM001");
      PlanMetadata plan2 = new PlanMetadata(
          "PLAN002", CUSTOMER_NO, "PROD002",
          OperationMode.SINGLE_ACCOUNT_MANAGER, "AM002");
      when(channelSessionPort.currentUserNo()).thenReturn(USER_NO);
      when(planMetadataGateway.findSelectablePlansByCustomer(USER_NO))
          .thenReturn(List.of(plan1, plan2));
      ListSelectablePlansQuery query = new ListSelectablePlansQuery("PLAN002", null);

      PageData<SelectablePlanDTO> page = planSelectionAppService.listSelectablePlans(query);

      assertThat(page.totalCount()).isEqualTo(1);
      assertThat(page.items()).hasSize(1);
      assertThat(page.items().get(0).planId()).isEqualTo("PLAN002");
    }
  }

  @Nested
  @DisplayName("selectPlan 选择当前计划")
  class SelectPlanTest {

    @Test
    @DisplayName("选择计划成功:解析权限快照并同步到渠道会话")
    void should_resolve_permissions_and_sync_to_session() {
      SelectPlanCommand command = new SelectPlanCommand(PLAN_ID, CUSTOMER_NO);
      PermissionSnapshot snapshot = new PermissionSnapshot(
          UserId.of(USER_ID), PLAN_ID,
          Set.of(PermissionCode.of("BIZ_A.HANDLE"),
              PermissionCode.of("BIZ_B.QUERY")),
          LocalDateTime.now());
      when(channelSessionPort.currentUserId()).thenReturn(USER_ID);
      when(permissionResolver.resolve(UserId.of(USER_ID), PLAN_ID))
          .thenReturn(snapshot);

      PlanPermissionDTO dto = planSelectionAppService.selectPlan(command);

      assertThat(dto.planId()).isEqualTo(PLAN_ID);
      assertThat(dto.customerNo()).isEqualTo(CUSTOMER_NO);
      assertThat(dto.permissions())
          .containsExactlyInAnyOrder("BIZ_A.HANDLE", "BIZ_B.QUERY");
      verify(channelSessionPort).setCurrentPlan(
          eq(PLAN_ID),
          eq(Set.of("BIZ_A.HANDLE", "BIZ_B.QUERY")));
    }
  }

  @Nested
  @DisplayName("getCurrentPlan 查询当前计划")
  class GetCurrentPlanTest {

    @Test
    @DisplayName("未选计划时返回空 DTO")
    void should_return_empty_dto_when_no_plan_selected() {
      when(channelSessionPort.getCurrentPlanId()).thenReturn(null);

      PlanPermissionDTO dto = planSelectionAppService.getCurrentPlan(new GetCurrentPlanQuery());

      assertThat(dto.planId()).isNull();
      assertThat(dto.permissions()).isEmpty();
    }

    @Test
    @DisplayName("已选计划时返回当前计划 ID 与会话中的权限集合")
    void should_return_current_plan_when_selected() {
      when(channelSessionPort.getCurrentPlanId()).thenReturn(PLAN_ID);
      when(channelSessionPort.getCurrentPermissions())
          .thenReturn(Set.of("BIZ_A.HANDLE"));

      PlanPermissionDTO dto = planSelectionAppService.getCurrentPlan(new GetCurrentPlanQuery());

      assertThat(dto.planId()).isEqualTo(PLAN_ID);
      assertThat(dto.permissions()).containsExactly("BIZ_A.HANDLE");
    }
  }

  @Nested
  @DisplayName("clearCurrentPlan 清除当前计划")
  class ClearCurrentPlanTest {

    @Test
    @DisplayName("清除当前计划:委托渠道会话清除")
    void should_delegate_clear_to_session() {
      planSelectionAppService.clearCurrentPlan(new ClearCurrentPlanCommand());

      verify(channelSessionPort).clearCurrentPlan();
    }
  }
}
