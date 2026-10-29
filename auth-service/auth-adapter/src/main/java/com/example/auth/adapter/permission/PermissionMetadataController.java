package com.example.auth.adapter.permission;

import com.example.auth.adapter.converter.PermissionMenuConverter;
import com.example.auth.adapter.converter.PermissionMetadataConverter;
import com.example.auth.api.PermissionMetadataApi;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.dto.*;
import com.example.auth.api.query.GetMenuNodeRequest;
import com.example.auth.api.query.ListMenuRequest;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.permission.PermissionMenuApplicationService;
import com.pension.permission.application.permission.PermissionMetadataApplicationService;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.aggregate.PermissionMenu;
import com.pension.permission.domain.permission.aggregate.valueobject.PermissionBinding;
import com.pension.permission.domain.permission.enumeration.MenuCategory;
import com.pension.permission.domain.permission.enumeration.MenuVisibility;
import com.pension.permission.domain.permission.errorcode.PermissionMenuError;
import com.pension.permission.types.MenuCode;
import com.pension.permission.types.PermissionMenuId;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限元数据与菜单管理 Controller.
 *
 * <p>实现 {@link PermissionMetadataApi}，供前端渲染权限项列表及管理菜单节点。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionMetadataController implements PermissionMetadataApi {

  private final PermissionMetadataApplicationService service;
  private final PermissionMetadataConverter converter;
  private final PermissionMenuApplicationService menuAppService;
  private final PermissionMenuConverter menuConverter;

  // ── 权限项查询 ──

  @Override
  @RequirePermission(business = "PERMISSION_METADATA", action = "VIEW", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
    com.pension.permission.domain.authorization.enumeration.PermissionCategory cat = resolveCategory(request.category());
    List<PermissionItem> items = service.listItems(cat);
    return ApiResult.success(converter.toResponseList(items));
  }

  @Override
  @RequirePermission(business = "PERMISSION_METADATA", action = "VIEW", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
    com.pension.permission.domain.authorization.enumeration.PermissionCategory cat = resolveCategory(request.category());
    Map<String, List<PermissionItem>> grouped = service.listGroupedItems(cat);
    return ApiResult.success(converter.toGroupedResponse(grouped));
  }

  // ── 菜单管理端 ──

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<List<MenuNodeResponse>> listMenus(ListMenuRequest request) {
    MenuCategory cat = resolveMenuCategory(request.category());
    List<PermissionMenu> menus = menuAppService.listMenus(cat);
    return ApiResult.success(menuConverter.toNodeResponseList(menus));
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<MenuNodeResponse> getMenu(GetMenuNodeRequest request) {
    PermissionMenuId menuId = new PermissionMenuId(request.menuId());
    PermissionMenu menu = menuAppService.getMenu(menuId)
      .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));
    return ApiResult.success(menuConverter.toNodeResponse(menu));
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<String> createMenu(CreateMenuCommand command) {
    UserNo operator = resolveCurrentUser();
    PermissionMenuId menuId = menuAppService.createMenu(
      new MenuCode(command.menuCode()),
      command.menuName(),
      menuConverter.toMenuId(command.parentId()),
      command.sortOrder(),
      command.icon(),
      command.route(),
      MenuCategory.valueOf(command.menuCategory()),
      MenuVisibility.valueOf(command.visibility()),
      operator
    );
    return ApiResult.success(menuId.value());
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<Void> updateMenu(UpdateMenuCommand command) {
    UserNo operator = resolveCurrentUser();
    menuAppService.updateMenu(
      new PermissionMenuId(command.menuId()),
      command.menuName(),
      command.icon(),
      command.route(),
      command.sortOrder(),
      operator
    );
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<Void> moveMenu(MoveMenuCommand command) {
    UserNo operator = resolveCurrentUser();
    menuAppService.moveMenu(
      new PermissionMenuId(command.menuId()),
      menuConverter.toMenuId(command.newParentId()),
      command.sortOrder(),
      operator
    );
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<Void> deleteMenu(DeleteMenuCommand command) {
    UserNo operator = resolveCurrentUser();
    menuAppService.deleteMenu(new PermissionMenuId(command.menuId()), operator);
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<Void> replaceBindings(ReplaceBindingsCommand command) {
    UserNo operator = resolveCurrentUser();
    List<PermissionBinding> bindings = menuConverter.toBindingList(command.bindings());
    menuAppService.replaceBindings(
      new PermissionMenuId(command.menuId()),
      bindings,
      operator
    );
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
  public ApiResult<Void> toggleMenu(ToggleMenuCommand command) {
    UserNo operator = resolveCurrentUser();
    menuAppService.toggleMenu(new PermissionMenuId(command.menuId()), operator);
    return ApiResult.success();
  }

  // ── 内部辅助 ──

  private com.pension.permission.domain.authorization.enumeration.PermissionCategory resolveCategory(String category) {
    return category != null && !category.isBlank()
      ? com.pension.permission.domain.authorization.enumeration.PermissionCategory.valueOf(category)
      : null;
  }

  private MenuCategory resolveMenuCategory(String category) {
    return category != null && !category.isBlank()
      ? MenuCategory.valueOf(category)
      : null;
  }

  private UserNo resolveCurrentUser() {
    String accountId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    return UserNo.of(accountId);
  }
}
