package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;

/**
 * 数据可见范围解析器抽象接口.
 *
 * <p>业务服务通过 HttpExchange 调用 auth-service（{@code DefaultDataScopeResolver}），
 * auth-service 提供本地短路实现（{@code LocalDataScopeResolver}）避免循环调用。
 *
 * @author shared-permission-starter
 */
public interface DataScopeResolver {

  /**
   * 解析当前用户的可见数据范围。
   *
   * @param business 业务编码
   * @return 可见范围，失败返回 {@link DataScope#empty()}（fail-closed）
   */
  DataScope resolve(String business);
}
