package com.example.iam.api.query;

import com.example.shared.web.core.dto.PageQuery;

/**
 * 业务定义列表查询
 *
 * <p>按业务编码、业务名称(模糊)、启用状态等条件分页查询业务定义列表。
 * 不传分页参数时返回全部业务定义。
 *
 * @author iam-service
 */
public record ListBusinessDefinitionsQuery(
    /**
     * 业务编码(可空,精确匹配)
     */
    String businessCode,
    /**
     * 业务名称(可空,模糊匹配)
     */
    String businessName,
    /**
     * 是否启用(可空,true=仅启用,false=仅禁用,不传=全部)
     */
    Boolean active,
    /**
     * 分页参数(可空,不传则返回全部)
     */
    PageQuery pageQuery
) {
}
