package com.example.auth.api;

import com.example.auth.api.dto.*;
import com.example.auth.api.query.GetMenuNodeRequest;
import com.example.auth.api.query.ListMenuRequest;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 权限元数据与菜单管理 API.
 * <p>包含权限项查询和菜单节点管理（CRUD、绑定、移动、启停）。
 *
 * @author auth-api
 */
@HttpExchange("/permission-metadata")
public interface PermissionMetadataApi {

  @PostExchange("/items")
  ApiResult<List<PermissionItemResponse>> listItems(@RequestBody @Valid ListPermissionItemsRequest request);

  @PostExchange("/items/grouped")
  ApiResult<List<PermissionGroupResponse>> listGroupedItems(@RequestBody @Valid ListPermissionItemsRequest request);

  // ── 菜单管理端 ──

  /** 列出所有菜单节点（扁平，含绑定，管理端表格用） */
  @PostExchange("/menus/list")
  ApiResult<List<MenuNodeResponse>> listMenus(@RequestBody @Valid ListMenuRequest request);

  /** 获取单个菜单节点详情 */
  @PostExchange("/menus/get")
  ApiResult<MenuNodeResponse> getMenu(@RequestBody @Valid GetMenuNodeRequest request);

  /** 创建菜单节点 */
  @PostExchange("/menus/create")
  ApiResult<String> createMenu(@RequestBody @Valid CreateMenuCommand command);

  /** 更新菜单显示信息 */
  @PostExchange("/menus/update")
  ApiResult<Void> updateMenu(@RequestBody @Valid UpdateMenuCommand command);

  /** 移动菜单节点 */
  @PostExchange("/menus/move")
  ApiResult<Void> moveMenu(@RequestBody @Valid MoveMenuCommand command);

  /** 删除菜单节点（需无子节点） */
  @PostExchange("/menus/delete")
  ApiResult<Void> deleteMenu(@RequestBody @Valid DeleteMenuCommand command);

  /** 替换菜单的权限绑定（全量替换） */
  @PostExchange("/menus/bindings/replace")
  ApiResult<Void> replaceBindings(@RequestBody @Valid ReplaceBindingsCommand command);

  /** 切换菜单启停 */
  @PostExchange("/menus/toggle")
  ApiResult<Void> toggleMenu(@RequestBody @Valid ToggleMenuCommand command);
}
