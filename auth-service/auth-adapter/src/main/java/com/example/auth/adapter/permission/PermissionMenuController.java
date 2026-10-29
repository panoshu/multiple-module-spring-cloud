package com.example.auth.adapter.permission;

import com.example.auth.adapter.converter.PermissionMenuConverter;
import com.example.auth.api.PermissionMenuApi;
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.dto.MenuTreeNodeResponse;
import com.example.auth.api.query.GetMenuTreeRequest;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.permission.PermissionMenuApplicationService;
import com.pension.permission.domain.permission.aggregate.valueobject.MenuTreeNode;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限菜单前端查询 Controller.
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionMenuController implements PermissionMenuApi {

  private final PermissionMenuApplicationService menuAppService;
  private final PermissionMenuConverter menuConverter;

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<MenuTreeNodeResponse>> getBusinessTree(GetMenuTreeRequest request) {
    // accountId 从会话上下文获取（由 shared-web-starter 的 SessionContextResolver 注入）
    UserNo identity = resolveCurrentUser();
    PlanNo planId = PlanNo.of(request.planId());
    List<MenuTreeNode> tree = menuAppService.getBusinessTree(identity, planId);
    return ApiResult.success(menuConverter.toTreeNodeResponseList(tree));
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<MenuTreeNodeResponse>> getPlatformTree() {
    // accountId 从会话上下文获取（由 shared-web-starter 的 SessionContextResolver 注入）
    UserNo identity = resolveCurrentUser();
    List<MenuTreeNode> tree = menuAppService.getPlatformTree(identity);
    return ApiResult.success(menuConverter.toTreeNodeResponseList(tree));
  }

  private UserNo resolveCurrentUser() {
    // 通过 Sa-Token 获取当前登录账号ID
    String accountId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    return UserNo.of(accountId);
  }
}
