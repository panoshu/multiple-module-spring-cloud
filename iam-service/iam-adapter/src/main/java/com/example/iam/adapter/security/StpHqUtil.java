package com.example.iam.adapter.security;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLogoutParameter;

/**
 * 总部渠道 StpLogic 工具类(运营人员)。
 *
 * <p>对应 sa-token 多 StpLogic 集成的 HQ 渠道,loginType 为 {@code "hq"},
 * Token Header 名称为 {@code "satoken-hq"}。Token 互不干扰,便于独立管理。
 *
 * <p>本工具类为静态门面,所有操作委托给 {@link #stpLogic} 实例。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public final class StpHqUtil {

  /** loginType 标识(对应 StpLogic 构造参数) */
  public static final String TYPE = "hq";

  /** Token Header 名称(前后台分离场景) */
  public static final String TOKEN_NAME = "satoken-hq";

  /** StpLogic 实例(每渠道独立) */
  public static StpLogic stpLogic = new StpLogic(TYPE);

  private StpHqUtil() {
  }

  // ==================== 登录相关 ====================

  public static void login(Object id) {
    stpLogic.login(id);
  }

  public static void logout() {
    stpLogic.logout();
  }

  public static void logout(Object loginId) {
    stpLogic.logout(loginId, new SaLogoutParameter());
  }

  // ==================== 会话查询 ====================

  public static Object getLoginId() {
    return stpLogic.getLoginId();
  }

  public static Long getLoginIdAsLong() {
    return stpLogic.getLoginIdAsLong();
  }

  public static boolean isLogin() {
    return stpLogic.isLogin();
  }

  public static void checkLogin() {
    stpLogic.checkLogin();
  }

  // ==================== Token 与 Session ====================

  public static String getTokenValue() {
    return stpLogic.getTokenValue();
  }

  public static cn.dev33.satoken.session.SaSession getSession() {
    return stpLogic.getSession();
  }

  public static cn.dev33.satoken.session.SaSession getSessionByLoginId(Object loginId) {
    return stpLogic.getSessionByLoginId(loginId);
  }

  public static cn.dev33.satoken.session.SaSession getTokenSession() {
    return stpLogic.getTokenSession();
  }

  /**
   * 获取指定登录 ID 的 Token-Session。
   *
   * <p>sa-token 1.45.0 未直接提供 {@code getTokenSessionByLoginId} API,
   * 此处通过 {@code getTokenValueByLoginId} + {@code getTokenSessionByToken} 链式调用实现。
   * 仅返回最新活跃 Token 对应的 Token-Session(用户多端登录场景下)。
   *
   * @param loginId 登录 ID
   * @return Token-Session(用户未登录或无 Token 时返回 null)
   */
  public static cn.dev33.satoken.session.SaSession getTokenSessionByLoginId(Object loginId) {
    String token = stpLogic.getTokenValueByLoginId(loginId);
    if (token == null) {
      return null;
    }
    return stpLogic.getTokenSessionByToken(token);
  }

  // ==================== 权限校验 ====================

  public static java.util.List<String> getPermissionList() {
    return stpLogic.getPermissionList();
  }

  public static java.util.List<String> getPermissionList(Object loginId) {
    return stpLogic.getPermissionList(loginId);
  }

  public static void checkPermission(String permission) {
    stpLogic.checkPermission(permission);
  }

  public static boolean hasPermission(String permission) {
    return stpLogic.hasPermission(permission);
  }

  // ==================== 角色校验 ====================

  public static java.util.List<String> getRoleList() {
    return stpLogic.getRoleList();
  }

  public static void checkRole(String role) {
    stpLogic.checkRole(role);
  }

  // ==================== 踢人下线 ====================

  public static void kickout(Object loginId) {
    stpLogic.kickout(loginId);
  }
}
