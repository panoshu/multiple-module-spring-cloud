package com.example.iam.api.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 网上渠道 StpLogic 工具类
 *
 * <p>对应 INTERNET 渠道（客户企业的 HR 经办人），独立 token-name={@code iam-internet-token}，
 * 独立 Redis 命名空间，与 {@link StpHqUtil}、{@link StpBranchUtil} 完全隔离。</p>
 *
 * <p>使用方式：业务代码通过本工具类进行网上渠道的登录、登出、权限校验等操作，
 * 不直接依赖 sa-token 原生 {@code StpUtil}。</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public class StpInternetUtil {

    /**
     * 账号体系标识（对应 {@link StpLogic#getType()}）
     */
    public static final String TYPE = "internet";

    /**
     * StpLogic 实例（多账号体系隔离的核心）
     */
    public static StpLogic stpLogic = new StpLogic(TYPE);

    private StpInternetUtil() {
    }

    public static void login(Object id) {
        stpLogic.login(id);
    }

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    public static boolean isLogin() {
        return stpLogic.isLogin();
    }

    public static Object getLoginId() {
        return stpLogic.getLoginId();
    }

    public static Long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    public static String getLoginIdAsString() {
        return stpLogic.getLoginIdAsString();
    }

    public static void logout() {
        stpLogic.logout();
    }

    public static SaSession getSession() {
        return stpLogic.getSession();
    }

    public static SaSession getTokenSession() {
        return stpLogic.getTokenSession();
    }

    public static SaTokenInfo getTokenInfo() {
        return stpLogic.getTokenInfo();
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }

    public static String getTokenName() {
        return stpLogic.getTokenName();
    }

    public static void checkPermission(String permission) {
        stpLogic.checkPermission(permission);
    }

    public static boolean hasPermission(String permission) {
        return stpLogic.hasPermission(permission);
    }

    public static void checkRole(String role) {
        stpLogic.checkRole(role);
    }

    public static boolean hasRole(String role) {
        return stpLogic.hasRole(role);
    }
}
