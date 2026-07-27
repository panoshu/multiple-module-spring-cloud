package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLogoutParameter;

/**
 * 网点渠道 StpLogic 工具类(银行柜员)。
 *
 * <p>对应 sa-token 多 StpLogic 集成的 BRANCH 渠道,loginType 为 {@code "branch"},
 * Token Header 名称为 {@code "satoken-branch"}。Token 互不干扰,便于独立管理。
 *
 * <p>除常规 StpLogic 能力外,本工具类额外封装网点渠道专属的二次授权会话查询方法:
 * <ul>
 *   <li>{@link #hasSecondaryAuth()} - 柜员是否已完成二次授权</li>
 *   <li>{@link #getSecondaryAuthSessionId()} - 当前二次授权会话 ID</li>
 *   <li>{@link #getBorrowedApproverId()} - 借用的经办人 ID</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public final class StpBranchUtil {

  /** loginType 标识(对应 StpLogic 构造参数) */
  public static final String TYPE = "branch";

  /** Token Header 名称(前后台分离场景) */
  public static final String TOKEN_NAME = "satoken-branch";

  /** Token-Session 中存储二次授权会话 ID 的键 */
  public static final String SESSION_KEY_SECONDARY_AUTH_ID = "secondaryAuthSessionId";

  /** Token-Session 中存储借用经办人 ID 的键 */
  public static final String SESSION_KEY_BORROWED_APPROVER_ID = "borrowedApproverId";

  /** StpLogic 实例(每渠道独立) */
  public static StpLogic stpLogic = new StpLogic(TYPE);

  private StpBranchUtil() {
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

  public static SaSession getSession() {
    return stpLogic.getSession();
  }

  public static SaSession getSessionByLoginId(Object loginId) {
    return stpLogic.getSessionByLoginId(loginId);
  }

  public static SaSession getTokenSession() {
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
  public static SaSession getTokenSessionByLoginId(Object loginId) {
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

  // ==================== 网点渠道专属:二次授权会话查询 ====================

  /**
   * 当前柜员是否已完成二次授权。
   *
   * @return true 表示已授权,可借用经办人权限办理业务
   */
  public static boolean hasSecondaryAuth() {
    if (!isLogin()) {
      return false;
    }
    return getTokenSession().get(SESSION_KEY_SECONDARY_AUTH_ID) != null;
  }

  /**
   * 获取当前二次授权会话 ID。
   *
   * @return 会话 ID(未授权时返回 null)
   */
  public static Long getSecondaryAuthSessionId() {
    if (!isLogin()) {
      return null;
    }
    Object value = getTokenSession().get(SESSION_KEY_SECONDARY_AUTH_ID);
    if (value == null) {
      return null;
    }
    if (value instanceof Number num) {
      return num.longValue();
    }
    return Long.parseLong(value.toString());
  }

  /**
   * 获取当前借用的经办人 ID。
   *
   * @return 经办人 ID(未授权时返回 null)
   */
  public static Long getBorrowedApproverId() {
    if (!isLogin()) {
      return null;
    }
    Object value = getTokenSession().get(SESSION_KEY_BORROWED_APPROVER_ID);
    if (value == null) {
      return null;
    }
    if (value instanceof Number num) {
      return num.longValue();
    }
    return Long.parseLong(value.toString());
  }
}
