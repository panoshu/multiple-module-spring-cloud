package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;

import java.time.LocalDateTime;

/**
 * 登录日志 Repository 接口（流水类记录，仅支持追加与统计）
 *
 * <p>流水类数据由应用层管理时间戳，不通过 DO/Converter 自动填充</p>
 */
public interface LoginLogRepository {

    /**
     * 追加一条登录日志
     *
     * @param userType    用户类型（INTERNET_USER / HQ_USER / BRANCH_USER）
     * @param userId      用户 ID（登录失败时可能为 null）
     * @param loginName   登录名
     * @param channel     渠道类型
     * @param loginResult 登录结果（SUCCESS / FAILURE）
     * @param failReason  失败原因（成功时为 null）
     * @param loginTime   登录时间
     * @param ipAddress   IP 地址
     * @param userAgent   User-Agent
     * @param createdBy   创建人
     */
    void append(String userType, Long userId, String loginName, ChannelType channel,
                String loginResult, String failReason,
                LocalDateTime loginTime, String ipAddress, String userAgent,
                String createdBy);

    /**
     * 统计指定登录名在指定时间之后的失败次数
     *
     * @param loginName 登录名
     * @param channel   渠道类型
     * @param since     起始时间
     * @return 失败次数
     */
    long countFailuresSince(String loginName, ChannelType channel, LocalDateTime since);
}
