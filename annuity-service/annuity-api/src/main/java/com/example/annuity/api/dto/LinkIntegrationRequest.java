package com.example.annuity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 跨服务调用外接集成服务请求
 *
 * @param channel              渠道
 * @param tellerNo             柜员号
 * @param tellerName           柜员姓名
 * @param enterpriseCustomerNo 企业客户号
 * @param enterprisePlanNo     企业计划号
 * @param annuityProductNo     年金产品编号
 * @author annuity-service
 */
public record LinkIntegrationRequest(
    @NotBlank(message = "渠道不能为空")
    String channel,

    @NotBlank(message = "柜员号不能为空")
    String tellerNo,

    @NotBlank(message = "柜员姓名不能为空")
    String tellerName,

    @NotBlank(message = "企业客户号不能为空")
    @Size(max = 10, message = "企业客户号长度不能超过10")
    String enterpriseCustomerNo,

    @NotBlank(message = "企业计划号不能为空")
    @Size(max = 10, message = "企业计划号长度不能超过10")
    String enterprisePlanNo,

    @NotBlank(message = "年金产品编号不能为空")
    @Size(max = 6, message = "年金产品编号长度不能超过6")
    String annuityProductNo
) {
}
