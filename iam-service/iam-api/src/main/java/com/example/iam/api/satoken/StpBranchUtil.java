package com.example.iam.api.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 网点渠道 StpLogic 工具类
 *
 * <p>对应 BRANCH 渠道（合作银行的柜员），独立 token-name={@code iam-branch-token}，
 * 独立 Redis 命名空间，与 {@link StpInternetUtil}、{@link StpHqUtil} 完全隔离。</p>
 *
 * <p><b>身份切换能力</b>：网点柜员通过二次授权后，可临时切换为经办人身份办理业务，
 * 完成后通过 {@link #endSwitch()} 切换回柜员身份。相关方法：
 * <ul>
 *   <li>{@link #switchTo(Object)} - 切换为指定经办人身份</li>
 *   <li>{@link #endSwitch()} - 结束身份切换，恢复柜员身份</li>
 *   <li>{@link #isSwitch()} - 判断当前是否处于切换状态</li>
 *   <li>{@link #getSwitchLoginId()} - 获取当前切换的身份 ID</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public class StpBranchUtil {

    /**
     * 账号体系标识（对应 {@link StpLogic#getType()}）
     */
    public static final String TYPE = "branch";

    /**
     * StpLogic 实例（多账号体系隔离的核心）
     */
    public static StpLogic stpLogic = new StpLogic(TYPE);

    private StpBranchUtil() {
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

    /**
     * 临时切换为指定经办人身份
     *
     * <p>切换后，{@link #getLoginId()} 将返回切换后的经办人 ID，
     * 直到调用 {@link #endSwitch()} 恢复柜员身份</p>
     *
     * @param internetUserId 经办人 ID
     */
    public static void switchTo(Object internetUserId) {
        stpLogic.switchTo(internetUserId);
    }

    /**
     * 结束身份切换，恢复柜员身份
     */
    public static void endSwitch() {
        stpLogic.endSwitch();
    }

    /**
     * 判断当前是否处于身份切换状态
     *
     * @return true 表示当前处于经办人身份
     */
    public static boolean isSwitch() {
        return stpLogic.isSwitch();
    }

    /**
     * 获取当前切换的身份 ID
     *
     * @return 切换后的经办人 ID，未切换返回 null
     */
    public static Object getSwitchLoginId() {
        return stpLogic.getSwitchLoginId();
    }
}
