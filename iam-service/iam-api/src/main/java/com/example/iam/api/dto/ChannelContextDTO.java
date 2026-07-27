package com.example.iam.api.dto;

/**
 * 渠道上下文DTO
 *
 * <p>返回当前登录渠道的会话信息,包括用户身份、渠道类型、二次授权状态与当前选定计划。
 *
 * @author iam-service
 */
public record ChannelContextDTO(
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 用户ID
     */
    Long userId,
    /**
     * 登录名
     */
    String loginName,
    /**
     * 显示名称
     */
    String displayName,
    /**
     * 是否拥有生效的二次授权(仅 BRANCH 渠道有意义)
     */
    boolean hasSecondaryAuth,
    /**
     * 二次授权会话ID(可空)
     */
    Long secondaryAuthSessionId,
    /**
     * 当前选定计划编号(可空)
     */
    String currentPlanId
) {
}
