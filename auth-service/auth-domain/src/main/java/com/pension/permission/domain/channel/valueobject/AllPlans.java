package com.pension.permission.domain.channel.valueobject;


/**
 * 总部渠道用：不需要也不应该把全部计划枚举出来，选择时直接按ID/搜索定位
 */
public record AllPlans() implements SelectablePlanScope {
}
