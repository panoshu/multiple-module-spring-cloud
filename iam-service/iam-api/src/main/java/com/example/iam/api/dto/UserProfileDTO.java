package com.example.iam.api.dto;

import java.util.Map;

/**
 * 用户档案DTO
 *
 * <p>对应用户渠道专属档案实体(UserProfile)的展示视图,承载渠道差异化字段。
 *
 * @author iam-service
 */
public record UserProfileDTO(
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 邮箱(网上渠道常用)
     */
    String email,
    /**
     * 手机号(网上渠道常用)
     */
    String phone,
    /**
     * 所属组织(总部渠道常用)
     */
    String organization,
    /**
     * 职位(总部/网点渠道常用)
     */
    String position,
    /**
     * 网点编号(网点渠道必填)
     */
    String branchId,
    /**
     * 员工编号
     */
    String employeeNo,
    /**
     * 扩展属性(渠道特有字段,如网点柜员的clearance)
     */
    Map<String, String> extraAttributes
) {
}
