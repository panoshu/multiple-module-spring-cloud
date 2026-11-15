package com.example.core.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 步骤执行记录 ID。
 * <p>
 * 对应 §16.3：新增步骤执行表使用 BIGINT 主键，由 {@code IdService} 号段策略生成；
 * 与存量三个字符串强类型 ID 区分。
 * <p>
 * {@code format = "%s"} 必须显式声明：默认模板 {@code "%p%d%s"} 会把业务前缀与日期
 * 拼进最终值，{@code LongTypeHandler} 反序列化为 Long 时抛 ID格式配置错误。
 */
@IdDefinition(type = IdType.SEGMENT, format = "%s")
public record StepExecutionId(Long value) implements Identifier<Long> {

  public StepExecutionId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("StepExecutionId must be positive");
    }
  }

  public static StepExecutionId of(Long value) {
    return new StepExecutionId(value);
  }
}
