package com.example.iam.api.dto;

/**
 * 登录结果DTO
 *
 * <p>登录接口返回的统一结果对象,承载登录成功/失败信息以及后续流程提示(如二次授权)。
 *
 * @author iam-service
 */
public record LoginResultDTO(
    /**
     * 是否登录成功
     */
    boolean success,
    /**
     * 登录成功时返回的令牌值(失败时为空)
     */
    String tokenValue,
    /**
     * 令牌名称(如 satoken-internet)
     */
    String tokenName,
    /**
     * 用户ID(失败且用户不存在时可为空)
     */
    Long userId,
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 失败原因描述(成功时为空)
     */
    String message,
    /**
     * 是否需要二次授权(仅网点渠道有意义)
     */
    boolean secondaryAuthRequired
) {
}
