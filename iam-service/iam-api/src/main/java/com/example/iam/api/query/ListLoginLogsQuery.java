package com.example.iam.api.query;

import com.example.shared.web.core.dto.PageQuery;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 登录日志查询
 *
 * <p>支持按用户、登录名、渠道、时间范围、登录结果等条件分页查询登录日志。
 *
 * @author iam-service
 */
public record ListLoginLogsQuery(
    /**
     * 用户 ID(可选)
     */
    Long userId,
    /**
     * 登录名(可选,模糊匹配)
     */
    String loginName,
    /**
     * 渠道类型(可选,如 INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 起始时间(可选,包含)
     */
    LocalDateTime startTime,
    /**
     * 结束时间(可选,包含)
     */
    LocalDateTime endTime,
    /**
     * 是否登录成功(可选,true=成功,false=失败)
     */
    Boolean success,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
