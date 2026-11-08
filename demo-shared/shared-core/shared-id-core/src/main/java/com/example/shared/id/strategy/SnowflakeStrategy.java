package com.example.shared.id.strategy;

import com.example.shared.id.algorithm.SnowflakeAlgorithm;
import com.example.shared.identifier.contract.IdType;

/**
 * 雪花算法策略（IdType.SNOWFLAKE 的正式实现，补齐"声明未实现"缺口）.
 *
 * <p>适用：Long 型强类型 ID（{@code @IdDefinition(type = SNOWFLAKE)}）、
 * 不依赖数据库号段、需要趋势递增的场景.
 *
 * <p>机器 ID：实例级配置（starter 装配时注入，{@code shared.id.snowflake.worker-id}，
 * 0-1023）——多实例部署必须保证各实例 worker-id 唯一，否则存在碰撞风险.
 *
 * @author shared-id-core
 */
public class SnowflakeStrategy implements IdGenerationStrategy {

  private final SnowflakeAlgorithm algorithm;

  public SnowflakeStrategy(long workerId) {
    this.algorithm = new SnowflakeAlgorithm(workerId);
  }

  @Override
  public IdType getSupportedType() {
    return IdType.SNOWFLAKE;
  }

  @Override
  public String nextId(IdContext context) {
    return String.valueOf(nextLongId(context));
  }

  @Override
  public Long nextLongId(IdContext context) {
    return algorithm.nextId();
  }

  @Override
  public boolean supportFormatting() {
    // 雪花 ID 自带时间序，无需上层格式化
    return false;
  }
}
