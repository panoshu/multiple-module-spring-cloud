package com.example.iam.api.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 总部渠道 StpLogic 工具类
 *
 * <p>对应 HQ 渠道（本公司运营人员），独立 token-name={@code iam-hq-token}，
 * 独立 Redis 命名空间，与 {@link StpInternetUtil}、{@link StpBranchUtil} 完全隔离。</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public class StpHqUtil {

    /**
     * 账号体系标识（对应 {@link StpLogic#getType()}）
     */
    public static final String TYPE = "hq";

    /**
     * StpLogic 实例（多账号体系隔离的核心）
     */
    public static StpLogic stpLogic = new StpLogic(TYPE);

    private StpHqUtil() {
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
