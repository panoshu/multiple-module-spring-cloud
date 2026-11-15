package com.example.auth.api;

import com.example.auth.api.dto.ParentPlanResponse;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.query.GetParentPlanRequest;
import com.example.auth.api.query.ListPlanMembersRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批次 66：组织目录查询 API 契约。
 * 先编译红灯（5 个契约类尚不存在）→ 实现后转绿，锁定协议形状防退化。
 */
@DisplayName("组织目录查询 API 契约（批次 66）")
class PlanDirectoryApiContractTest {

  @Test
  @DisplayName("API 用 @HttpExchange 且路径为 /plan-directory")
  void api_usesHttpExchange() {
    HttpExchange exchange = PlanDirectoryApi.class.getAnnotation(HttpExchange.class);

    assertThat(exchange).isNotNull();
    assertThat(exchange.value()).isEqualTo("/plan-directory");
  }

  @Test
  @DisplayName("/members 用 @PostExchange + @RequestBody + @Valid，返回用户编号列表")
  void members_usesPostExchangeAndValidatedBody() throws Exception {
    Method method = PlanDirectoryApi.class.getMethod("listMembers", ListPlanMembersRequest.class);

    assertThat(method.getAnnotation(PostExchange.class).value()).isEqualTo("/members");
    assertThat(method.getParameters()[0].isAnnotationPresent(RequestBody.class)).isTrue();
    assertThat(method.getParameters()[0].isAnnotationPresent(Valid.class)).isTrue();
    ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
    assertThat(returnType.getActualTypeArguments()[0]).isEqualTo(PlanMembersResponse.class);
  }

  @Test
  @DisplayName("/parent 用 @PostExchange + @RequestBody + @Valid，返回父计划响应")
  void parent_usesPostExchangeAndValidatedBody() throws Exception {
    Method method = PlanDirectoryApi.class.getMethod("getParentPlan", GetParentPlanRequest.class);

    assertThat(method.getAnnotation(PostExchange.class).value()).isEqualTo("/parent");
    assertThat(method.getParameters()[0].isAnnotationPresent(RequestBody.class)).isTrue();
    assertThat(method.getParameters()[0].isAnnotationPresent(Valid.class)).isTrue();
    ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
    assertThat(returnType.getActualTypeArguments()[0]).isEqualTo(ParentPlanResponse.class);
  }

  @Test
  @DisplayName("ListPlanMembersRequest.planNo 必填，excludeUserNo 可选")
  void listMembersRequest_validation() throws Exception {
    assertThat(ListPlanMembersRequest.class.getDeclaredField("planNo")
      .isAnnotationPresent(NotBlank.class)).isTrue();
    assertThat(ListPlanMembersRequest.class.getDeclaredField("excludeUserNo")
      .isAnnotationPresent(NotBlank.class)).isFalse();
  }

  @Test
  @DisplayName("GetParentPlanRequest.planNo 必填")
  void getParentPlanRequest_validation() throws Exception {
    assertThat(GetParentPlanRequest.class.getDeclaredField("planNo")
      .isAnnotationPresent(NotBlank.class)).isTrue();
  }

  @Test
  @DisplayName("响应只携带编号列表与可空父计划号")
  void responses_holdOnlyIdentifiers() {
    PlanMembersResponse members = new PlanMembersResponse(List.of("U-1", "U-2"));
    ParentPlanResponse parent = new ParentPlanResponse("PLAN-PARENT");
    ParentPlanResponse root = new ParentPlanResponse(null);

    assertThat(members.userNos()).containsExactly("U-1", "U-2");
    assertThat(parent.parentPlanNo()).isEqualTo("PLAN-PARENT");
    assertThat(root.parentPlanNo()).isNull();
  }
}
