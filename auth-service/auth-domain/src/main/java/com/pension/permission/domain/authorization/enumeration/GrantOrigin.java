package com.pension.permission.domain.authorization.enumeration;

public enum GrantOrigin {
  HQ_CONFIG,         // 总部按客户/产品/计划维度配置
  PLAN_DELEGATE,     // 计划与计划之间的代办关系
  CUSTOMER_TO_AGENT, // 企业主动委托给经办人(可能是外部企业的经办)，网上渠道自助发起
  ROLE_TEMPLATE      // 由角色模板在身份分配创建/变更时自动生成
}
