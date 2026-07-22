package com.example.core.infrastructure.engine.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * BusinessExtension 基础 Mix-in，提供多态序列化的通用配置。
 * 应用模块可以继承此接口并注册具体子类型。
 * 定义多态序列化的通用策略：
 * - 使用逻辑名称（@JsonTypeName 或类名）作为类型标识
 * - 类型标识字段名为 "businessType"
 * - 类型标识作为普通 JSON 属性
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 12:42
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.EXISTING_PROPERTY,  // 复用已有的 businessType 字段
  property = "businessType",
  visible = true  // 必须设为 true，否则 businessType 会被消费掉
)
@JsonSubTypes({})  // 空，由应用模块填充具体实现
public interface BusinessExtensionMixIn {

}
