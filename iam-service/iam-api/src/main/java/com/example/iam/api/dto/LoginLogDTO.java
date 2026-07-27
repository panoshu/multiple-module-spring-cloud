package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志DTO
 *
 * <p>对应登录日志聚合根(LoginLog)的展示视图,审计每次登录尝试(成功/失败),
 * 失败时通过 failureRecords 列表记录具体原因。
 *
 * @author iam-service
 */
public record LoginLogDTO(
    /**
     * 日志ID
     */
    Long logId,
    /**
     * 用户ID(可空,用户不存在场景)
     */
    Long userId,
    /**
     * 登录名
     */
    String loginName,
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 是否登录成功
     */
    boolean success,
    /**
     * 登录时间
     */
    LocalDateTime loginTime,
    /**
     * 登录IP(可空)
     */
    String loginIp,
    /**
     * User-Agent(可空)
     */
    String userAgent,
    /**
     * 失败记录列表(成功日志为空列表)
     */
    List<LoginFailureRecordDTO> failureRecords
) {
}
