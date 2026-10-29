package com.example.auth.api;

import com.example.auth.api.dto.MenuTreeNodeResponse;
import com.example.shared.web.core.api.ApiResult;
import com.example.auth.api.query.GetMenuTreeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 权限菜单前端查询 API（只读）.
 *
 * @author auth-api
 */
@HttpExchange("/permission-menu")
public interface PermissionMenuApi {

  /**
   * 获取当前用户可见的业务菜单树（需传 planId）.
   */
  @PostExchange("/business-tree")
  ApiResult<List<MenuTreeNodeResponse>> getBusinessTree(@RequestBody @Valid GetMenuTreeRequest request);

  /**
   * 获取当前用户可见的平台菜单树.
   */
  @PostExchange("/platform-tree")
  ApiResult<List<MenuTreeNodeResponse>> getPlatformTree();
}
