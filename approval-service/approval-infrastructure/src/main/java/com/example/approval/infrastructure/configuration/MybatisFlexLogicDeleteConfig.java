package com.example.approval.infrastructure.configuration;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.logicdelete.impl.BooleanLogicDeleteProcessor;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 全局逻辑删除处理器修正（批次 73）。
 *
 * <p>approval 侧 {@code deleted} 列统一为 BOOLEAN（06-数据库规范）且 DO 为 Boolean，
 * 默认的 Integer 处理器会对无显式 deleted 条件的查询追加 {@code deleted = 0}，
 * 在真 PostgreSQL 上报 {@code operator does not exist: boolean = integer}。
 * 注册 Boolean 处理器后追加 {@code deleted = FALSE} 与列类型一致。
 *
 * <p>与本仓库 {@code business-core-infrastructure}、{@code auth-infrastructure}
 * 的同名配置类同源；file-service 为独立的 Integer 体系（DO Integer + INT 列），
 * 不受影响、不适用。
 */
@Configuration
public class MybatisFlexLogicDeleteConfig implements MyBatisFlexCustomizer {

  @Override
  public void customize(FlexGlobalConfig globalConfig) {
    LogicDeleteManager.setProcessor(new BooleanLogicDeleteProcessor());
  }
}
