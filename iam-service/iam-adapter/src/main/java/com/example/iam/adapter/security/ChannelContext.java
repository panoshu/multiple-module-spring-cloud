package com.example.iam.adapter.security;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;

/**
 * 渠道上下文 - 贯穿网关与应用层的渠道抽象。
 *
 * <p>封装当前请求的渠道信息与 sa-token 会话状态,提供渠道分派的权限/角色校验方法。
 * 由 {@link ChannelContextProvider} 根据当前请求的 Token Header 识别渠道后构建。
 *
 * <p>所有权限/角色校验通过 {@link #checkPermission} 与 {@link #checkRole} 分派到
 * 对应渠道的 StpLogic,避免使用默认 {@code StpUtil} 导致渠道混淆。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record ChannelContext(
    ChannelType channelType,
    Long userId,
    boolean hasSecondaryAuth,
    Long secondaryAuthSessionId,
    String currentPlanId
) {

  /**
   * 渠道分派的权限校验。
   *
   * @param permission 权限码(如 ANNUITY_ESTABLISH.HANDLE)
   */
  public void checkPermission(String permission) {
    switch (channelType) {
      case INTERNET -> StpInternetUtil.checkPermission(permission);
      case HQ -> StpHqUtil.checkPermission(permission);
      case BRANCH -> StpBranchUtil.checkPermission(permission);
    }
  }

  /**
   * 渠道分派的角色校验。
   *
   * @param role 角色名
   */
  public void checkRole(String role) {
    switch (channelType) {
      case INTERNET -> StpInternetUtil.checkRole(role);
      case HQ -> StpHqUtil.checkRole(role);
      case BRANCH -> StpBranchUtil.checkRole(role);
    }
  }

  /**
   * 渠道分派的权限包含判断(不抛异常)。
   *
   * @param permission 权限码
   * @return true 表示拥有该权限
   */
  public boolean hasPermission(String permission) {
    return switch (channelType) {
      case INTERNET -> StpInternetUtil.hasPermission(permission);
      case HQ -> StpHqUtil.hasPermission(permission);
      case BRANCH -> StpBranchUtil.hasPermission(permission);
    };
  }

  /**
   * 获取当前渠道的 Token 值。
   *
   * @return Token 值(未登录时为 null)
   */
  public String tokenValue() {
    return switch (channelType) {
      case INTERNET -> StpInternetUtil.getTokenValue();
      case HQ -> StpHqUtil.getTokenValue();
      case BRANCH -> StpBranchUtil.getTokenValue();
    };
  }

  /**
   * Token Header 名称(用于服务间调用透传)。
   *
   * @return Header 名称(如 satoken-internet)
   */
  public String tokenName() {
    return "satoken-" + channelType.name().toLowerCase();
  }
}
