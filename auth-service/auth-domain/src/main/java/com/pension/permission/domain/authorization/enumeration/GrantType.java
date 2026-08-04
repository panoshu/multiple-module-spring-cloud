package com.pension.permission.domain.authorization.enumeration;

public enum GrantType {
  BASE,               // 基础授权，如按客户/产品/计划维度配置的范围，或角色模板生成的默认授权
  DELEGATE_WHOLESALE, // 计划整体代办：源计划全体经办自动获得对目标计划的授权
  DELEGATE_SELECTIVE  // 计划指定人员代办：源计划指定的部分经办获得对目标计划的授权
}
