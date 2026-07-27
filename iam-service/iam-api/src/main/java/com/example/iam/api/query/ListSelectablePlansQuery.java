package com.example.iam.api.query;

import com.example.shared.web.core.dto.PageQuery;

/**
 * 查询当前用户可选计划
 *
 * <p>查询当前登录用户在所属渠道下可选的业务计划列表。
 * 可选计划范围由当前登录渠道决定,服务端从 sa-token 上下文获取当前用户信息。
 *
 * @author iam-service
 */
public record ListSelectablePlansQuery(
    /**
     * 关键字(可选,计划编号/名称模糊匹配)
     */
    String keyword,
    /**
     * 分页参数(可选,不传则返回全部)
     */
    PageQuery pageQuery
) {
}
